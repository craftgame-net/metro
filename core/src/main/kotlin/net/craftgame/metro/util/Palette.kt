package net.craftgame.metro.util

import net.craftgame.metro.data.Junction
import java.util.Locale

/**
 * One colour per marked role, shared by the world preview and the editor menus so a marker and its menu entry
 * match.
 */
object Palette {
    const val ROUTE = 0xFFFFFF
    const val BOARD = 0x9CFF2E
    const val BEGIN = 0x2ED573
    const val OUT = 0x00FF40
    const val IN = 0xFFD500
    const val EJECT = 0xFF8A00
    const val LAND = 0xFF3B30
    const val SPLIT = 0x35C6FF
    const val JOIN = 0x2E6BFF
    const val CROSS = 0x7A5CFF

    const val CROSS_OFF = 0x8A8F98

    /** Returns [rgb] as an ampersand hex colour that [Text] understands, e.g. `&#9CFF2E`. */
    fun code(rgb: Int): String = String.format(Locale.ROOT, "&#%06X", rgb)

    /** Returns the colour of a junction. [turns] only matters for a crossing: turning off greys it. */
    fun junction(
        kind: Junction.Kind,
        turns: Boolean,
    ): Int =
        when (kind) {
            Junction.Kind.SPLIT -> SPLIT
            Junction.Kind.JOIN -> JOIN
            Junction.Kind.CROSS -> if (turns) CROSS else CROSS_OFF
        }

    /** Returns the colour of the anchor [role], or null for an unknown role. */
    fun anchor(role: String): Int? =
        when (role) {
            "board" -> BOARD
            "begin" -> BEGIN
            "out" -> OUT
            "in" -> IN
            "eject" -> EJECT
            "land" -> LAND
            else -> null
        }

    /** Like [anchor] as a [code], or an empty string for an unknown role. */
    fun anchorCode(role: String): String = anchor(role)?.let { code(it) }.orEmpty()
}
