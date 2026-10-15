package net.craftgame.metro.data

import net.craftgame.metro.config.Speeds
import net.craftgame.metro.debug.DebugLevel
import net.craftgame.metro.debug.DebugLog
import net.craftgame.metro.geo.Cardinal
import net.craftgame.metro.geo.Point
import org.yaml.snakeyaml.error.YAMLException
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Reads and writes the [Network] to data.yml. It does not build edges. [version] also grows on [load] and [save].
 * Entries in a world that is not loaded are kept as read and written back unchanged.
 */
class DataStore(
    private val file: File,
    private val logger: Logger,
    /** True when the world of this name is loaded. */
    private val worldLoaded: (String) -> Boolean,
    /** Returns the item name of the icon a stored name stands for, or null when it cannot be shown as an icon. */
    private val iconName: (String) -> String?,
    /** Item name of the icon for a station whose stored icon is missing or unusable. */
    private val defaultIcon: () -> String,
    /** Display name of the fallback line `default`, which takes stations that have no line. */
    private val defaultLineName: () -> String = { "Default" },
    private val debugLog: DebugLog = DebugLog.OFF,
) : Network() {
    /** Kept as read and written back unchanged, so a world that loads late does not lose its entries. */
    private val unloadedStations = LinkedHashMap<String, Unloaded>()
    private val unloadedJunctions = LinkedHashMap<String, Unloaded>()
    private val unloadedEdges = LinkedHashMap<String, Unloaded>()

    private class Unloaded(
        val world: String,
        val values: Map<String, Any?>,
    )

    /** True after the file could not be read: the data is empty, so [save] refuses to overwrite the file. */
    var loadFailed = false
        private set

    private val writer = SnapshotWriter(file, logger)

    /** Stored speeds that [speed] rejected during the current [load]. */
    private var invalidSpeeds = 0

    override fun addStation(
        lineId: String,
        station: Station,
    ) {
        if (lineId !in lines) return
        super.addStation(lineId, station)
        unloadedStations.remove(station.id)
    }

    override fun deleteLine(lineId: String): Int {
        lines[lineId]?.stations?.forEach { unloadedStations.remove(it) }
        return super.deleteLine(lineId)
    }

    override fun putJunction(junction: Junction) {
        unloadedJunctions.remove(junction.id)
        super.putJunction(junction)
    }

    /** Waits for queued writes first, so the file is never a stale copy. */
    fun load() {
        writer.flush()
        clear()
        unloadedStations.clear()
        unloadedJunctions.clear()
        unloadedEdges.clear()
        loadFailed = false
        invalidSpeeds = 0
        if (!file.exists()) return

        val yaml = read() ?: return

        yaml.section("lines")?.let { loadLines(it) }
        yaml.section("stations")?.let { loadStations(it) }
        yaml.section("junctions")?.let { loadJunctions(it) }
        yaml.section("edges")?.let { loadEdges(it) }
        warnUnloaded()
        if (invalidSpeeds > 0) {
            logger.warning(
                "Metro: ignored $invalidSpeeds speed(s) in ${file.name} that are not a number of at least " +
                    "${Speeds.MIN}; those run at the default speed until set again",
            )
        }

        reconcile()
        debugLog.log(DebugLevel.EVENT, null) {
            "data loaded: ${lines.size} line(s), ${stations.size} station(s), ${junctions.size} junction(s), " +
                "${edges.size} edge(s)"
        }
    }

    /** Null, with [loadFailed] set and a copy of the file kept, when the file cannot be read. */
    private fun read(): YamlSection? {
        val error =
            try {
                return YamlSection.read(file)
            } catch (e: IOException) {
                e
            } catch (e: YAMLException) {
                e
            }
        loadFailed = true
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val copy = File(file.parentFile, "${file.name}.broken-$stamp")
        val kept =
            try {
                Files.copy(file.toPath(), copy.toPath(), StandardCopyOption.REPLACE_EXISTING)
                "a copy is in ${copy.name}"
            } catch (e: IOException) {
                "could not copy it: ${e.message}"
            }
        logger.log(
            Level.SEVERE,
            "Metro: could not read ${file.name} ($kept). Nothing is loaded and nothing will be saved " +
                "until it reads: fix the file and run /metro reload",
            error,
        )
        return null
    }

    private fun loadLines(section: YamlSection) {
        for (id in section.keys) {
            val entry = section.section(id) ?: continue
            val line =
                Line(
                    id = id,
                    display = entry.string("display") ?: id,
                    icon = icon(entry.string("icon")) ?: "COMPASS",
                    speed = speed(entry, "speed"),
                )
            line.stations.addAll(entry.stringList("stations"))
            lineMap[id] = line
        }
    }

    private fun loadStations(section: YamlSection) {
        for (id in section.keys) {
            val entry = section.section(id)
            val worldName = entry?.string("world")
            if (entry != null && worldName != null) loadStation(id, entry, worldName)
        }
    }

    private fun loadStation(
        id: String,
        entry: YamlSection,
        worldName: String,
    ) {
        if (!worldLoaded(worldName)) {
            unloadedStations[id] = Unloaded(worldName, entry.toMap())
            return
        }
        val station =
            Station(
                id = id,
                display = entry.string("display") ?: id,
                world = worldName,
                icon = icon(entry.string("icon")) ?: defaultIcon(),
                terminal = entry.boolean("terminal", false),
            )
        if ("platforms" in entry) {
            entry.mapList("platforms").forEach { station.platforms.add(readPlatform(worldName, it)) }
        } else {
            // Data from before platforms existed: the anchors are stored on the station itself.
            station.platforms.add(
                Platform(
                    board = readPoint(worldName, entry["board"]),
                    begin = readPoint(worldName, entry["begin"]),
                    outbound = readPoint(worldName, entry["out"]),
                    inbound = readPoint(worldName, entry["in"]),
                    eject = readPoint(worldName, entry["eject"]),
                    land = readPoint(worldName, entry["land"]),
                ),
            )
        }
        for ((old, new) in station.ensurePlatformIds()) {
            logger.warning(
                "Metro: station '$id' has more than one platform '$old'; the later one is now '$new' " +
                    "(run /metro rebuild to re-link it)",
            )
        }
        stationMap[id] = station
    }

    private fun loadJunctions(section: YamlSection) {
        for (id in section.keys) {
            val entry = section.section(id)
            val worldName = entry?.string("world")
            if (entry != null && worldName != null) loadJunction(id, entry, worldName)
        }
    }

    private fun loadJunction(
        id: String,
        entry: YamlSection,
        worldName: String,
    ) {
        if (!worldLoaded(worldName)) {
            unloadedJunctions[id] = Unloaded(worldName, entry.toMap())
            return
        }
        val kind = Junction.kindOf(entry.string("kind").orEmpty())
        if (kind == null) {
            logger.warning("Junction '$id': unknown kind '${entry.string("kind")}'")
            return
        }
        junctionMap[id] =
            Junction(
                id = id,
                world = worldName,
                location = Point(worldName, entry.double("x"), entry.double("y"), entry.double("z")),
                kind = kind,
                turns = entry.boolean("turns", true),
                speed = speed(entry, "speed"),
            )
    }

    private fun loadEdges(section: YamlSection) {
        for (id in section.keys) {
            val entry = section.section(id)
            val fromNode = entry?.string("from-node")
            val toNode = entry?.string("to-node")
            if (entry != null && fromNode != null && toNode != null) loadEdge(id, entry, fromNode, toNode)
        }
    }

    private fun loadEdge(
        id: String,
        entry: YamlSection,
        fromNode: String,
        toNode: String,
    ) {
        val unloadedWorld = unloadedWorldOf(fromNode) ?: unloadedWorldOf(toNode)
        if (unloadedWorld != null) {
            unloadedEdges[id] = Unloaded(unloadedWorld, entry.toMap())
            return
        }
        val world = loadedWorldOf(fromNode) ?: loadedWorldOf(toNode) ?: return
        edgeMap[id] = readEdge(id, fromNode, toNode, entry, world)
    }

    private fun readEdge(
        id: String,
        fromNode: String,
        toNode: String,
        entry: YamlSection,
        world: String,
    ): Edge {
        val points = readPoints(world, entry["points"]).toMutableList()
        val speed = speed(entry, "cruise-speed")
        val auto = entry.boolean("auto", true)
        val switches = if (entry.isList("switches")) entry.intList("switches") else null
        val startSwitch = speed(entry, "start-switch-speed")
        val endSwitch = speed(entry, "end-switch-speed")
        return Edge(id, fromNode, toNode, speed, points, auto, switches, startSwitch, endSwitch)
    }

    /**
     * A value that is not a finite number of at least [Speeds.MIN] counts as unset (`double` would read
     * garbage as 0).
     */
    private fun speed(
        entry: YamlSection,
        key: String,
    ): Double? {
        if (key !in entry) return null
        val value = entry[key]
        val speed = ((value as? Number)?.toDouble() ?: value?.toString()?.toDoubleOrNull())?.takeIf(Speeds::valid)
        if (speed == null) invalidSpeeds++
        return speed
    }

    private fun unloadedWorldOf(node: String): String? {
        Nodes.parseJunction(node)?.let { return unloadedJunctions[it.id]?.world }
        return Nodes.parse(node)?.let { unloadedStations[it.stationId]?.world }
    }

    private fun warnUnloaded() {
        val worldNames = (unloadedStations.values + unloadedJunctions.values + unloadedEdges.values).map { it.world }
        if (worldNames.isNotEmpty()) {
            debugLog.log(DebugLevel.DECISION, null) {
                "data load kept ${unloadedStations.size} station(s), ${unloadedJunctions.size} junction(s) and " +
                    "${unloadedEdges.size} edge(s) of unloaded worlds"
            }
        }
        for (world in worldNames.distinct()) {
            val stations = unloadedStations.values.count { it.world == world }
            val junctions = unloadedJunctions.values.count { it.world == world }
            val edges = unloadedEdges.values.count { it.world == world }
            logger.warning(
                "Metro: world '$world' is not loaded; keeping its $stations station(s), $junctions junction(s) " +
                    "and $edges segment(s) unchanged in data.yml until it is (then run /metro reload)",
            )
        }
    }

    /** Returns the item name of a usable icon, or null. */
    private fun icon(name: String?): String? = name?.let(iconName)

    /** Drops unknown or duplicate station ids and moves line-less stations into `default`. */
    private fun reconcile() {
        stationLine.clear()
        val placedUnloaded = HashSet<String>()
        for (line in lineMap.values) {
            val valid = ArrayList<String>(line.stations.size)
            for (stationId in line.stations) {
                when {
                    // A station in an unloaded world keeps its place but has no object to index.
                    stationId in unloadedStations -> if (placedUnloaded.add(stationId)) valid.add(stationId)
                    stationMap[stationId] == null || stationLine.containsKey(stationId) -> continue
                    else -> {
                        stationLine[stationId] = line.id
                        valid.add(stationId)
                    }
                }
            }
            line.stations.clear()
            line.stations.addAll(valid)
        }

        val orphans = stationMap.keys.filter { it !in stationLine }
        if (orphans.isNotEmpty()) {
            val fallback = lineMap.getOrPut(DEFAULT_LINE_ID) { Line(DEFAULT_LINE_ID, defaultLineName()) }
            for (stationId in orphans) {
                fallback.stations.add(stationId)
                stationLine[stationId] = fallback.id
            }
            logger.warning("Metro: attached ${orphans.size} station(s) without a line to '${fallback.id}'")
        }
    }

    /**
     * Snapshots on the calling thread and writes in the background. Returns false, writing nothing,
     * while [loadFailed] blocks saving, so callers can tell the player.
     */
    fun save(): Boolean {
        changed()
        if (loadFailed) {
            logger.severe("Metro: not saving ${file.name}: it could not be read at load; fix it and run /metro reload")
            return false
        }
        val root = LinkedHashMap<String, Any?>()
        root["version"] = SCHEMA_VERSION
        linesMap().takeIf { it.isNotEmpty() }?.let { root["lines"] = it }
        stationsMap().takeIf { it.isNotEmpty() }?.let { root["stations"] = it }
        junctionsMap().takeIf { it.isNotEmpty() }?.let { root["junctions"] = it }
        edgesMap().takeIf { it.isNotEmpty() }?.let { root["edges"] = it }
        // Logged before the hand-off: the writer thread must not use the debug log.
        debugLog.log(DebugLevel.DECISION, null) {
            "data save: ${lines.size} line(s), ${stations.size} station(s), ${junctions.size} junction(s), " +
                "${edges.size} edge(s)"
        }
        writer.submit(YamlSection.write(root))
        return true
    }

    // Each id is one key. Ids hold no dots (Ids.validated, EdgeBuilder): older versions read a dot in a key as nesting.
    private fun linesMap(): Map<String, Any?> {
        val lines = LinkedHashMap<String, Any?>()
        for ((id, line) in lineMap) {
            val entry = LinkedHashMap<String, Any?>()
            entry["display"] = line.display
            entry["icon"] = line.icon
            entry["stations"] = line.stations.toList()
            line.speed?.let { entry["speed"] = it }
            lines[id] = entry
        }
        return lines
    }

    private fun stationsMap(): Map<String, Any?> {
        val stations = LinkedHashMap<String, Any?>()
        for ((id, station) in stationMap) {
            val entry = LinkedHashMap<String, Any?>()
            entry["display"] = station.display
            entry["world"] = station.world
            entry["icon"] = station.icon
            if (station.terminal) entry["terminal"] = true
            entry["platforms"] = station.platforms.map { platformMap(it) }
            stations[id] = entry
        }
        for ((id, entry) in unloadedStations) stations[id] = entry.values
        return stations
    }

    private fun junctionsMap(): Map<String, Any?> {
        val junctions = LinkedHashMap<String, Any?>()
        for ((id, junction) in junctionMap) {
            val entry = LinkedHashMap<String, Any?>()
            entry["world"] = junction.world
            entry["x"] = junction.location.x
            entry["y"] = junction.location.y
            entry["z"] = junction.location.z
            entry["kind"] = junction.kind.name.lowercase()
            if (!junction.turns) entry["turns"] = false
            junction.speed?.let { entry["speed"] = it }
            junctions[id] = entry
        }
        for ((id, entry) in unloadedJunctions) junctions[id] = entry.values
        return junctions
    }

    private fun edgesMap(): Map<String, Any?> {
        val edges = LinkedHashMap<String, Any?>()
        for ((id, edge) in edgeMap) {
            val entry = LinkedHashMap<String, Any?>()
            entry["from-node"] = edge.fromNode
            entry["to-node"] = edge.toNode
            edge.cruiseSpeed?.let { entry["cruise-speed"] = it }
            if (!edge.auto) entry["auto"] = false
            entry["points"] = edge.points.map { listOf(it.x, it.y, it.z) }
            edge.switches?.let { entry["switches"] = it.toList() }
            edge.startSwitchSpeed?.let { entry["start-switch-speed"] = it }
            edge.endSwitchSpeed?.let { entry["end-switch-speed"] = it }
            edges[id] = entry
        }
        for ((id, entry) in unloadedEdges) {
            if (id !in edgeMap) edges[id] = entry.values
        }
        return edges
    }

    /** Waits for pending writes. Call on shutdown; saves after this are written on the calling thread. */
    fun close() {
        writer.close()
    }

    /**
     * Returns the world name of the station or junction that the graph node id belongs to.
     * Null when the node is unknown or its world is not loaded.
     */
    private fun loadedWorldOf(node: String): String? {
        val junction = Nodes.parseJunction(node)
        val world =
            if (junction != null) {
                junctionMap[junction.id]?.world
            } else {
                Nodes.parse(node)?.let { stationMap[it.stationId] }?.world
            }
        return world?.takeIf(worldLoaded)
    }

    private fun platformMap(platform: Platform): Map<String, Any?> {
        val map = LinkedHashMap<String, Any?>()
        map["id"] = platform.id
        platform.display?.let { map["display"] = it }
        map["icon"] = platform.icon
        platform.board?.let { map["board"] = listOf(it.x, it.y, it.z) }
        platform.begin?.let { map["begin"] = listOf(it.x, it.y, it.z) }
        platform.outbound?.let { map["out"] = listOf(it.x, it.y, it.z) }
        platform.inbound?.let { map["in"] = listOf(it.x, it.y, it.z) }
        platform.eject?.let { map["eject"] = listOf(it.x, it.y, it.z) }
        platform.land?.let { map["land"] = listOf(it.x, it.y, it.z) }
        platform.heading?.let { map["heading"] = it.name }
        return map
    }

    private fun readPlatform(
        world: String,
        map: Map<*, *>,
    ): Platform =
        Platform(
            id = (map["id"] as? String).orEmpty(),
            display = map["display"] as? String,
            icon = icon(map["icon"] as? String) ?: "OAK_SIGN",
            board = readPoint(world, map["board"]),
            begin = readPoint(world, map["begin"]),
            outbound = readPoint(world, map["out"]),
            inbound = readPoint(world, map["in"]),
            eject = readPoint(world, map["eject"]),
            land = readPoint(world, map["land"]),
            heading = readHeading(map["heading"]),
        )

    private fun readHeading(raw: Any?): Cardinal? = (raw as? String)?.let(Cardinal::of)

    private fun readPoint(
        world: String,
        raw: Any?,
    ): Point? {
        val list = raw as? List<*>
        if (list == null || list.size < COORDINATES) return null
        return runCatching {
            Point(world, number(list[0]), number(list[1]), number(list[2]))
        }.getOrNull()
    }

    private fun readPoints(
        world: String,
        raw: Any?,
    ): List<Point> {
        val list = raw as? List<*> ?: return emptyList()
        return list.mapNotNull { readPoint(world, it) }
    }

    private fun number(value: Any?): Double =
        when (value) {
            is Number -> value.toDouble()
            else -> value.toString().toDouble()
        }

    companion object {
        const val DEFAULT_LINE_ID = "default"

        private const val SCHEMA_VERSION = 1

        /** A stored location is a list of x, y and z. */
        private const val COORDINATES = 3
    }
}
