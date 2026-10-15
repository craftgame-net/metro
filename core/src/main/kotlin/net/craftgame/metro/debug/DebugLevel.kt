package net.craftgame.metro.debug

/** The levels of [DebugLog] lines. */
object DebugLevel {
    /** A few events that are safe in production. */
    const val EVENT = 1

    /** Why something happened. */
    const val DECISION = 2

    /** Per-tick or per-block detail. */
    const val DETAIL = 3
}
