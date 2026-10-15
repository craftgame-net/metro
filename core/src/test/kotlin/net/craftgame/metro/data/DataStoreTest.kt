package net.craftgame.metro.data

import net.craftgame.metro.geo.Cardinal
import net.craftgame.metro.geo.Point
import net.craftgame.metro.recordingLogger
import net.craftgame.metro.testStore
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DataStoreTest {
    @TempDir
    lateinit var dir: File

    private val file get() = File(dir, "data.yml")

    private fun store(): DataStore = testStore(dir)

    /** A store that has read the file. */
    private fun loaded(warnings: MutableList<String> = ArrayList()): DataStore {
        val store = testStore(dir, recordingLogger(warnings))
        store.load()
        return store
    }

    private fun write(text: String) = file.writeText(text.trimIndent())

    private fun saved(): YamlSection = YamlSection.read(file)

    /** Returns the value at a dotted [path], or null when any part is missing. */
    private fun YamlSection.at(path: String): Any? {
        val keys = path.split('.')
        val parent = keys.dropLast(1).fold<String, YamlSection?>(this) { section, key -> section?.section(key) }
        return parent?.get(keys.last())
    }

    private fun at(
        x: Double,
        z: Double = 0.0,
    ) = Point("world", x, 64.0, z)

    private fun station(id: String): Station = Station(id = id, display = id, world = "world")

    private fun edge(
        id: String,
        from: String,
        to: String,
    ): Edge = Edge(id, from, to, 0.5, mutableListOf(at(0.0)))

    @Test
    fun everyFieldSurvivesARoundTrip() {
        val store = store()
        store.createLine("red", "Red", "REDSTONE_BLOCK")!!.speed = 0.9
        val central = Station("central", "Central", "world", icon = "STONE", terminal = true)
        central.addPlatform(
            Platform(
                display = "North",
                icon = "OAK_DOOR",
                board = at(1.0),
                begin = at(2.0),
                outbound = at(3.0),
                inbound = at(4.0),
                eject = at(5.0),
                land = at(6.0),
                heading = Cardinal.SOUTH,
            ),
        )
        central.addPlatform(Platform(id = "east"))
        store.addStation("red", central)
        store.putJunction(Junction("j", "world", at(9.0, 9.0), Junction.Kind.CROSS, turns = false, speed = 0.4))
        val points = mutableListOf(at(3.0), at(3.0, 9.0), at(9.0, 9.0))
        store.putEdge(Edge("e", "central:0:out", "junction.j.x", 0.7, points, false, listOf(0, 2), 0.3, 0.25))
        assertTrue(store.save())
        store.close()

        val loaded = loaded()

        assertEquals(store.lines, loaded.lines)
        assertEquals(store.stations, loaded.stations)
        assertEquals(store.junctions, loaded.junctions)
        assertEquals(store.edges, loaded.edges)
        assertEquals("red", loaded.lineIdOf("central"))
    }

    @Test
    fun unsetValuesAreLeftOutOfTheFile() {
        val store = store()
        store.createLine("blue", "Blue", "COMPASS")
        store.addStation("blue", station("a"))
        store.putJunction(Junction("j", "world", at(0.0), Junction.Kind.SPLIT))
        store.putEdge(edge("plain", "a:0:out", "junction.j.x"))
        store.save()
        store.close()

        val yaml = saved()
        assertNull(yaml.at("lines.blue.speed"))
        assertNull(yaml.at("stations.a.terminal"))
        assertNull(yaml.at("junctions.j.turns"))
        assertNull(yaml.at("junctions.j.speed"))
        assertNull(yaml.at("edges.plain.auto"))
        assertNull(yaml.at("edges.plain.switches"))
        assertNull(yaml.at("edges.plain.start-switch-speed"))
        assertNull(yaml.at("edges.plain.end-switch-speed"))
        val reloaded = loaded()
        assertNull(reloaded.lines.getValue("blue").speed)
        assertNull(reloaded.edges.getValue("plain").switches)
        assertTrue(reloaded.edges.getValue("plain").auto)
    }

    @Test
    fun rapidSavesLeaveLatestDataOnDisk() {
        val store = store()
        val line = store.createLine("red", "Red 0", "COMPASS")!!
        repeat(50) { i ->
            line.display = "Red $i"
            store.save()
        }
        store.close()

        assertEquals("Red 49", saved().at("lines.red.display"))
        assertFalse(File(dir, "data.yml.tmp").exists())
    }

    @Test
    fun saveAfterCloseWritesSynchronously() {
        val store = store()
        store.createLine("red", "Red", "COMPASS")
        store.close()

        store.save()

        assertEquals("Red", saved().at("lines.red.display"))
    }

    @Test
    fun loadReadsWhatWasJustSaved() {
        val store = store()
        store.createLine("red", "Red", "COMPASS")
        repeat(20) { i ->
            // load() replaces the line objects: look the line up again each time.
            store.lines.getValue("red").display = "Red $i"
            store.save()
            store.load()
            assertEquals("Red $i", store.lines["red"]?.display)
        }
        store.close()
    }

    @Test
    fun everyChangeBumpsTheVersion() {
        val store = store()
        val changes =
            listOf<() -> Unit>(
                { store.load() },
                { store.createLine("red", "Red", "COMPASS") },
                { store.createLine("blue", "Blue", "COMPASS") },
                { store.addStation("red", station("a")) },
                { store.moveStation("a", "blue") },
                { store.putJunction(Junction("j", "world", at(0.0), Junction.Kind.SPLIT)) },
                { store.putEdge(edge("e", "a:0:out", "a:0:in")) },
                { store.replaceAutoEdges(emptyList()) },
                { store.removeStation("a") },
                { store.deleteLine("red") },
                { store.save() },
            )
        for ((index, change) in changes.withIndex()) {
            val before = store.version
            change()
            assertTrue(store.version > before, "change $index")
        }
        // Wait for the background write before @TempDir is deleted.
        store.close()
    }

    @Test
    fun aMissingFileLoadsEmpty() {
        val store = loaded()

        assertTrue(store.lines.isEmpty())
        assertFalse(store.loadFailed)
    }

    @Test
    fun entriesInAnUnloadedWorldSurviveASave() {
        write(
            """
            version: 1
            lines:
              red:
                display: Red
                stations: [central, faraway]
            stations:
              central:
                display: Central
                world: world
                platforms: [{id: '0', board: [1.0, 64.0, 1.0]}]
              faraway:
                display: Far Away
                world: nether
                icon: STONE
                platforms: [{id: '0', board: [5.0, 64.0, 5.0], heading: EAST}]
            junctions:
              fork:
                world: nether
                x: 1.0
                y: 64.0
                z: 1.0
                kind: split
            edges:
              far:
                from-node: faraway:0:out
                to-node: junction.fork.x
                points: [[1.0, 64.0, 1.0], [2.0, 64.0, 1.0]]
              toFork:
                from-node: central:0:out
                to-node: junction.fork.x
                points: [[1.0, 64.0, 1.0], [2.0, 64.0, 1.0]]
            """,
        )
        val store = loaded()
        assertNull(store.stations["faraway"])
        assertNull(store.junctions["fork"])
        assertTrue(store.edges.isEmpty())
        assertNotNull(store.stations["central"])
        assertEquals(listOf("central", "faraway"), store.lines["red"]?.stations?.toList())
        assertEquals(listOf(store.stations.getValue("central")), store.stationsOf("red"))

        store.save()
        store.close()

        val yaml = saved()
        assertEquals("Far Away", yaml.at("stations.faraway.display"))
        assertEquals("nether", yaml.at("stations.faraway.world"))
        assertEquals("STONE", yaml.at("stations.faraway.icon"))
        val platform = (yaml.at("stations.faraway.platforms") as List<*>).single() as Map<*, *>
        assertEquals("EAST", platform["heading"])
        assertEquals("split", yaml.at("junctions.fork.kind"))
        assertEquals("faraway:0:out", yaml.at("edges.far.from-node"))
        assertEquals(2, (yaml.at("edges.far.points") as List<*>).size)
        // An edge to a junction in an unloaded world is kept even when its station is loaded.
        assertEquals("central:0:out", yaml.at("edges.toFork.from-node"))
        assertEquals(listOf("central", "faraway"), yaml.at("lines.red.stations"))
    }

    @Test
    fun anEntryAddedAgainReplacesTheKeptCopy() {
        write(
            """
            lines:
              red: {display: Red, stations: [faraway]}
            stations:
              faraway: {display: Far Away, world: nether, platforms: []}
            junctions:
              fork: {world: nether, x: 1.0, y: 64.0, z: 1.0, kind: split}
            """,
        )
        val store = loaded()

        store.addStation("red", Station("faraway", "Moved Here", "world"))
        store.putJunction(Junction("fork", "world", at(3.0), Junction.Kind.JOIN))
        store.save()
        store.close()

        val yaml = saved()
        assertEquals("Moved Here", yaml.at("stations.faraway.display"))
        assertEquals("world", yaml.at("stations.faraway.world"))
        assertEquals("join", yaml.at("junctions.fork.kind"))
        assertEquals(listOf("faraway"), yaml.at("lines.red.stations"))
    }

    @Test
    fun deletingALineDropsItsKeptStations() {
        write(
            """
            lines:
              red: {display: Red, stations: [faraway]}
            stations:
              faraway: {display: Far Away, world: nether, platforms: []}
            """,
        )
        val store = loaded()

        assertEquals(1, store.deleteLine("red"))
        store.save()
        store.close()

        assertNull(saved().at("stations.faraway"))
    }

    @Test
    fun aBrokenFileLoadsNothingIsCopiedAsideAndIsNeverOverwritten() {
        val broken = "lines:\n  red: [unclosed\n"
        file.writeText(broken)
        val store = loaded()

        assertTrue(store.loadFailed)
        assertTrue(store.lines.isEmpty())
        val copies = dir.listFiles { candidate -> candidate.name.startsWith("data.yml.broken-") }.orEmpty()
        assertEquals(broken, copies.single().readText())

        store.createLine("blue", "Blue", "COMPASS")
        assertFalse(store.save())
        store.close()
        assertEquals(broken, file.readText())
    }

    @Test
    fun aGoodLoadAfterABrokenOneAllowsSavingAgain() {
        file.writeText("lines: [unclosed\n")
        val store = loaded()
        assertTrue(store.loadFailed)

        write(
            """
            lines:
              red:
                display: Red
            """,
        )
        store.load()
        assertFalse(store.loadFailed)
        store.lines.getValue("red").display = "Red 2"
        store.save()
        store.close()
        assertEquals("Red 2", saved().at("lines.red.display"))
    }

    @Test
    fun aStationWhoseLastPlatformWasDeletedReloadsWithNone() {
        val store = store()
        store.createLine("red", "Red", "COMPASS")
        val central = station("central")
        central.addPlatform(Platform(board = at(1.0)))
        store.addStation("red", central)
        store.removePlatform("central", central.platforms.single().id)
        store.save()
        store.load()

        assertEquals(emptyList(), store.stations["central"]?.platforms?.toList())
        store.close()
    }

    @Test
    fun anchorsSavedWithoutAPlatformsListStillLoad() {
        write(
            """
            stations:
              central:
                world: world
                board: [1.0, 64.0, 1.0]
                out: [3.0, 64.0, 1.0]
            """,
        )
        val platform = loaded().stations["central"]?.platforms?.single()

        assertEquals("0", platform?.id)
        assertEquals(Point("world", 1.0, 64.0, 1.0), platform?.board)
        assertEquals(Point("world", 3.0, 64.0, 1.0), platform?.outbound)
    }

    @Test
    fun badPointsAndHeadingsAreDropped() {
        write(
            """
            stations:
              a:
                world: world
                platforms:
                  - {id: '0', board: [1.0, 64.0], begin: [x, 64.0, 1.0], out: ['3', '64', '1'], heading: up}
            """,
        )
        val platform =
            loaded()
                .stations
                .getValue("a")
                .platforms
                .single()

        assertNull(platform.board)
        assertNull(platform.begin)
        assertEquals(Point("world", 3.0, 64.0, 1.0), platform.outbound)
        assertNull(platform.heading)
    }

    @Test
    fun iconsThatCannotBeShownFallBackToTheDefaults() {
        write(
            """
            lines:
              red:
                icon: AIR
            stations:
              central:
                world: world
                icon: AIR
                platforms: [{id: '0', icon: AIR}]
            """,
        )
        val store = loaded()

        assertEquals("COMPASS", store.lines["red"]?.icon)
        assertEquals("COMPASS", store.stations["central"]?.icon)
        assertEquals(
            "OAK_SIGN",
            store.stations["central"]
                ?.platforms
                ?.single()
                ?.icon,
        )
    }

    @Test
    fun invalidStoredSpeedsLoadAsUnsetWithOneWarning() {
        write(
            """
            lines:
              red: {display: Red, speed: fast, stations: [a]}
              blue: {display: Blue, speed: 0, stations: []}
              green: {display: Green, speed: 0.8, stations: []}
            stations:
              a: {world: world, platforms: [{id: '0'}]}
            junctions:
              j1: {world: world, x: 0.0, y: 64.0, z: 0.0, kind: split, speed: -1}
              j2: {world: world, x: 9.0, y: 64.0, z: 0.0, kind: split, speed: '0.3'}
            edges:
              e1:
                from-node: a:0:out
                to-node: a:0:in
                cruise-speed: .inf
                start-switch-speed: 0.01
                end-switch-speed: 0.2
                points: [[0.0, 64.0, 0.0], [5.0, 64.0, 0.0]]
            """,
        )
        val warnings = ArrayList<String>()
        val store = loaded(warnings)

        assertNull(store.lines["red"]?.speed)
        assertNull(store.lines["blue"]?.speed)
        assertEquals(0.8, store.lines["green"]?.speed)
        assertNull(store.junctions["j1"]?.speed)
        assertEquals(0.3, store.junctions["j2"]?.speed)
        val edge = assertNotNull(store.edges["e1"])
        assertNull(edge.cruiseSpeed)
        assertNull(edge.startSwitchSpeed)
        assertEquals(0.2, edge.endSwitchSpeed)
        assertEquals(1, warnings.count { "speed" in it })
    }

    @Test
    fun duplicatePlatformIdsGetAFreshIdOnLoad() {
        write(
            """
            lines:
              red: {display: Red, stations: [a]}
            stations:
              a:
                world: world
                platforms:
                  - {id: '0', board: [1.0, 64.0, 1.0]}
                  - {id: '0', board: [2.0, 64.0, 2.0]}
            """,
        )
        val warnings = ArrayList<String>()
        val store = loaded(warnings)

        val platforms = store.stations.getValue("a").platforms
        assertEquals(listOf("0", "1"), platforms.map { it.id })
        assertEquals(
            2.0,
            store.stations
                .getValue("a")
                .platform("1")
                ?.board
                ?.x,
        )
        assertEquals(1, warnings.count { "platform" in it })
    }

    @Test
    fun missingOptionalFieldsLoadAsUnset() {
        write(
            """
            version: 1
            lines:
              red:
                display: Red
                stations: [a]
            stations:
              a: {world: world, platforms: [{id: '0'}]}
            edges:
              e:
                from-node: a:0:out
                to-node: a:0:in
                cruise-speed: 0.5
                points: [[0.0, 64.0, 0.0], [1.0, 64.0, 0.0], [2.0, 64.0, 0.0]]
            """,
        )
        val store = loaded()

        assertNull(store.lines.getValue("red").speed)
        val edge = store.edges.getValue("e")
        // Dense points are valid as they are.
        assertEquals(3, edge.points.size)
        assertNull(edge.switches)
        assertNull(edge.startSwitchSpeed)
        assertNull(edge.endSwitchSpeed)

        store.save()
        store.close()
        assertEquals(1, saved().at("version"))
    }

    @Test
    fun stationsWithoutALineJoinTheDefaultLineUnderTheGivenName() {
        write(
            """
            stations:
              a: {world: world, platforms: []}
            """,
        )
        val store = testStore(dir, defaultLineName = { "Standard" })
        store.load()

        assertEquals("Standard", store.lines[DataStore.DEFAULT_LINE_ID]?.display)
        assertEquals(DataStore.DEFAULT_LINE_ID, store.lineIdOf("a"))
    }

    @Test
    fun aStationListedTwiceOrUnknownKeepsOnlyItsFirstPlace() {
        write(
            """
            lines:
              red: {display: Red, stations: [a, ghost, a]}
              blue: {display: Blue, stations: [a, b]}
            stations:
              a: {world: world, platforms: []}
              b: {world: world, platforms: []}
            """,
        )
        val store = loaded()

        assertEquals(listOf("a"), store.lines.getValue("red").stations)
        assertEquals(listOf("b"), store.lines.getValue("blue").stations)
        assertEquals("red", store.lineIdOf("a"))
    }

    @Test
    fun junctionsOfAnUnknownKindAndEdgesWithoutEndsAreSkipped() {
        write(
            """
            stations:
              a: {world: world, platforms: []}
            junctions:
              odd: {world: world, x: 0.0, y: 64.0, z: 0.0, kind: ramp}
            edges:
              toOdd: {from-node: a:0:out, to-node: junction.odd.x, points: [[0.0, 64.0, 0.0]]}
              nowhere: {from-node: x:0:out, to-node: y:0:in, points: [[0.0, 64.0, 0.0]]}
              noEnd: {from-node: a:0:out, points: [[0.0, 64.0, 0.0]]}
            """,
        )
        val warnings = ArrayList<String>()
        val store = loaded(warnings)

        assertTrue(store.junctions.isEmpty())
        // An edge needs only one known end to load: the other may be built later.
        assertEquals(listOf("toOdd"), store.edges.keys.toList())
        assertEquals(1, warnings.count { "unknown kind 'ramp'" in it })
    }
}
