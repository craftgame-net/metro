package net.craftgame.metro.util

/**
 * Counts holders per key, for resources that are only on or off (plugin chunk tickets), so independent users do
 * not undo each other.
 *
 * [onFirst] runs when a key goes from 0 to 1 holder and [onLast] when it goes back to 0. Both run synchronously on
 * the thread that calls [acquire], [release] or [releaseAll]. The class is not thread-safe: use it from one thread.
 */
class RefCounter<K : Any>(
    private val onFirst: (K) -> Unit,
    private val onLast: (K) -> Unit,
) {
    private val counts = HashMap<K, Int>()

    /** Returns the number of current holders of [key]. */
    fun count(key: K): Int = counts[key] ?: 0

    /** Returns the number of keys that have a holder. */
    fun size(): Int = counts.size

    /** Adds one holder. Calls [onFirst] if there was none. */
    fun acquire(key: K) {
        val count = counts.merge(key, 1, Int::plus)
        if (count == 1) onFirst(key)
    }

    /** Removes one holder and calls [onLast] if it was the last. A key nobody holds is ignored. */
    fun release(key: K) {
        val count = counts[key] ?: return
        if (count > 1) {
            counts[key] = count - 1
        } else {
            counts.remove(key)
            onLast(key)
        }
    }

    /** Drops every holder of every key and calls [onLast] once per key. */
    fun releaseAll() {
        val keys = counts.keys.toList()
        counts.clear()
        keys.forEach(onLast)
    }
}
