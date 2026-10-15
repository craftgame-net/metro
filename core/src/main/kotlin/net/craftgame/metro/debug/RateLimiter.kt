package net.craftgame.metro.debug

/** Lets at most [limit] events through in each window of [windowMillis] and counts the others. */
class RateLimiter(
    private val limit: Int,
    private val windowMillis: Long,
    private val clock: () -> Long,
) {
    private var windowStart = Long.MIN_VALUE
    private var used = 0
    private var dropped = 0

    /** Returns true when one more event fits in the current window; otherwise counts it as dropped. */
    fun admit(): Boolean {
        val now = clock()
        if (windowStart == Long.MIN_VALUE || now - windowStart >= windowMillis) {
            windowStart = now
            used = 0
        }
        if (used >= limit) {
            dropped++
            return false
        }
        used++
        return true
    }

    /** Returns the events dropped since the last call and resets the count. */
    fun takeDropped(): Int {
        val count = dropped
        dropped = 0
        return count
    }
}
