package net.craftgame.metro.route

import net.craftgame.metro.data.Junction
import net.craftgame.metro.geo.Vec3
import kotlin.math.abs

/** Axes of a junction block. A junction must sit on a flat, straight track. */
object JunctionGeometry {
    /**
     * Returns the axes (`x`, `z`) of a junction, or null when the rails do not match [kind]. [xPair]/[zPair] mean two
     * opposed level neighbours on that axis (the straight track); [xCount]/[zCount] count all rail neighbours on that
     * axis, where the third one is the side rail.
     *
     * - `split`/`join`: one opposed pair plus one side rail, so one axis.
     * - `cross`: two opposed pairs, so both axes.
     */
    fun axes(
        kind: Junction.Kind,
        xPair: Boolean,
        xCount: Int,
        zPair: Boolean,
        zCount: Int,
    ): List<String>? {
        val twoByTwo = xCount == 2 && zCount == 2
        return when (kind) {
            Junction.Kind.CROSS ->
                if (xPair && zPair && twoByTwo) listOf("x", "z") else null
            Junction.Kind.SPLIT, Junction.Kind.JOIN ->
                when {
                    xPair && xCount == 2 && zCount == 1 -> listOf("x")
                    zPair && zCount == 2 && xCount == 1 -> listOf("z")
                    else -> null
                }
        }
    }

    /** Returns `x` for a heading that points east or west, else `z`. */
    fun axisOf(heading: Vec3): String =
        when (TrackChain.cardinal(heading)) {
            "east", "west" -> "x"
            else -> "z"
        }

    /**
     * True when [path], which starts at the junction, first moves against [heading] (the travel direction of the
     * straight track). Only the first [lookahead] points after the start are checked. A side rail that bends back
     * against travel (a turn of about 135 degrees) gives true; a forward one gives false.
     */
    fun turnsBack(
        path: List<Vec3>,
        heading: Vec3,
        lookahead: Int = BRANCH_LOOKAHEAD,
    ): Boolean {
        val origin = path.firstOrNull() ?: return false
        val first =
            path
                .drop(1)
                .take(lookahead)
                .map { (it.x - origin.x) * heading.x + (it.z - origin.z) * heading.z }
                .firstOrNull { abs(it) > EPSILON }
        return first != null && first < 0
    }

    /** Points. Enough for a diagonal or a curve to show its way. */
    const val BRANCH_LOOKAHEAD = 8

    private const val EPSILON = 1e-6
}
