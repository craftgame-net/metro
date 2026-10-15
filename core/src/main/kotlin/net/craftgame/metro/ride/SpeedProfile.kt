package net.craftgame.metro.ride

import net.craftgame.metro.geo.Point
import net.craftgame.metro.route.Slopes
import net.craftgame.metro.route.Turns

/**
 * Builds the speed limit per point: segment speeds, sharp-turn caps, slope caps and a braking envelope so the cart
 * slows before a turn, a slope or the stop. The envelope at a point is the highest speed from which the cart can
 * still brake to every later limit. All speeds are in blocks per tick.
 */
object SpeedProfile {
    /** Returns the speed one tick later: [speed] moves towards [target] by at most [accel] up or [decel] down. */
    fun step(
        speed: Double,
        target: Double,
        accel: Double,
        decel: Double,
    ): Double =
        when {
            speed < target -> (speed + accel).coerceAtMost(target)
            speed > target -> (speed - decel).coerceAtLeast(target)
            else -> speed
        }

    /** Returns the speed limit of each point of [track]'s path for a cart that moves with [physics]. */
    fun limits(
        track: RouteTrack,
        segmentSpeeds: List<Double>,
        physics: RideTime.Physics,
    ): DoubleArray = limits(track.path, lazyOf(track), segmentSpeeds, physics)

    /** The slope cap: [speed] at most, widened by [lead] blocks before a slope and [trail] after. */
    data class Slope(
        /** Speed limit on slopes in blocks per tick. [Slopes.OFF] leaves slopes alone. */
        val speed: Double = Slopes.OFF,
        val lead: Double = 0.0,
        val trail: Double = 0.0,
    )

    /**
     * Returns the speed limit of each point of [path]. A missing segment speed counts as 0. The last point (the stop)
     * is always 0. A [turnSpeed] or [decel] of 0 or less turns that part off. [lead] and [trail] are blocks: each slow
     * stretch is widened by [lead] before it and [trail] after it. Slopes have their own window in [slope].
     */
    fun limits(
        path: List<Point>,
        segmentSpeeds: List<Double>,
        decel: Double,
        turnSpeed: Double,
        lead: Double = 0.0,
        trail: Double = 0.0,
        slope: Slope = Slope(),
    ): DoubleArray {
        // Acceleration plays no part in the limits.
        val physics = RideTime.Physics(0.0, decel, turnSpeed, lead, trail, slope)
        return limits(path, lazy { RouteTrack(path) }, segmentSpeeds, physics)
    }

    /** [track] is the track of [path]; it is only built when a window needs distances. */
    private fun limits(
        path: List<Point>,
        track: Lazy<RouteTrack>,
        segmentSpeeds: List<Double>,
        physics: RideTime.Physics,
    ): DoubleArray {
        val limits = DoubleArray(path.size)
        if (path.isEmpty()) return limits
        val last = path.size - 1
        // The last point is the stop; the envelope alone reaches it.
        val raw = DoubleArray(last)
        for (i in 0 until last) {
            var value = segmentSpeeds.getOrElse(i) { 0.0 }
            if (physics.turnSpeed > 0.0 && Turns.isSharp(path, i)) {
                value = minOf(value, physics.turnSpeed)
            }
            raw[i] = value
        }
        val widened = widen(track, raw, physics.slowLead, physics.slowTrail)
        val slope = physics.slope
        slopeCaps(path, segmentSpeeds, slope.speed)?.let { caps ->
            val slow = widen(track, caps, slope.lead, slope.trail)
            for (i in 0 until last) widened[i] = minOf(widened[i], slow[i])
        }
        val decel = physics.decel
        limits[last] = 0.0
        for (i in last - 1 downTo 0) {
            val segment = path[i].toVec3().distance(path[i + 1].toVec3())
            val envelope =
                if (decel > EPSILON) {
                    Math.sqrt(limits[i + 1] * limits[i + 1] + 2 * decel * segment)
                } else {
                    Double.MAX_VALUE
                }
            limits[i] = minOf(widened[i], envelope)
        }
        return limits
    }

    /**
     * Returns the slope cap of each segment of [path], or infinity where the segment is not a slope. Returns null
     * when [slopeSpeed] is [Slopes.OFF], or when the path has no slope.
     */
    private fun slopeCaps(
        path: List<Point>,
        segmentSpeeds: List<Double>,
        slopeSpeed: Double,
    ): DoubleArray? {
        val first = if (slopeSpeed < Slopes.OFF) firstSlope(path) else -1
        if (first < 0) return null
        val caps = DoubleArray(path.size - 1) { Double.POSITIVE_INFINITY }
        for (i in first until caps.size) {
            if (Slopes.isSlope(path[i], path[i + 1])) {
                caps[i] = Slopes.cap(segmentSpeeds.getOrElse(i) { 0.0 }, slopeSpeed)
            }
        }
        return caps
    }

    /** Returns the index of the first slope segment of [path], or -1 when it has none. */
    private fun firstSlope(path: List<Point>): Int {
        for (i in 0 until path.size - 1) {
            if (Slopes.isSlope(path[i], path[i + 1])) return i
        }
        return -1
    }

    /**
     * Returns for each point the lowest value of [raw] within [lead] blocks ahead and [trail] blocks behind. Returns
     * [raw] itself when both are 0, so [track] is only built when needed. The lead counts from the start of a point's
     * segment to the start of the slow segment, the trail from the end of the slow segment to the end of the point's
     * segment, so a long slow segment (such as a slope) is covered as a whole.
     */
    private fun widen(
        track: Lazy<RouteTrack>,
        raw: DoubleArray,
        lead: Double,
        trail: Double,
    ): DoubleArray {
        if (lead <= 0.0 && trail <= 0.0) return raw
        val distances = track.value
        val out = DoubleArray(raw.size)
        val deque = IntArray(raw.size)
        var head = 0
        var tail = 0
        var next = 0
        for (i in raw.indices) {
            val here = distances.distanceAt(i)
            while (next < raw.size && distances.distanceAt(next) <= here + lead + EPSILON) {
                while (tail > head && raw[deque[tail - 1]] >= raw[next]) tail--
                deque[tail++] = next
                next++
            }
            val end = distances.distanceAt(i + 1)
            while (distances.distanceAt(deque[head] + 1) + trail < end - EPSILON) head++
            out[i] = raw[deque[head]]
        }
        return out
    }

    private const val EPSILON = 1e-9
}
