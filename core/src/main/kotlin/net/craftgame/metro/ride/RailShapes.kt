package net.craftgame.metro.ride

import net.craftgame.metro.geo.Point
import net.craftgame.metro.rail.RailShape

/** Works out the shape a rail needs so a cart can pass along a path. */
object RailShapes {
    enum class Dir { NORTH, SOUTH, EAST, WEST }

    /**
     * Returns the shape of the rail at [current] that joins [previous] and [next]. A sloped rail sits on the lower
     * block of a step and points at the higher neighbour. Null when a neighbour is in the same column.
     */
    fun shapeAt(
        previous: Point,
        current: Point,
        next: Point,
    ): RailShape? {
        val upFromPrev = previous.blockY > current.blockY
        val upToNext = next.blockY > current.blockY
        val dirPrev = dirOf(current, previous)
        val dirNext = dirOf(current, next)
        return when {
            upToNext && dirNext != null -> ascending(dirNext)
            upFromPrev && dirPrev != null -> ascending(dirPrev)
            dirPrev != null && dirNext != null -> shapeFor(dirPrev, dirNext)
            else -> null
        }
    }

    /** Returns the compass direction from block [from] to block [to] on the x/z plane, or null for the same column. */
    fun dirOf(
        from: Point,
        to: Point,
    ): Dir? {
        val dx = to.blockX - from.blockX
        val dz = to.blockZ - from.blockZ
        return when {
            dx > 0 -> Dir.EAST
            dx < 0 -> Dir.WEST
            dz > 0 -> Dir.SOUTH
            dz < 0 -> Dir.NORTH
            else -> null
        }
    }

    /** Returns the sloped shape that rises towards [dir]. */
    fun ascending(dir: Dir): RailShape =
        when (dir) {
            Dir.NORTH -> RailShape.ASCENDING_NORTH
            Dir.SOUTH -> RailShape.ASCENDING_SOUTH
            Dir.EAST -> RailShape.ASCENDING_EAST
            Dir.WEST -> RailShape.ASCENDING_WEST
        }

    /** Returns the flat shape that joins directions [a] and [b], or null when they are equal or no shape fits. */
    fun shapeFor(
        a: Dir,
        b: Dir,
    ): RailShape? =
        when (setOf(a, b)) {
            setOf(Dir.NORTH, Dir.SOUTH) -> RailShape.NORTH_SOUTH
            setOf(Dir.EAST, Dir.WEST) -> RailShape.EAST_WEST
            setOf(Dir.NORTH, Dir.EAST) -> RailShape.NORTH_EAST
            setOf(Dir.NORTH, Dir.WEST) -> RailShape.NORTH_WEST
            setOf(Dir.SOUTH, Dir.EAST) -> RailShape.SOUTH_EAST
            setOf(Dir.SOUTH, Dir.WEST) -> RailShape.SOUTH_WEST
            else -> null
        }
}
