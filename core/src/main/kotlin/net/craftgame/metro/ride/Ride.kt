package net.craftgame.metro.ride

import net.craftgame.metro.geo.Point
import net.craftgame.metro.geo.Vec3
import java.util.UUID

/**
 * [DWELL]: waits at `begin` before leaving. [MOVING]: follows the path. [ARRIVED]: stopped at `eject`, waiting to set
 * the rider down.
 */
enum class RideState { DWELL, MOVING, ARRIVED }

/** A platform of a station. */
data class StationSpot(
    val stationId: String,
    val platformId: String,
)

/**
 * One trip of a rider along a planned path. The constructor folds the deceleration into the limits,
 * so the cart slows ahead of a turn, a slope, a slower zone or the stop.
 */
class Ride(
    val playerId: UUID,
    val cart: RideCart,
    /** Id of the destination station. */
    val destId: String,
    /** Id of the platform where the ride ends. */
    val destPlatform: String,
    /** A rider who drops out mid-ride is rescued to this platform. */
    val origin: StationSpot,
    /** Points along the rails, one per block. */
    val path: List<Point>,
    /** Speed limit in blocks per tick of the segment starting at each path point. */
    segmentSpeeds: List<Double>,
    /** Kept even if `/metro reload` changes the config. */
    val physics: RideTime.Physics,
) {
    private val track = RouteTrack(path)
    private val limits = SpeedProfile.limits(track, segmentSpeeds, physics)
    val totalLength: Double get() = track.totalLength

    /** Highest segment speed, in blocks per tick. Start value for the cart's vanilla max speed and the steering cap. */
    val topSpeed: Double = segmentSpeeds.maxOrNull()?.takeIf { it > 0.0 } ?: MIN_TOP_SPEED

    /** Chunks this ride holds, as (world name, [net.craftgame.metro.route.ChunkSet.key]). */
    val heldChunks = HashSet<Pair<String, Long>>()

    val switches = RideSwitches()

    val stall = StallTracker()

    var state = RideState.DWELL

    /** Ticks left to wait in [RideState.DWELL]. */
    var dwellTicks = 0

    /** Blocks travelled along the path. */
    var distance = 0.0

    /** Current speed in blocks per tick. */
    var speed = 0.0

    /** Ticks left to wait in [RideState.ARRIVED]. */
    var ejectTicks = 0

    /** Value of [distance] at the last debug log line, in blocks. */
    var debugMark = 0.0

    fun speedLimitAt(distance: Double): Double {
        if (path.size < 2) return 0.0
        return limits[track.segmentAt(distance)]
    }

    /** Returns the point [distance] blocks along the path, clamped to its ends. */
    fun pointAt(distance: Double): Point = track.pointAt(distance)

    /** Returns the blocks from the start to path point [index]. */
    fun distanceAt(index: Int): Double = track.distanceAt(index)

    /** Returns the distance along the path of the cart at [location]; [hint] is the last known distance. */
    fun projectedDistance(
        location: Vec3,
        hint: Double,
    ): Double = track.projectedDistance(location, hint, speed)

    private companion object {
        /** Blocks per tick. Used when the path has no positive speed. */
        const val MIN_TOP_SPEED = 0.1
    }
}
