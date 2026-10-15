package net.craftgame.metro.data

/** The one rule for line, station and junction ids typed by an editor. */
object Ids {
    private val valid = Regex("[a-z0-9_-]+")

    /**
     * Returns [raw] without surrounding whitespace if the rest is a valid id: not empty and only lowercase a-z, 0-9,
     * `_` and `-`. Returns null otherwise.
     */
    fun validated(raw: String): String? = raw.trim().takeIf { valid.matches(it) }
}
