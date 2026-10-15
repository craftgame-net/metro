package net.craftgame.metro.ride

import net.craftgame.metro.geo.Point
import net.craftgame.metro.geo.Vec3
import java.util.UUID

/**
 * An in-memory cart at [position] with [passenger] aboard. [move] applies [lastVelocity] the way vanilla moves a
 * cart with a passenger, so a ride follows its path tick by tick.
 */
class FakeCart(
    override var position: Point,
    var passenger: UUID?,
) : RideCart {
    override val id: UUID = UUID.randomUUID()

    override var isValid = true

    /** Last values the ride set, in blocks per tick and degrees. */
    var lastVelocity: Vec3 = Vec3.ZERO
    var lastMaxSpeed = 0.0
    var yaw = 0f

    /** True once the ride has removed the cart. */
    var removed = false

    /** While true, [move] leaves the cart where it is, as a blocked cart. */
    var blocked = false

    /** While true, [setVelocity] throws, as a broken entity would. */
    var broken = false

    override fun carries(rider: UUID): Boolean = passenger == rider

    override fun setVelocity(velocity: Vec3) {
        check(!broken) { "broken cart" }
        lastVelocity = velocity
    }

    override fun setMaxSpeed(speed: Double) {
        lastMaxSpeed = speed
    }

    override fun face(yaw: Float) {
        this.yaw = yaw
    }

    override fun remove() {
        passenger = null
        removed = true
        isValid = false
    }

    /** Moves the cart by one tick of its velocity, undoing [Steering.PASSENGER_SCALE] on the horizontal axes. */
    fun move() {
        if (blocked || removed) return
        val scale = Steering.PASSENGER_SCALE
        val step = Vec3(lastVelocity.x * scale, lastVelocity.y, lastVelocity.z * scale)
        position = Point.of(position.world, position.toVec3() + step)
    }
}

/** A [RideHost] that writes down every call as one line in [events] and keeps the chunk holds in [held]. */
class RecordingHost : RideHost {
    val online = HashSet<UUID>()
    val events = ArrayList<String>()

    /** Holds per (world, chunk x, chunk z). A finished ride leaves none. */
    val held = HashMap<Triple<String, Int, Int>, Int>()

    /** Every chunk that was held at some point, in order. */
    val everHeld = ArrayList<Triple<String, Int, Int>>()

    override fun isOnline(rider: UUID): Boolean = rider in online

    override fun name(rider: UUID): String? = if (rider in online) "rider" else null

    override fun setDown(
        rider: UUID,
        target: Point,
    ) {
        events += "setDown ${target.x} ${target.y} ${target.z}"
    }

    override fun rememberStation(
        rider: UUID,
        spot: StationSpot,
    ) {
        events += "remember ${spot.stationId}:${spot.platformId}"
    }

    override fun clearPendingRescue(rider: UUID) {
        events += "clearRescue"
    }

    override fun rescue(rider: UUID) {
        events += "rescue"
    }

    override fun message(
        rider: UUID,
        key: String,
        vararg placeholders: Pair<String, String>,
    ) {
        events += "message $key ${placeholders.joinToString { it.second }}".trim()
    }

    override fun holdChunk(
        world: String,
        chunkX: Int,
        chunkZ: Int,
    ) {
        val chunk = Triple(world, chunkX, chunkZ)
        held.merge(chunk, 1, Int::plus)
        everHeld += chunk
    }

    override fun releaseChunk(
        world: String,
        chunkX: Int,
        chunkZ: Int,
    ) {
        val chunk = Triple(world, chunkX, chunkZ)
        val left = (held[chunk] ?: error("released $chunk without a hold")) - 1
        if (left == 0) held.remove(chunk) else held[chunk] = left
    }
}
