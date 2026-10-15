package net.craftgame.metro.ride

/**
 * Detects a moving ride that makes no progress (blocked, derailed). It uses no server state, so tests run without a
 * server.
 */
class StallTracker(
    /** Ticks without enough progress after which the ride counts as stalled. */
    private val limitTicks: Int = STALL_TICKS,
    /** Blocks the distance must grow by to count as progress. */
    private val minProgress: Double = MIN_PROGRESS,
) {
    /** Distance at the last progress; NaN before the first update. */
    private var mark = Double.NaN

    /** Ticks since the last progress. */
    private var ticks = 0

    /** True once the distance has not grown by [minProgress] blocks for [limitTicks] ticks in a row. */
    fun update(distance: Double): Boolean {
        if (mark.isNaN() || distance - mark >= minProgress) {
            mark = distance
            ticks = 0
            return false
        }
        ticks++
        return ticks >= limitTicks
    }

    /** Forgets all progress, so the next update starts a new count. */
    fun reset() {
        mark = Double.NaN
        ticks = 0
    }

    companion object {
        /** Ticks (5 seconds). */
        const val STALL_TICKS = 100

        /** Blocks. */
        const val MIN_PROGRESS = 0.5
    }
}
