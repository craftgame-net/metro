package net.craftgame.metro.debug

import java.util.UUID

/**
 * Leveled debug log: 1 is a few events safe in production, 2 explains decisions, 3 is per-tick or per-block detail.
 * A line's subject is the player it is about; null is a global line. Server thread only.
 */
interface DebugLog {
    /** Returns true when some viewer wants a line of [level] about [subject]. */
    fun enabled(
        level: Int,
        subject: UUID?,
    ): Boolean

    /** Sends the text of [message] to every viewer that wants it. [message] does not run when no viewer does. */
    fun log(
        level: Int,
        subject: UUID?,
        message: () -> String,
    )

    companion object {
        val OFF: DebugLog =
            object : DebugLog {
                override fun enabled(
                    level: Int,
                    subject: UUID?,
                ) = false

                override fun log(
                    level: Int,
                    subject: UUID?,
                    message: () -> String,
                ) = Unit
            }
    }
}

/** Which lines a viewer receives. */
sealed interface DebugScope {
    /** Every line. */
    data object All : DebugScope

    /** Lines about the viewer's own player. */
    data object Own : DebugScope

    /** Lines about [id]. */
    data class Player(
        val id: UUID,
    ) : DebugScope
}

/** Receives debug lines on the server thread. */
interface DebugSink {
    /** Shows one line of [level] about [subject]. */
    fun accept(
        level: Int,
        subject: UUID?,
        text: String,
    )

    /** Tells that [count] lines were dropped by the rate limit. */
    fun suppressed(count: Int)
}
