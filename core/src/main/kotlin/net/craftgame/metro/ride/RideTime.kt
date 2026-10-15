package net.craftgame.metro.ride

import net.craftgame.metro.geo.Point

/**
 * Estimated ride time, simulated the way the controller drives a cart. It uses no server state, so tests run
 * without a server.
 */
object RideTime {
    /** How a cart moves. */
    data class Physics(
        /** Speed gained each tick, in blocks per tick. */
        val accel: Double,
        /** Speed lost each tick when braking, in blocks per tick. */
        val decel: Double,
        /** Cap for sharp turns, in blocks per tick. */
        val turnSpeed: Double,
        /** Blocks before a slow stretch where braking starts. */
        val slowLead: Double = 0.0,
        /** Blocks after a slow stretch that stay slow. */
        val slowTrail: Double = 0.0,
        /** Speed cap on slopes and its window. */
        val slope: SpeedProfile.Slope = SpeedProfile.Slope(),
    )

    /**
     * Returns the ticks a cart needs from a standing start at the first point of [path] until it stops at the last.
     * [segmentSpeeds] are in blocks per tick. Returns 0 for fewer than two points.
     */
    fun ticks(
        path: List<Point>,
        segmentSpeeds: List<Double>,
        physics: Physics,
    ): Int {
        if (path.size < 2) return 0
        val track = RouteTrack(path)
        val limits = SpeedProfile.limits(track, segmentSpeeds, physics)
        var distance = 0.0
        var speed = 0.0
        var ticks = 0
        while (track.totalLength - distance > ARRIVE_EPSILON && ticks < MAX_TICKS) {
            val target = limits[track.segmentAt(distance)].coerceAtLeast(MIN_SPEED)
            speed = SpeedProfile.step(speed, target, physics.accel, physics.decel)
            distance += speed
            ticks++
        }
        return ticks
    }

    /** Returns the ticks a player needs to walk [blocks]. */
    fun walkTicks(blocks: Double): Int = (blocks / WALK_BLOCKS_PER_TICK).toInt()

    /** Blocks from the end at which a ride counts as arrived. The controller uses it too. */
    const val ARRIVE_EPSILON = 0.3

    /** Blocks per tick. Keeps a zero limit short of the end from stalling the estimate. */
    private const val MIN_SPEED = 0.02

    /** Ticks (30 minutes), in case a route never gets there. */
    private const val MAX_TICKS = 20 * 60 * 30

    /** Blocks per tick, about 4.3 blocks per second. */
    private const val WALK_BLOCKS_PER_TICK = 0.216
}
