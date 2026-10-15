package net.craftgame.metro.data

import net.craftgame.metro.geo.Point

/**
 * A switch that belongs to no station. Rails cannot tell a merge from a split, so [kind] is part of the marking.
 */
data class Junction(
    val id: String,
    var world: String,
    var location: Point,
    var kind: Kind,
    /** For a crossing: whether a cart may turn here. Only crossings use it. */
    var turns: Boolean = true,
    /** Blocks per tick in the switch zone of a branch or ramp. Null: unset, so `speed.branch` applies. */
    var speed: Double? = null,
) {
    enum class Kind { SPLIT, JOIN, CROSS }

    companion object {
        fun kindOf(raw: String): Kind? = Kind.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
    }
}
