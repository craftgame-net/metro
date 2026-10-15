package net.craftgame.metro.rail

import net.craftgame.metro.data.Edge
import net.craftgame.metro.geo.Point
import net.craftgame.metro.ride.RailShapes
import net.craftgame.metro.route.Polyline

/** Finds the points of an edge whose rail a ride may have to reshape, so a departure reads only those blocks. */
object SwitchMarks {
    /** Returns [path] compressed, with the switch marks of [read]. */
    fun stored(
        rails: RailWorlds,
        path: List<Point>,
    ): Polyline.Marked = Polyline.compressMarked(path, read(rails, path))

    /** Recomputes the marks of [edge] from the world and replaces its points with the compressed ones. */
    fun remark(
        rails: RailWorlds,
        edge: Edge,
    ) {
        val stored = stored(rails, Polyline.densify(edge.points).points)
        edge.points.clear()
        edge.points.addAll(stored.points)
        edge.switches = stored.marks
    }

    /**
     * Returns the indices of [path] that may need reshaping. Null when [path] is empty or its world is not
     * loaded. Reads blocks, so server thread.
     */
    fun read(
        rails: RailWorlds,
        path: List<Point>,
    ): List<Int>? {
        val world = path.firstOrNull()?.let { rails.world(it.world) } ?: return null
        return candidates(
            path,
            neighbours = { point ->
                RailPathfinder.connections(world, point.block()).map { RailPathfinder.center(world, it) }
            },
            shape = { point -> world.railShape(point.block()) },
        )
    }

    /**
     * Returns the indices of [path] that are candidates, given the rail neighbours and the shape of a rail.
     * A switch (3+ rail neighbours) is always a candidate: redstone and other rides flip it. Any other rail is one only
     * if its shape is wrong for the path right now. The ends of a path are interior points of a longer route, where
     * the neighbour beyond is unknown, so an end counts unless it sits between exactly two rails it already joins.
     */
    fun candidates(
        path: List<Point>,
        neighbours: (Point) -> List<Point>,
        shape: (Point) -> RailShape?,
    ): List<Int> {
        val result = ArrayList<Int>()
        for ((index, point) in path.withIndex()) {
            val current = shape(point) ?: continue
            val around = neighbours(point)
            val needed =
                if (index in 1 until path.size - 1) {
                    listOfNotNull(RailShapes.shapeAt(path[index - 1], point, path[index + 1]))
                } else if (around.size == 2) {
                    listOfNotNull(
                        RailShapes.shapeAt(around[0], point, around[1]),
                        RailShapes.shapeAt(around[1], point, around[0]),
                    )
                } else {
                    null
                }
            val candidate =
                around.size >= RailPathfinder.SWITCH_MIN_NEIGHBOURS || needed == null || needed.any { it != current }
            if (candidate) result.add(index)
        }
        return result
    }
}
