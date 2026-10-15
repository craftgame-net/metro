package net.craftgame.metro.debug

import java.util.UUID

/**
 * Holds the viewers of the debug log. A viewer is the console (key null) or a player, with a level and a scope.
 * Player viewers get at most [CHAT_MAX_LEVEL] and are rate limited.
 */
class DebugHub(
    private val clock: () -> Long,
) : DebugLog {
    /** The level and scope of one viewer. */
    data class Watch(
        val level: Int,
        val scope: DebugScope,
    )

    private class Viewer(
        val key: UUID?,
        val watch: Watch,
        val sink: DebugSink,
        val limiter: RateLimiter?,
    ) {
        fun wants(
            level: Int,
            subject: UUID?,
        ): Boolean {
            if (level > watch.level) return false
            return when (val scope = watch.scope) {
                DebugScope.All -> true
                DebugScope.Own -> subject != null && subject == key
                is DebugScope.Player -> subject != null && subject == scope.id
            }
        }
    }

    private val viewers = LinkedHashMap<UUID?, Viewer>()

    /**
     * Starts or replaces the watch of [viewer], the player id or null for the console. Returns false and changes
     * nothing when [level] is not 1 to 3, when a player asks for more than [CHAT_MAX_LEVEL], or when the console
     * asks for [DebugScope.Own].
     */
    fun watch(
        viewer: UUID?,
        watch: Watch,
        sink: DebugSink,
    ): Boolean {
        val maxLevel = if (viewer == null) MAX_LEVEL else CHAT_MAX_LEVEL
        if (watch.level !in 1..maxLevel || (viewer == null && watch.scope == DebugScope.Own)) return false
        val limiter = if (viewer == null) null else RateLimiter(CHAT_LINES, CHAT_WINDOW_MILLIS, clock)
        viewers[viewer] = Viewer(viewer, watch, sink, limiter)
        return true
    }

    /** Removes the watch of [viewer]. */
    fun stop(viewer: UUID?) {
        viewers.remove(viewer)
    }

    /** Returns the watch of [viewer], or null when it is not watching. */
    fun watchOf(viewer: UUID?): Watch? = viewers[viewer]?.watch

    override fun enabled(
        level: Int,
        subject: UUID?,
    ): Boolean = viewers.values.any { it.wants(level, subject) }

    override fun log(
        level: Int,
        subject: UUID?,
        message: () -> String,
    ) {
        if (viewers.isEmpty()) return
        var text: String? = null
        for (viewer in viewers.values) {
            if (!viewer.wants(level, subject)) continue
            val line = text ?: message().also { text = it }
            deliver(viewer, level, subject, line)
        }
    }

    private fun deliver(
        viewer: Viewer,
        level: Int,
        subject: UUID?,
        text: String,
    ) {
        val limiter = viewer.limiter
        if (limiter != null) {
            if (!limiter.admit()) return
            val dropped = limiter.takeDropped()
            if (dropped > 0) viewer.sink.suppressed(dropped)
        }
        viewer.sink.accept(level, subject, text)
    }

    companion object {
        const val MAX_LEVEL = DebugLevel.DETAIL
        const val CHAT_MAX_LEVEL = DebugLevel.DECISION
        const val CHAT_LINES = 5
        const val CHAT_WINDOW_MILLIS = 1000L
    }
}
