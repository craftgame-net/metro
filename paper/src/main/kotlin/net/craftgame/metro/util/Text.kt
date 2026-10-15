package net.craftgame.metro.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer

object Text {
    private val legacy =
        LegacyComponentSerializer
            .builder()
            .character('&')
            .hexColors()
            .build()

    private val placeholder = Regex("""\{([A-Za-z0-9_-]+)\}""")

    /** One pass: a value that contains `{otherKey}` is kept as it is. Unknown placeholders stay in place. */
    fun fill(
        raw: String,
        vararg placeholders: Pair<String, String>,
    ): String = if (placeholders.isEmpty()) raw else fill(raw, placeholders.toMap())

    /** Like the vararg form, with the values in a map. */
    fun fill(
        raw: String,
        values: Map<String, String>,
    ): String {
        if (values.isEmpty()) return raw
        return placeholder.replace(raw) { match -> values[match.groupValues[1]] ?: match.value }
    }

    /** Fills [placeholders], then turns `&` colour codes and `&#RRGGBB` colours into a [Component]. */
    fun of(
        raw: String,
        vararg placeholders: Pair<String, String>,
    ): Component = legacy.deserialize(fill(raw, *placeholders))

    /** Like [of], and a click puts [command] into the player's chat box without sending it. */
    fun command(
        raw: String,
        command: String,
        vararg placeholders: Pair<String, String>,
    ): Component = of(raw, *placeholders).clickEvent(ClickEvent.suggestCommand(command))

    /** Like [of], and a click runs [command]. [hover] is the tooltip text. */
    fun run(
        raw: String,
        command: String,
        hover: String,
    ): Component = of(raw).clickEvent(ClickEvent.runCommand(command)).hoverEvent(HoverEvent.showText(of(hover)))

    /** Returns [raw], a space, then [label] as a link that opens [url]. [hover] is the tooltip text of the link. */
    fun withLink(
        raw: String,
        label: String,
        url: String,
        hover: String,
    ): Component {
        val link = of(label).clickEvent(ClickEvent.openUrl(url)).hoverEvent(HoverEvent.showText(of(hover)))
        return Component.textOfChildren(of(raw), Component.space(), link)
    }
}
