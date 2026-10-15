package net.craftgame.metro.data

/**
 * Lines, stations, junctions and edges in memory. Mutators do not save. Subclasses that keep more state about
 * an id override the open mutators and call them.
 *
 * [version] grows on every mutator and on [changed], so caches know when to start over. Editors change model
 * objects in place and always save afterwards, so a save marks those changes too.
 */
open class Network {
    protected val lineMap = LinkedHashMap<String, Line>()
    protected val stationMap = LinkedHashMap<String, Station>()
    protected val junctionMap = LinkedHashMap<String, Junction>()
    protected val edgeMap = LinkedHashMap<String, Edge>()

    /** Line id by station id. */
    protected val stationLine = HashMap<String, String>()

    val lines: Map<String, Line> get() = lineMap
    val stations: Map<String, Station> get() = stationMap
    val junctions: Map<String, Junction> get() = junctionMap
    val edges: Map<String, Edge> get() = edgeMap

    /** Never goes back. */
    var version = 0L
        private set

    protected fun changed() {
        version++
    }

    /** Empties every map. */
    protected fun clear() {
        changed()
        lineMap.clear()
        stationMap.clear()
        junctionMap.clear()
        edgeMap.clear()
        stationLine.clear()
    }

    /** Returns the line that holds the station, or null for an unknown station. */
    fun lineOf(stationId: String): Line? = stationLine[stationId]?.let { lineMap[it] }

    fun lineIdOf(stationId: String): String? = stationLine[stationId]

    /** Returns the line's stations in line order. Stations without an object (see subclasses) are left out. */
    fun stationsOf(lineId: String): List<Station> = lineMap[lineId]?.stations.orEmpty().mapNotNull { stationMap[it] }

    /** [icon] is an item name such as `COMPASS`. Returns null when the id is taken. */
    fun createLine(
        id: String,
        display: String,
        icon: String,
    ): Line? {
        if (lineMap.containsKey(id)) return null
        changed()
        return Line(id, display, icon).also { lineMap[id] = it }
    }

    /** Adds [station] to the line, moving it off its previous line. Does nothing for an unknown line. */
    open fun addStation(
        lineId: String,
        station: Station,
    ) {
        val line = lineMap[lineId] ?: return
        changed()
        val previous = stationLine[station.id]
        if (previous != null && previous != lineId) lineMap[previous]?.stations?.remove(station.id)
        stationMap[station.id] = station
        if (station.id !in line.stations) line.stations.add(station.id)
        stationLine[station.id] = lineId
    }

    enum class MoveResult { MOVED, NO_STATION, NO_LINE, ALREADY_ON_LINE }

    /** Nothing changes unless the result is [MoveResult.MOVED]. */
    fun moveStation(
        stationId: String,
        lineId: String,
    ): MoveResult {
        val station = stationMap[stationId]
        return when {
            station == null -> MoveResult.NO_STATION
            lineMap[lineId] == null -> MoveResult.NO_LINE
            stationLine[stationId] == lineId -> MoveResult.ALREADY_ON_LINE
            else -> {
                addStation(lineId, station)
                MoveResult.MOVED
            }
        }
    }

    /** Also drops the edges of the station. Returns null for an unknown id. */
    fun removeStation(stationId: String): Station? {
        val station = stationMap.remove(stationId) ?: return null
        changed()
        stationLine.remove(stationId)?.let { lineMap[it]?.stations?.remove(stationId) }
        pruneEdgesFor(setOf(stationId))
        return station
    }

    /** Also removes the line's stations and their edges. Returns the number of stations removed. */
    open fun deleteLine(lineId: String): Int {
        val line = lineMap.remove(lineId) ?: return 0
        changed()
        val removed = line.stations.toList()
        for (id in removed) {
            stationMap.remove(id)
            stationLine.remove(id)
        }
        pruneEdgesFor(removed.toSet())
        return removed.size
    }

    /** Also drops the edges that reference its nodes, so routes skip it until the next rebuild. */
    fun removePlatform(
        stationId: String,
        platformId: String,
    ): Platform? {
        val platform = stationMap[stationId]?.removePlatform(platformId) ?: return null
        changed()
        pruneEdges { node -> Nodes.parse(node)?.let { it.stationId == stationId && it.platform == platformId } == true }
        return platform
    }

    private fun pruneEdgesFor(stationIds: Set<String>) {
        if (stationIds.isEmpty()) return
        pruneEdges { node -> Nodes.parse(node)?.stationId?.let { it in stationIds } == true }
    }

    private fun pruneEdges(matches: (String) -> Boolean) {
        edgeMap.entries.removeIf { (_, edge) -> matches(edge.fromNode) || matches(edge.toNode) }
    }

    /** Adds the junction, or replaces the one with the same id. */
    open fun putJunction(junction: Junction) {
        junctionMap[junction.id] = junction
        changed()
    }

    /** Also drops the edges that reference the junction. Returns null for an unknown id. */
    fun removeJunction(id: String): Junction? {
        val junction = junctionMap.remove(id) ?: return null
        changed()
        pruneEdges { node -> Nodes.parseJunction(node)?.id == id }
        return junction
    }

    /** Adds the edge, or replaces the one with the same id. */
    fun putEdge(edge: Edge) {
        edgeMap[edge.id] = edge
        changed()
    }

    /** Replaces all edges generated by a rebuild (`auto = true`) with [edges]. Keeps hand-authored edges. */
    fun replaceAutoEdges(edges: Collection<Edge>) {
        edgeMap.entries.removeIf { it.value.auto }
        for (edge in edges) edgeMap[edge.id] = edge
        changed()
    }
}
