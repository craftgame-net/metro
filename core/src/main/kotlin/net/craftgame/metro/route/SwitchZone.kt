package net.craftgame.metro.route

/**
 * The slow stretch at the switch end of a branch or ramp edge: the first or last [SEGMENTS] dense segments.
 * It only slows an edge down. Shared by [RoutePlanner] and [EdgeCost] so the ride and the estimate agree.
 */
object SwitchZone {
    /** Number of dense segments (one per block) at an end of an edge that the zone covers. */
    const val SEGMENTS = 2

    /**
     * Returns the speed limit of the dense segment [index] of an edge with [count] dense segments. [base] is the
     * edge speed. [start] applies within the first [SEGMENTS] segments, [end] within the last; the slowest wins where
     * they overlap. All speeds are in blocks per tick; a null zone speed means no zone at that end.
     */
    fun speed(
        base: Double,
        start: Double?,
        end: Double?,
        index: Int,
        count: Int,
    ): Double {
        var speed = base
        if (start != null && index < SEGMENTS) speed = minOf(speed, start)
        if (end != null && index >= count - SEGMENTS) speed = minOf(speed, end)
        return speed
    }
}
