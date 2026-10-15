package net.craftgame.metro.route

import net.craftgame.metro.config.Speeds
import net.craftgame.metro.data.Edge
import net.craftgame.metro.geo.Point

/**
 * Turns edges into the polyline a ride follows, with a speed per segment. It uses no server state, so tests
 * run without a server.
 */
object RoutePlanner {
    /** Joins the points of [route] into one path. A point shared by two edges appears once. */
    fun buildPath(route: List<Edge>): List<Point> {
        val path = ArrayList<Point>()
        for (edge in route) {
            val points = edge.points
            if (path.isEmpty()) {
                path.addAll(points)
            } else if (points.isNotEmpty()) {
                path.addAll(points.subList(1, points.size))
            }
        }
        return path
    }

    /**
     * Returns the plan for [route]. Speeds are in blocks per tick. [fallbackSpeed] is for edges with no speed
     * (virtual edges, the straight connectors the router adds where a pocket edge is missing). No segment runs faster
     * than [maxSpeed] or slower than [Speeds.MIN], so a bad stored speed never gives a limit of zero. Edges are
     * densified first.
     */
    fun buildPlan(
        route: List<Edge>,
        fallbackSpeed: Double,
        maxSpeed: Double = Double.POSITIVE_INFINITY,
    ): RoutePlan {
        val points = ArrayList<Point>()
        val speeds = ArrayList<Double>()
        val candidates = sortedSetOf<Int>()
        for (edge in route) {
            val dense = Polyline.densify(edge.points)
            val pts = dense.points
            if (pts.isEmpty()) continue
            val speed = (edge.cruiseSpeed ?: fallbackSpeed).coerceAtMost(maxSpeed).coerceAtLeast(Speeds.MIN)
            // A shared joint maps onto the previous edge's last point.
            val shared = points.isNotEmpty() && samePoint(pts.first(), points.last())
            val base = if (shared) points.size - 1 else points.size
            val count = pts.size - 1
            val start = edge.startSwitchSpeed
            val end = edge.endSwitchSpeed

            fun segment(index: Int) = SwitchZone.speed(speed, start, end, index, count).coerceAtLeast(Speeds.MIN)
            if (points.isEmpty()) {
                points.addAll(pts)
                for (j in 0 until count) speeds.add(segment(j))
            } else {
                // Without a shared joint, the step from the previous edge onto point 0 runs at this edge's speed.
                if (!shared) {
                    points.add(pts[0])
                    speeds.add(speed)
                }
                for (i in 1 until pts.size) {
                    points.add(pts[i])
                    speeds.add(segment(i - 1))
                }
            }
            for (i in edgeCandidates(edge, dense)) candidates.add(base + i)
        }
        if (points.isNotEmpty()) speeds.add(0.0)
        return RoutePlan(points, speeds, candidates.toList())
    }

    /**
     * Returns the dense indices of the edge's switch marks, or every dense point when the marks are unknown or stale.
     */
    private fun edgeCandidates(
        edge: Edge,
        dense: Polyline.Dense,
    ): List<Int> {
        val size = edge.points.size
        val marks = edge.switches
        return if (marks == null || marks.any { it !in 0 until size }) {
            dense.points.indices.toList()
        } else {
            marks.map { dense.indices[it] }
        }
    }

    private fun samePoint(
        a: Point,
        b: Point,
    ): Boolean = a.blockX == b.blockX && a.blockY == b.blockY && a.blockZ == b.blockZ

    /** The plan for a ride from [start] to [end], with `speed.station` as fallback and `speed.max` as cap. */
    fun plan(
        route: List<Edge>,
        start: Point,
        end: Point,
        speeds: Speeds,
    ): RoutePlan = assemble(start, buildPlan(route, speeds.station, speeds.max), end, speeds.station)

    /**
     * Prepends [start] and appends [end] to [plan], unless they already match the first or last point.
     * [fallback] is the speed in blocks per tick for a segment that has no speed in [plan].
     */
    fun assemble(
        start: Point,
        plan: RoutePlan,
        end: Point,
        fallback: Double,
    ): RoutePlan {
        val points = ArrayList<Point>()
        val speeds = ArrayList<Double>()

        fun append(
            point: Point,
            segmentSpeed: Double,
        ): Int {
            val last = points.lastOrNull()
            if (last == null) {
                points.add(point)
            } else if (!samePoint(last, point)) {
                points.add(point)
                speeds.add(segmentSpeed)
            }
            return points.size - 1
        }

        // The trailing 0.0 is the stop marker, never a segment's limit.
        val segments = plan.segmentSpeeds.take((plan.points.size - 1).coerceAtLeast(0))
        val first = segments.firstOrNull() ?: fallback
        append(start, first)
        val placed = IntArray(plan.points.size)
        plan.points.forEachIndexed { index, point ->
            val speed = if (index == 0) first else segments.getOrElse(index - 1) { fallback }
            placed[index] = append(point, speed)
        }
        append(end, segments.lastOrNull() ?: fallback)
        speeds.add(0.0)
        val candidates =
            plan.switchCandidates
                .filter { it in placed.indices }
                .map { placed[it] }
                .distinct()
        return RoutePlan(points, speeds, candidates)
    }
}
