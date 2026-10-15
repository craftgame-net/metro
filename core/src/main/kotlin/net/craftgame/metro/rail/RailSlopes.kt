package net.craftgame.metro.rail

import net.craftgame.metro.rail.RailShape

/** Tells whether two neighbouring rails connect across a height step. */
object RailSlopes {
    /**
     * On one level, always. One block up, only when [from] ascends towards the neighbour; one block down, only
     * when the neighbour ascends back.
     */
    fun connects(
        dx: Int,
        dy: Int,
        dz: Int,
        from: RailShape?,
        to: RailShape?,
    ): Boolean =
        when {
            dy == 0 -> true
            dy > 0 -> from != null && from == ascending(dx, dz)
            else -> to != null && to == ascending(-dx, -dz)
        }

    private fun ascending(
        dx: Int,
        dz: Int,
    ): RailShape? =
        when {
            dx > 0 -> RailShape.ASCENDING_EAST
            dx < 0 -> RailShape.ASCENDING_WEST
            dz > 0 -> RailShape.ASCENDING_SOUTH
            dz < 0 -> RailShape.ASCENDING_NORTH
            else -> null
        }
}
