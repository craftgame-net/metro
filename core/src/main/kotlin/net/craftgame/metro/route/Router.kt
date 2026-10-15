package net.craftgame.metro.route

import net.craftgame.metro.config.Speeds
import net.craftgame.metro.data.Edge
import net.craftgame.metro.data.Network
import net.craftgame.metro.data.Nodes
import net.craftgame.metro.geo.Point
import net.craftgame.metro.ride.RouteTrack
import java.util.PriorityQueue

/**
 * Dijkstra over the node graph by estimated travel time ([EdgeCost]). Missing pocket segments (`begin` to `out`,
 * `in` to `eject`) become straight "virtual" edges. The graph is built once and reused while [Network.version],
 * the speeds and the slope speed stay the same.
 */
class Router(
    private val store: Network,
    /** Called on every query: the graph is rebuilt when the result differs from the one it was built with. */
    private val speeds: () -> Speeds = { Speeds() },
    /** Virtual edges are reported only while the graph is built, so a change here rebuilds it. */
    private val reporting: () -> Boolean = { true },
    /** Speed limit on slopes in blocks per tick, called on every query like [speeds]. */
    private val slopeSpeed: () -> Double = { Slopes.OFF },
    /**
     * Called with (from node, to node) for each virtual edge added while the graph is built. It runs synchronously
     * on the thread that made the query. Null: no callback.
     */
    private val onVirtualEdge: ((String, String) -> Unit)? = null,
) {
    /** Worked out once per graph, so [Rules.allowed] does not parse nodes on every step. */
    private class Arc(
        val edge: Edge,
        val cost: Double,
        val from: Nodes.Ref?,
        val to: Nodes.Ref?,
        /** True when the edge leaves a terminal station for another one. */
        val leavesTerminal: Boolean,
        /** Id of the terminal's line when [leavesTerminal]; null otherwise or when the station has no line. */
        val terminalLine: String?,
    )

    /**
     * The graph as built for one data version, speeds, slope speed and reporting state. It is stale when any of
     * them changes.
     */
    private class Graph(
        val version: Long,
        val speeds: Speeds,
        val slopeSpeed: Double,
        val reporting: Boolean,
        val adjacency: Map<String, List<Arc>>,
    )

    private var cached: Graph? = null

    private fun graph(): Graph {
        val speeds = speeds()
        val slopeSpeed = slopeSpeed()
        val reporting = reporting()
        val current = cached
        val fresh =
            current?.let {
                it.version == store.version &&
                    it.speeds == speeds &&
                    it.slopeSpeed == slopeSpeed &&
                    it.reporting == reporting
            }
        if (current != null && fresh == true) return current
        val edges = LinkedHashMap<String, MutableList<Edge>>()
        for (edge in store.edges.values) {
            edges.getOrPut(edge.fromNode) { ArrayList() }.add(edge)
        }
        for (station in store.stations.values) {
            for (platform in station.platforms) {
                addVirtual(
                    edges,
                    Nodes.of(station.id, platform.id, "begin"),
                    Nodes.of(station.id, platform.id, "out"),
                    platform.begin,
                    platform.outbound,
                )
                addVirtual(
                    edges,
                    Nodes.of(station.id, platform.id, "in"),
                    Nodes.of(station.id, platform.id, "eject"),
                    platform.inbound,
                    platform.eject,
                )
            }
        }
        val adjacency = edges.mapValues { (_, list) -> list.map { arc(it, speeds, slopeSpeed) } }
        return Graph(store.version, speeds, slopeSpeed, reporting, adjacency).also { cached = it }
    }

    private fun arc(
        edge: Edge,
        speeds: Speeds,
        slopeSpeed: Double,
    ): Arc {
        val from = Nodes.parse(edge.fromNode)
        val to = Nodes.parse(edge.toNode)
        val terminal = from != null && store.stations[from.stationId]?.terminal == true
        val leaves = terminal && to?.stationId != from.stationId
        val line = if (leaves) store.lineIdOf(from.stationId) else null
        return Arc(edge, EdgeCost.ticks(edge, speeds, slopeSpeed), from, to, leaves, line)
    }

    private inner class Rules(
        val origin: Nodes.Ref?,
        val destination: Nodes.Ref?,
    ) {
        private val originLine = store.lineIdOf(origin?.stationId.orEmpty())
        private val destinationLine = store.lineIdOf(destination?.stationId.orEmpty())

        /**
         * A ride may not pass through another platform of the station it started at (that would be a U-turn on a
         * junction), but may end there. A terminal only blocks rides that would pass it and circle back on its
         * own line.
         */
        fun allowed(arc: Arc): Boolean = allowsTerminal(arc) && allowsReentry(arc)

        /** Riding out of a terminal, or through it toward another line, is always fine. */
        private fun allowsTerminal(arc: Arc): Boolean {
            val from = arc.from
            if (!arc.leavesTerminal || from == null) return true
            val departsTerminal =
                origin != null && origin.stationId == from.stationId && origin.platform == from.platform
            val line = arc.terminalLine
            val originOnLine = line != null && originLine == line
            val destinationOnLine = line != null && destinationLine == line
            return departsTerminal || !(originOnLine && destinationOnLine)
        }

        private fun allowsReentry(arc: Arc): Boolean {
            val to = arc.to
            val sameStation = origin != null && to != null && to.stationId == origin.stationId
            val otherPlatform = sameStation && to.platform != origin.platform
            if (!otherPlatform) return true
            return destination != null &&
                destination.stationId == origin.stationId &&
                destination.platform == to.platform
        }
    }

    /** Returns the stored anchor of a station node, or null for a junction node or an unset anchor. */
    fun anchorPoint(node: String): Point? =
        Nodes.parse(node)?.let { ref -> store.stations[ref.stationId]?.platform(ref.platform)?.anchor(ref.role) }

    /**
     * Returns every node that can be reached from [origin], in the order a search finds them. The terminal rule
     * (a ride may not pass through a terminal station) needs a destination, so it is not applied here.
     */
    fun reachableFrom(origin: String): Set<String> {
        val adjacency = graph().adjacency
        val rules = Rules(Nodes.parse(origin), null)
        val seen = LinkedHashSet<String>()
        val queue = ArrayDeque<String>()
        queue.add(origin)
        seen.add(origin)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            for (arc in adjacency[current].orEmpty()) {
                if (rules.allowed(arc) && seen.add(arc.edge.toNode)) queue.add(arc.edge.toNode)
            }
        }
        return seen
    }

    /** Returns the `eject` nodes among [reachableFrom] [fromNode]. */
    fun reachableEjects(fromNode: String): Set<String> {
        val reachable = reachableFrom(fromNode)
        return reachable.filterTo(LinkedHashSet()) { it.endsWith(":eject") }
    }

    /**
     * Returns the route (edges in travel order) to every `eject` reachable from [origin], except those of the station
     * [skipStation] (null: none skipped). Keys are in the order [reachableFrom] finds them.
     */
    fun routesToEjects(
        origin: String,
        skipStation: String?,
    ): Map<String, List<Edge>> {
        val ejects = reachableEjects(origin).filter { Nodes.parse(it)?.stationId != skipStation }
        val routes = routesFrom(origin, ejects)
        val ordered = LinkedHashMap<String, List<Edge>>()
        for (eject in ejects) routes[eject]?.let { ordered[eject] = it }
        return ordered
    }

    /**
     * Returns the quickest route from [origin] to [destination] as edges in travel order: empty when both are the same
     * node, null when [destination] cannot be reached. Quickest means least estimated travel time, not fewest edges or
     * shortest length: with junctions there can be a slow branch and a faster way around.
     */
    fun findRoute(
        origin: String,
        destination: String,
    ): List<Edge>? {
        if (origin == destination) return emptyList()
        val search = search(origin, Nodes.parse(destination), stopAt = destination)
        return if (destination in search.visited) reconstruct(search.previous, origin, destination) else null
    }

    /**
     * The routes [findRoute] would give to [targets], unreachable ones left out, with one search per group of targets
     * that [Rules.allowed] treats alike.
     *
     * [Rules.allowed] looks at the destination in two ways only: whether it is on the line of a terminal of the
     * origin's line, and whether it is another platform of the origin station. Targets that agree on both get the same
     * edges, so one full run serves them all. It visits nodes in the same order with the same tie-breaks as
     * [findRoute], which only stops earlier, so the routes are identical. Targets at other stations need at most
     * two runs.
     */
    fun routesFrom(
        origin: String,
        targets: Collection<String>,
    ): Map<String, List<Edge>> {
        val originRef = Nodes.parse(origin)
        val originLine = originRef?.let { store.lineIdOf(it.stationId) }
        val lineHasTerminal = originLine != null && store.stationsOf(originLine).any { it.terminal }
        val result = LinkedHashMap<String, List<Edge>>()
        val groups = LinkedHashMap<Any, MutableList<String>>()
        for (target in targets) {
            if (target == origin) {
                result[target] = emptyList()
            } else {
                val terminalLine = originLine.takeIf { lineHasTerminal }
                val group = destinationClass(store, originRef, terminalLine, Nodes.parse(target))
                groups.getOrPut(group) { ArrayList() }.add(target)
            }
        }
        for (group in groups.values) {
            val search = search(origin, Nodes.parse(group.first()))
            for (target in group) {
                if (target in search.visited) result[target] = reconstruct(search.previous, origin, target)
            }
        }
        return result
    }

    private class Search(
        val previous: Map<String, Edge>,
        val visited: Set<String>,
    )

    /** Runs Dijkstra from [origin]. Stops once [stopAt] is settled; null: runs through the whole graph. */
    private fun search(
        origin: String,
        destination: Nodes.Ref?,
        stopAt: String? = null,
    ): Search {
        val adjacency = graph().adjacency
        val rules = Rules(Nodes.parse(origin), destination)
        val previous = HashMap<String, Edge>()
        val distances = HashMap<String, Double>()
        val visited = HashSet<String>()
        val queue = PriorityQueue<Step>(compareBy { it.distance })
        distances[origin] = 0.0
        queue.add(Step(origin, 0.0))

        while (queue.isNotEmpty() && (stopAt == null || stopAt !in visited)) {
            val current = queue.poll().node
            if (!visited.add(current) || current == stopAt) continue
            for (arc in adjacency[current].orEmpty()) {
                val edge = arc.edge
                val candidate = distances.getValue(current) + arc.cost
                val better = candidate < (distances[edge.toNode] ?: Double.POSITIVE_INFINITY)
                if (better && rules.allowed(arc)) {
                    distances[edge.toNode] = candidate
                    previous[edge.toNode] = edge
                    queue.add(Step(edge.toNode, candidate))
                }
            }
        }
        return Search(previous, visited)
    }

    private data class Step(
        val node: String,
        val distance: Double,
    )

    /** Returns the length of [route] in blocks. */
    fun routeLength(route: List<Edge>): Double = RouteTrack(RoutePlanner.buildPath(route)).totalLength

    private fun addVirtual(
        map: MutableMap<String, MutableList<Edge>>,
        from: String,
        to: String,
        fromPoint: Point?,
        toPoint: Point?,
    ) {
        if (fromPoint == null || toPoint == null) return
        val list = map.getOrPut(from) { ArrayList() }
        if (list.any { it.toNode == to }) return
        val points = arrayListOf(fromPoint, toPoint)
        list.add(Edge("virtual:$from->$to", from, to, null, points.toMutableList()))
        onVirtualEdge?.invoke(from, to)
    }
}

/** The part of [destination] that [Rules.allowed] can see, as a grouping key for [Router.routesFrom]. */
private fun destinationClass(
    store: Network,
    origin: Nodes.Ref?,
    terminalLine: String?,
    destination: Nodes.Ref?,
): Any {
    val onLine =
        terminalLine != null && destination != null && store.lineIdOf(destination.stationId) == terminalLine
    val sameStation = origin != null && destination != null && destination.stationId == origin.stationId
    val reentry = if (sameStation && destination.platform != origin.platform) destination.platform else null
    return onLine to reentry
}

private fun reconstruct(
    previous: Map<String, Edge>,
    origin: String,
    destination: String,
): List<Edge> {
    val route = ArrayList<Edge>()
    var current = destination
    while (current != origin) {
        val edge = previous[current] ?: return emptyList()
        route.add(edge)
        current = edge.fromNode
    }
    route.reverse()
    return route
}
