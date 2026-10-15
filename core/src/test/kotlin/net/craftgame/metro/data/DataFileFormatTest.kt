package net.craftgame.metro.data

import net.craftgame.metro.geo.Cardinal
import net.craftgame.metro.geo.Point
import net.craftgame.metro.recordingLogger
import net.craftgame.metro.testStore
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Checks data.yml against files that the Bukkit-based DataStore read and wrote: the `*-loaded.txt` and `*-saved.yml`
 * resources are its results. Older plugin versions must read what this one writes, and the reverse.
 */
class DataFileFormatTest {
    @TempDir
    lateinit var dir: File

    private val file get() = File(dir, "data.yml")

    private fun resource(name: String): String {
        val url = checkNotNull(javaClass.getResource("/data/$name")) { "missing resource $name" }
        return url.readText()
    }

    /** Every loaded object, its line and the warnings, one per line. */
    private fun summary(
        store: DataStore,
        warnings: List<String>,
    ): String =
        buildString {
            appendLine("loadFailed=${store.loadFailed}")
            append("counts lines=${store.lines.size} stations=${store.stations.size} ")
            append("junctions=${store.junctions.size} edges=${store.edges.size} ")
            append("platforms=${store.stations.values.sumOf { it.platforms.size }} ")
            append("switchMarks=${store.edges.values.sumOf { it.switches?.size ?: 0 }} ")
            appendLine("points=${store.edges.values.sumOf { it.points.size }}")
            store.lines.values.forEach { appendLine("L $it") }
            store.stations.values.forEach { appendLine("S $it line=${store.lineIdOf(it.id)}") }
            store.junctions.values.forEach { appendLine("J $it") }
            store.edges.values.forEach { appendLine("E $it") }
            warnings.forEach { appendLine("W $it") }
        }

    @Test
    fun oddValuesLoadAsBefore() {
        file.writeText(resource("odd-input.yml"))
        val warnings = ArrayList<String>()
        val store = testStore(dir, recordingLogger(warnings))
        store.load()

        assertEquals(resource("odd-loaded.txt"), summary(store, warnings))

        store.save()
        store.close()
        assertEquals(resource("odd-saved.yml"), file.readText())
    }

    @Test
    fun aRichNetworkIsWrittenAsBefore() {
        val store = testStore(dir)
        richNetwork(store)
        store.save()
        store.close()

        assertEquals(resource("rich-saved.yml"), file.readText())
        val loaded = testStore(dir)
        loaded.load()
        assertEquals(store.lines, loaded.lines)
        assertEquals(store.stations, loaded.stations)
        assertEquals(store.junctions, loaded.junctions)
        assertEquals(store.edges, loaded.edges)
    }

    @Test
    fun aFileWrittenBeforeLoadsAndIsWrittenBackUnchanged() {
        file.writeText(resource("rich-saved.yml"))
        val store = testStore(dir)
        store.load()
        store.save()
        store.close()

        assertEquals(resource("rich-saved.yml"), file.readText())
    }

    private fun at(
        x: Double,
        z: Double = 0.0,
    ) = Point("world", x, 64.0625, z)

    /** Ids and names that YAML would read as other types unless quoted, and text longer than a line. */
    private fun richNetwork(store: DataStore) {
        store.createLine("red", "Red Line: \"quoted\" & 'single' # hash", "REDSTONE_BLOCK")!!.speed = 0.9
        store.createLine("yes", "Метростроителей", "GREEN_BANNER")
        store.createLine("0", "0", "COMPASS")!!.speed = 1.0
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
        central.addPlatform(Platform(id = "7", display = "true", board = Point("world", -1.0, -64.0, 1e7)))
        store.addStation("red", central)
        store.addStation("yes", Station("on", "null", "world"))
        store.addStation("yes", Station("123", "~", "world", icon = "OAK_SIGN"))
        val long = "a very long display name that goes well past the eighty column width of the yaml dumper"
        store.addStation("0", Station("no", "$long, to see folding", "world"))
        store.putJunction(Junction("j", "world", at(9.0, 9.0), Junction.Kind.CROSS, turns = false, speed = 0.4))
        store.putJunction(Junction("off", "world", at(19.0, 9.0), Junction.Kind.SPLIT))
        store.putJunction(Junction("2", "world", at(29.0, 9.0), Junction.Kind.JOIN, speed = 1.5))
        val points = mutableListOf(at(3.0), at(3.0, 9.0), at(9.0, 9.0))
        store.putEdge(Edge("e", "central:0:out", "junction.j.x", 0.7, points, false, listOf(0, 2), 0.3, 0.25))
        val pocket = mutableListOf(at(1.0))
        store.putEdge(Edge("central_east_out->on_0_in", "central:east:out", "on:0:in", null, pocket, true, emptyList()))
        store.putEdge(Edge("x", "junction.off.z", "junction.2.x", 0.05, mutableListOf(), true, null, null, 1.5))
    }
}
