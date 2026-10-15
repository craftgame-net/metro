package net.craftgame.metro.data

/**
 * An ordered group of stations, kept as ids. [icon] is an item name such as `COMPASS`. [speed] is blocks per tick;
 * null is unset, so `speed.cruise` applies.
 */
data class Line(
    val id: String,
    var display: String,
    var icon: String = "COMPASS",
    val stations: MutableList<String> = ArrayList(),
    var speed: Double? = null,
)
