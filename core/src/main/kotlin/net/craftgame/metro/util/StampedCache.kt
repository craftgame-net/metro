package net.craftgame.metro.util

/**
 * Values by key, all dropped at once when the stamp they were computed under changes.
 * Not thread-safe: use it from one thread.
 */
class StampedCache<K : Any, V : Any> {
    private val values = HashMap<K, V>()
    private var stamp: Any? = null

    /**
     * Returns the value for [key]. [current] is the stamp, compared with `equals`; a different one drops all values.
     * [compute] runs on the calling thread, only when [key] has no value yet.
     */
    fun get(
        key: K,
        current: Any,
        compute: () -> V,
    ): V {
        if (current != stamp) {
            values.clear()
            stamp = current
        }
        return values.getOrPut(key, compute)
    }
}
