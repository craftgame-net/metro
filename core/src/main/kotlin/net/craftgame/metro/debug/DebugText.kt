package net.craftgame.metro.debug

import java.util.Locale

/** Formats [distance] in blocks with one decimal for debug lines. */
internal fun blocks(distance: Double): String = String.format(Locale.ROOT, "%.1f", distance)
