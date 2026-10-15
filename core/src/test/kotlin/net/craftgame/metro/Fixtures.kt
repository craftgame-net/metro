package net.craftgame.metro

import net.craftgame.metro.config.Speeds
import net.craftgame.metro.data.DataStore
import net.craftgame.metro.data.Edge
import net.craftgame.metro.data.Junction
import net.craftgame.metro.data.Line
import net.craftgame.metro.data.Network
import net.craftgame.metro.data.Platform
import net.craftgame.metro.data.Station
import net.craftgame.metro.debug.DebugHub
import net.craftgame.metro.debug.DebugLog
import net.craftgame.metro.debug.DebugSink
import net.craftgame.metro.geo.Point
import net.craftgame.metro.rail.RailWorlds
import net.craftgame.metro.route.EdgeBuilder
import net.craftgame.metro.route.IssueTarget
import net.craftgame.metro.route.RebuildReport
import net.craftgame.metro.route.Router
import net.craftgame.metro.route.WarningText
import java.io.File
import java.util.UUID
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

/** The world of every test fixture. */
const val WORLD = "world"

/** Returns the point at the corner of block x, y, z in [WORLD]. */
fun at(
    x: Int,
    y: Int = 0,
    z: Int = 0,
): Point = Point(WORLD, x.toDouble(), y.toDouble(), z.toDouble())

/** Returns the point x, y, z in [WORLD]. */
fun point(
    x: Double,
    y: Double = 0.0,
    z: Double = 0.0,
): Point = Point(WORLD, x, y, z)

/** Returns the point on top of a flat rail at the centre of block x, y, z, as the path search gives it. */
fun railAt(
    x: Int,
    y: Int = 64,
    z: Int = 0,
): Point = Point(WORLD, x + 0.5, y + 0.0625, z + 0.5)

/** An edge whose length does not matter: one point, unless [points] says otherwise. Speed in blocks per tick. */
fun edge(
    id: String,
    from: String,
    to: String,
    speed: Double? = 0.5,
    points: List<Point> = listOf(at(0)),
    auto: Boolean = true,
): Edge = Edge(id, from, to, speed, points.toMutableList(), auto)

/** A straight edge of [length] blocks along x. Speed in blocks per tick. */
fun straightEdge(
    id: String,
    from: String,
    to: String,
    length: Double,
    speed: Double? = 0.5,
): Edge = edge(id, from, to, speed, listOf(point(0.0), point(length)))

/** A network that marks in-place changes the way a saving store does. */
class TestNetwork : Network() {
    fun save() = changed()
}

/** Creates line [id] with a station in [WORLD] for each of [stationIds]. */
fun Network.addLine(
    id: String,
    vararg stationIds: String,
): Line {
    val line = createLine(id, id, "COMPASS") ?: error("line $id exists")
    for (stationId in stationIds) addStation(id, Station(stationId, stationId, WORLD))
    return line
}

/** Adds one short edge per pair of nodes, with the id `from->to`. */
fun Network.connect(vararg links: Pair<String, String>) {
    for ((from, to) in links) putEdge(edge("$from->$to", from, to))
}

/** Warning texts as the key followed by the placeholder values, so tests can read what was filled in. */
val keyText = WarningText { key, placeholders -> "$key ${placeholders.joinToString { it.second }}".trim() }

/** Adds [platform] to station [stationId] on [line], creating both when missing. Returns the platform. */
fun Network.addPlatform(
    stationId: String,
    platform: Platform = Platform(),
    line: String = "red",
): Platform {
    if (line !in lines) addLine(line)
    val station = stations[stationId] ?: Station(stationId, stationId, WORLD).also { addStation(line, it) }
    return station.addPlatform(platform)
}

/** Adds a junction of [kind] at [location]. Speed in blocks per tick; null is unset. */
fun Network.addJunction(
    id: String,
    kind: Junction.Kind,
    location: Point,
    turns: Boolean = true,
    speed: Double? = null,
) = putJunction(Junction(id, location.world, location, kind, turns, speed))

/** Returns the node pairs of all edges. */
fun Network.links(): Set<Pair<String, String>> = edges.values.mapTo(LinkedHashSet()) { it.fromNode to it.toNode }

/** Returns the one edge from [from] to [to]. */
fun Network.edgeBetween(
    from: String,
    to: String,
): Edge = edges.values.single { it.fromNode == from && it.toNode == to }

/** Rebuilds the auto edges on [rails], with [keyText] texts and block targets in [WORLD]. */
fun Network.rebuild(
    rails: RailWorlds,
    speeds: Speeds = Speeds(),
    maxSearch: Int = 1000,
): RebuildReport {
    val input =
        EdgeBuilder.Input(this, Router(this, { speeds }), rails, speeds, maxSearch, keyText) { point ->
            IssueTarget.Block(point.world, point.blockX, point.blockY, point.blockZ)
        }
    return EdgeBuilder.rebuild(input)
}

/** Returns a logger that adds the text of every warning to [warnings] and prints nothing. */
fun recordingLogger(warnings: MutableList<String> = ArrayList()): Logger =
    Logger.getAnonymousLogger().apply {
        useParentHandlers = false
        addHandler(
            object : Handler() {
                override fun publish(record: LogRecord) {
                    if (record.level == Level.WARNING) warnings += record.message
                }

                override fun flush() = Unit

                override fun close() = Unit
            },
        )
    }

/**
 * A [DataStore] on `data.yml` in [dir]: only [WORLD] is loaded, and every item name but `AIR` is a usable icon,
 * written in capitals.
 */
fun testStore(
    dir: File,
    logger: Logger = recordingLogger(),
    defaultLineName: () -> String = { "Default" },
): DataStore =
    DataStore(
        File(dir, "data.yml"),
        logger,
        worldLoaded = { it == WORLD },
        iconName = { name -> name.uppercase().takeIf { it != "AIR" } },
        defaultIcon = { "COMPASS" },
        defaultLineName = defaultLineName,
    )

/** One debug line as a sink received it. */
data class DebugLine(
    val level: Int,
    val subject: UUID?,
    val text: String,
)

/** A [DebugSink] that keeps the lines and the dropped counts it gets. */
class RecordingSink : DebugSink {
    val lines = ArrayList<DebugLine>()
    val suppressed = ArrayList<Int>()

    override fun accept(
        level: Int,
        subject: UUID?,
        text: String,
    ) {
        lines += DebugLine(level, subject, text)
    }

    override fun suppressed(count: Int) {
        suppressed += count
    }
}

/** A [DebugLog] that records every line up to [level] and counts how often a message was built. */
class RecordingDebugLog(
    var level: Int = DebugHub.MAX_LEVEL,
) : DebugLog {
    val lines = ArrayList<DebugLine>()
    var built = 0

    override fun enabled(
        level: Int,
        subject: UUID?,
    ) = level <= this.level

    override fun log(
        level: Int,
        subject: UUID?,
        message: () -> String,
    ) {
        if (!enabled(level, subject)) return
        built++
        lines += DebugLine(level, subject, message())
    }
}
