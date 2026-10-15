package net.craftgame.metro.route

/**
 * One finding of a rebuild. [key] names the `command.<key>` text and the wiki section, [text] is the message with
 * its placeholders filled. [target] is where a teleport link goes; null when there is no place to go to.
 */
data class RebuildWarning(
    val key: String,
    val text: String,
    val target: IssueTarget? = null,
) {
    /** Errors keep something out of the graph; everything else is a warning. */
    val error: Boolean get() = key in ERROR_KEYS

    companion object {
        val ERROR_KEYS = setOf("junction-no-rail", "junction-duplicate", "junction-bad-block", "heading-conflict")
    }
}

/** Source of warning texts. */
fun interface WarningText {
    /** Returns the text of the finding [key] with [placeholders] filled. */
    fun of(
        key: String,
        vararg placeholders: Pair<String, String>,
    ): String

    /** Returns the finding [key] at [target], with its text from [of]. */
    fun warning(
        key: String,
        target: IssueTarget?,
        vararg placeholders: Pair<String, String>,
    ): RebuildWarning = RebuildWarning(key, of(key, *placeholders), target)
}
