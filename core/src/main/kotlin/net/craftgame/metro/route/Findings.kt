package net.craftgame.metro.route

import net.craftgame.metro.data.Edge
import net.craftgame.metro.geo.BlockPos
import net.craftgame.metro.geo.Point
import net.craftgame.metro.rail.RailPathfinder
import net.craftgame.metro.rail.RailWorld

/** Collects warnings and the start nodes of searches that gave up. */
internal class Findings(
    /** Rail blocks one search may visit before it gives up. */
    val maxSearch: Int,
) {
    val warnings = LinkedHashSet<RebuildWarning>()

    /** Nodes whose path search stopped at [maxSearch]. */
    val limitHits = LinkedHashSet<String>()

    /** Replace the store's edges only once the whole rebuild has succeeded. */
    val edges = ArrayList<Edge>()

    /** Runs [RailPathfinder.findNearest] and records [from] in [limitHits] when it stops at [maxSearch]. */
    fun nearest(
        from: String,
        world: RailWorld,
        anchor: Point,
        goals: Set<BlockPos>,
        departure: RailPathfinder.Departure,
    ): RailPathfinder.PathResult {
        val result = RailPathfinder.findNearest(world, anchor, goals, departure, maxSearch)
        if (result.limitReached) limitHits += from
        return result
    }

    /**
     * Runs [RailPathfinder.findPath] and records [from] in [limitHits] when it stops at [maxSearch]. Returns the path
     * or null.
     */
    fun path(
        from: String,
        world: RailWorld,
        start: Point,
        end: Point,
    ): List<Point>? {
        val result = RailPathfinder.findPath(world, start, end, maxSearch)
        if (result.limitReached) limitHits += from
        return result.path
    }

    /** Returns one warning per node in [limitHits]. */
    fun limitWarnings(text: WarningText): List<RebuildWarning> =
        limitHits.map {
            text.warning("search-limit", IssueTarget.Node(it), "from" to it, "limit" to maxSearch.toString())
        }
}
