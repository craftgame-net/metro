package net.craftgame.metro.util

object Paging {
    /** Lines per page of a chat list. */
    const val CHAT_LINES = 10

    /** Returns the number of pages for [total] items with [size] items per page. An empty list still has one page. */
    fun pages(
        total: Int,
        size: Int,
    ): Int = maxOf(1, (total + size - 1) / size)

    /** So a stale link or a typo lands on the nearest page. */
    fun clamp(
        page: Int,
        total: Int,
        size: Int,
    ): Int = page.coerceIn(1, pages(total, size))

    /** Returns the items of [page] (counted from 1), which is first clamped into range. */
    fun <T> slice(
        items: List<T>,
        page: Int,
        size: Int,
    ): List<T> {
        val from = (clamp(page, items.size, size) - 1) * size
        return items.subList(from, minOf(items.size, from + size))
    }
}
