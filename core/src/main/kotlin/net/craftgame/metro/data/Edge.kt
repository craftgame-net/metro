package net.craftgame.metro.data

import net.craftgame.metro.geo.Point

/**
 * A directed segment between two anchors. [points] is the rail polyline, stored compressed.
 *
 * [auto] edges are regenerated on every rebuild; edges with `auto = false` are authored by hand and survive it.
 *
 * [switches] are the indices of [points] whose rail a ride may have to reshape. Null means unknown,
 * so a ride checks every point.
 *
 * [startSwitchSpeed] and [endSwitchSpeed] slow the first or last few blocks (see `route.SwitchZone`).
 */
data class Edge(
    val id: String,
    var fromNode: String,
    var toNode: String,
    /** Blocks per tick. Null: unset, so planning falls back to the station speed. */
    var cruiseSpeed: Double?,
    /** Block-centre points along the rail, compressed (see `route.Polyline`). */
    val points: MutableList<Point>,
    val auto: Boolean = true,
    var switches: List<Int>? = null,
    /** Blocks per tick at the start of the edge. Null: no switch zone. */
    var startSwitchSpeed: Double? = null,
    /** Blocks per tick at the end of the edge. Null: no switch zone. */
    var endSwitchSpeed: Double? = null,
)
