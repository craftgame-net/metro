package net.craftgame.metro.ride

import net.craftgame.metro.data.Network
import net.craftgame.metro.data.Nodes
import net.craftgame.metro.debug.DebugLevel
import net.craftgame.metro.debug.DebugLog
import net.craftgame.metro.debug.blocks
import net.craftgame.metro.geo.Vec3
import net.craftgame.metro.rail.RailWorlds
import net.craftgame.metro.route.ChunkSet
import java.util.Locale
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Owns every active [Ride] and drives its cart each [tick]. It does not plan routes or spawn carts. Server thread
 * only.
 */
class Rides(
    private val network: Network,
    private val rails: RailWorlds,
    private val host: RideHost,
    private val logger: Logger,
    /** Ticks a ride waits at `eject` before the rider is set down. Read at each arrival. */
    private val ejectDelay: () -> Int,
    private val debugLog: DebugLog,
) {
    private val running = ArrayList<Ride>()

    /** Returns the active rides. This is the live list: do not change it. */
    fun active(): List<Ride> = running

    /** Returns the ride of [rider], or null when they are not riding. */
    fun rideOf(rider: UUID): Ride? = running.firstOrNull { it.playerId == rider }

    /** Returns the ride of the cart with entity id [cartId], or null when the cart belongs to no active ride. */
    fun rideOfCart(cartId: UUID): Ride? = running.firstOrNull { it.cart.id == cartId }

    /**
     * Registers [ride], whose cart already holds the rider, and saves its origin as the rider's last station.
     * [switchCandidates] are the path indices whose rails the ride may reshape.
     */
    fun start(
        ride: Ride,
        switchCandidates: List<Int>,
    ) {
        // Only a starting value: tickMove sets the cap every tick to at least the ride speed.
        ride.cart.setMaxSpeed(ride.topSpeed)
        ride.switches.plan(ride.path, switchCandidates, debugLog, ride.playerId, ride::distanceAt)
        running.add(ride)
        host.rememberStation(ride.playerId, ride.origin)
        debugLog.log(DebugLevel.EVENT, ride.playerId) {
            "ride start ${Nodes.stop(ride.origin.stationId, ride.origin.platformId)} -> " +
                "${Nodes.stop(ride.destId, ride.destPlatform)}, ${blocks(ride.totalLength)} blocks"
        }
    }

    /**
     * Ends [ride] without arrival: restores its rails, removes the cart and releases its chunks. With [rollback],
     * an online rider goes back to where they boarded and is told [reason].
     */
    fun abort(
        ride: Ride,
        reason: RideAbort,
        rollback: Boolean,
    ) {
        val online = host.isOnline(ride.playerId)
        debugLog.log(DebugLevel.EVENT, ride.playerId) {
            "ride abort reason=$reason state=${ride.state} d=${blocks(ride.distance)}/${blocks(ride.totalLength)} " +
                "rollback=$rollback"
        }
        teardown(ride)
        if (rollback && online) {
            host.rescue(ride.playerId)
            val distance = String.format(Locale.ROOT, "%.0f", ride.distance)
            val total = String.format(Locale.ROOT, "%.0f", ride.totalLength)
            val stop = Nodes.stop(ride.destId, ride.destPlatform)
            host.message(ride.playerId, reason.messageKey, "distance" to distance, "total" to total, "stop" to stop)
        }
    }

    /**
     * Ends every ride. A ride waiting at `eject` counts as arrived; the riders of the others go back to where they
     * boarded. Called on shutdown.
     */
    fun stopAll() {
        debugLog.log(DebugLevel.EVENT, null) { "stop all: ${running.size} ride(s)" }
        for (ride in ArrayList(running)) {
            if (ride.state == RideState.ARRIVED) finish(ride) else abort(ride, RideAbort.SHUTDOWN, rollback = true)
        }
    }

    /**
     * Ends [ride] as an arrival: sets the rider down at `land` (or `eject`), saves their last station and sends
     * the arrival message. A rider who quits or dies while waiting at `eject` also arrives this way. Does nothing
     * for a ride that already ended.
     */
    fun finish(ride: Ride) {
        if (ride !in running) return
        val rider = ride.playerId
        val online = host.isOnline(rider)
        val station = network.stations[ride.destId]
        val platform = station?.platform(ride.destPlatform)
        debugLog.log(DebugLevel.EVENT, rider) {
            "ride finish ${Nodes.stop(ride.destId, ride.destPlatform)}, ${blocks(ride.distance)} blocks"
        }
        teardown(ride)

        if (online) {
            host.setDown(rider, platform?.teleportTarget() ?: ride.path.last())
            host.rememberStation(rider, StationSpot(ride.destId, ride.destPlatform))
            host.clearPendingRescue(rider)
            if (station != null) host.message(rider, "boarding.arrival", "station" to station.display)
        }
    }

    /** Moves every ride on by one tick. A ride that throws is aborted with rollback; the others go on. */
    fun tick() {
        for (ride in ArrayList(running)) {
            // An earlier ride of this tick may have ended this one.
            if (ride in running) tickOrAbort(ride)
        }
    }

    private fun tickOrAbort(ride: Ride) {
        val error = runCatching { tickRide(ride) }.exceptionOrNull() ?: return
        // Only an exception is the ride's own failure. An Error goes on up to the scheduler.
        if (error !is Exception) throw error
        failed(ride, error)
    }

    private fun tickRide(ride: Ride) {
        val cart = ride.cart
        val online = host.isOnline(ride.playerId)
        val aboard = cart.carries(ride.playerId)
        if (online && ride.state == RideState.ARRIVED && !aboard) {
            finish(ride)
            return
        }
        // A ride without its rider aboard must never roll on by itself.
        if (!cart.isValid || !online || !aboard) {
            debugLog.log(DebugLevel.DECISION, ride.playerId) {
                "ride cannot go on: cart valid=${cart.isValid} rider online=$online aboard=$aboard"
            }
            abort(ride, abortReason(cart.isValid), rollback = online)
            return
        }
        when (ride.state) {
            RideState.DWELL -> tickDwell(ride)
            RideState.MOVING -> tickMove(ride)
            RideState.ARRIVED -> tickArrived(ride)
        }
    }

    private fun failed(
        ride: Ride,
        error: Exception,
    ) {
        val name = host.name(ride.playerId) ?: ride.playerId.toString()
        logger.log(Level.SEVERE, "Metro: ride of $name to ${ride.destId} failed, aborting it", error)
        val second = runCatching { abort(ride, RideAbort.FAILED, rollback = true) }.exceptionOrNull() ?: return
        if (second !is Exception) throw second
        running.remove(ride)
        logger.log(Level.SEVERE, "Metro: could not abort the ride of $name cleanly", second)
    }

    private fun stalled(ride: Ride) {
        val name = host.name(ride.playerId) ?: ride.playerId.toString()
        val at = "${blocks(ride.distance)}/${blocks(ride.totalLength)}"
        val limit = String.format(Locale.ROOT, "%.3f", ride.speedLimitAt(ride.distance))
        logger.warning(
            "Metro: ride of $name to ${ride.destId} stalled at d=$at (no progress for ${StallTracker.STALL_TICKS} " +
                "ticks, speed limit $limit), aborting it",
        )
        abort(ride, RideAbort.STALLED, rollback = true)
    }

    private fun abortReason(cartValid: Boolean) = if (cartValid) RideAbort.RIDER_LEFT else RideAbort.CART_LOST

    private fun tickDwell(ride: Ride) {
        ride.cart.setVelocity(Vec3.ZERO)
        if (ride.dwellTicks > 0) {
            ride.dwellTicks--
            return
        }
        ride.state = RideState.MOVING
        debugLog.log(DebugLevel.DETAIL, ride.playerId) { "ride leaves the platform" }
        ride.speed = 0.0
        ride.stall.reset()
    }

    private fun tickMove(ride: Ride) {
        // The ride's own top speed, not speed.cruise: a line faster than speed.cruise would be held back by the
        // cart's speed cap.
        val cruise = ride.topSpeed

        val from = ride.cart.position.toVec3()
        val previousDistance = ride.distance
        // Distance follows the cart's real position, so a blocked cart does not run ahead on paper.
        ride.distance = ride.projectedDistance(from, ride.distance)
        val travelled = ride.distance - previousDistance

        val remaining = ride.totalLength - ride.distance
        if (remaining <= RideTime.ARRIVE_EPSILON) {
            arrive(ride)
            return
        }
        if (ride.stall.update(ride.distance)) {
            stalled(ride)
            return
        }

        val speedTarget = ride.speedLimitAt(ride.distance)
        // The ride's own physics, as its limits were built with: a reload mid-ride must not mix the two.
        ride.speed = SpeedProfile.step(ride.speed, speedTarget, ride.physics.accel, ride.physics.decel)

        val lookahead = Steering.lookahead(ride.speed)
        val steering =
            Steering.steer(from, ride.pointAt(ride.distance + lookahead).toVec3(), ride.speed, cruise)
                // The first target can sit on the cart itself; aim one block further then.
                ?: Steering.steer(from, ride.pointAt(ride.distance + lookahead + 1.0).toVec3(), ride.speed, cruise)
        var maxSpeed = cruise
        if (steering != null) {
            ride.cart.setVelocity(steering.velocity)
            maxSpeed = steering.maxSpeed
            faceAlong(ride.cart, steering.velocity)
        }
        ride.cart.setMaxSpeed(maxSpeed.coerceAtLeast(ride.speed))
        if (ride.distance - ride.debugMark >= DEBUG_INTERVAL && debugLog.enabled(DebugLevel.DETAIL, ride.playerId)) {
            logProgress(ride, travelled)
        }
        ride.switches.tend(rails, ride.distance, debugLog, ride.playerId)
        keepChunks(ride)
    }

    /** Turns the cart to look along its horizontal travel. A cart going straight up or down keeps its heading. */
    private fun faceAlong(
        cart: RideCart,
        velocity: Vec3,
    ) {
        val flat = Vec3(velocity.x, 0.0, velocity.z)
        if (flat.lengthSquared() > MIN_FACING_SQ) {
            val horizontal = flat.normalized()
            cart.face(Math.toDegrees(Math.atan2(-horizontal.x, horizontal.z)).toFloat())
        }
    }

    private fun logProgress(
        ride: Ride,
        travelled: Double,
    ) {
        ride.debugMark = ride.distance
        debugLog.log(DebugLevel.DETAIL, ride.playerId) {
            String.format(
                Locale.ROOT,
                "ride d=%.1f/%.1f limit=%.3f speed=%.3f real=%.3f",
                ride.distance,
                ride.totalLength,
                ride.speedLimitAt(ride.distance),
                ride.speed,
                travelled,
            )
        }
    }

    private fun arrive(ride: Ride) {
        ride.cart.setVelocity(Vec3.ZERO)
        val platform = network.stations[ride.destId]?.platform(ride.destPlatform)
        if (platform?.eject != null) {
            ride.state = RideState.ARRIVED
            ride.ejectTicks = ejectDelay()
            debugLog.log(DebugLevel.DECISION, ride.playerId) {
                "ride at eject, rider set down in ${ride.ejectTicks} ticks"
            }
        } else {
            debugLog.log(DebugLevel.DECISION, ride.playerId) { "ride at the end, the platform has no eject" }
            finish(ride)
        }
    }

    private fun tickArrived(ride: Ride) {
        ride.cart.setVelocity(Vec3.ZERO)
        if (ride.ejectTicks > 0) {
            ride.ejectTicks--
            return
        }
        finish(ride)
    }

    private fun teardown(ride: Ride) {
        running.remove(ride)
        ride.switches.restoreAll(rails, debugLog, ride.playerId)
        ride.cart.remove()
        releaseChunks(ride)
    }

    /**
     * Holds the chunk of the cart and the chunk 16 blocks ahead, once per chunk. The holds stay until the ride ends
     * ([releaseChunks]), so the cart never runs into an unloaded chunk.
     */
    private fun keepChunks(ride: Ride) {
        val position = ride.cart.position
        val world = position.world
        val samples = listOf(position.toVec3(), ride.pointAt(ride.distance + CHUNK_LOOKAHEAD).toVec3())
        for (sample in samples) {
            val block = sample.block()
            val chunkX = block.x shr CHUNK_SHIFT
            val chunkZ = block.z shr CHUNK_SHIFT
            if (ride.heldChunks.add(world to ChunkSet.key(chunkX, chunkZ))) {
                host.holdChunk(world, chunkX, chunkZ)
                debugLog.log(DebugLevel.DETAIL, ride.playerId) {
                    "ride holds chunk $chunkX,$chunkZ (${ride.heldChunks.size} held)"
                }
            }
        }
    }

    private fun releaseChunks(ride: Ride) {
        for ((world, key) in ride.heldChunks) {
            host.releaseChunk(world, ChunkSet.chunkX(key), ChunkSet.chunkZ(key))
        }
        debugLog.log(DebugLevel.DETAIL, ride.playerId) { "ride releases ${ride.heldChunks.size} chunk(s)" }
        ride.heldChunks.clear()
    }

    private companion object {
        /** Blocks the ride has to travel between two debug log lines. */
        const val DEBUG_INTERVAL = 10.0

        /** Squared horizontal speed, in blocks per tick, below which the cart keeps its heading. */
        const val MIN_FACING_SQ = 1e-8

        /** Blocks ahead of the cart whose chunk is held too. */
        const val CHUNK_LOOKAHEAD = 16.0

        /** Block coordinate to chunk coordinate: 16 blocks per chunk. */
        const val CHUNK_SHIFT = 4
    }
}
