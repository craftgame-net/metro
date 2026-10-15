package net.craftgame.metro.data

import net.craftgame.metro.WORLD
import net.craftgame.metro.addLine
import net.craftgame.metro.at
import net.craftgame.metro.connect
import net.craftgame.metro.edge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NetworkTest {
    private fun station(id: String): Station = Station(id, id, WORLD)

    private fun junction(id: String) = Junction(id, WORLD, at(0, y = 64), Junction.Kind.SPLIT)

    @Test
    fun createLineRejectsDuplicateId() {
        val store = Network()
        assertNotNull(store.createLine("red", "Red", "REDSTONE_BLOCK"))
        assertNull(store.createLine("red", "Other", "COMPASS"))
        assertEquals("Red", store.lines.getValue("red").display)
    }

    @Test
    fun addStationLinksBothWays() {
        val store = Network()
        store.addLine("red")
        val central = station("central")
        store.addStation("red", central)

        assertEquals("red", store.lineIdOf("central"))
        assertEquals("red", store.lineOf("central")?.id)
        assertEquals(listOf("central"), store.lines.getValue("red").stations)
        assertEquals(listOf(central), store.stationsOf("red"))
    }

    @Test
    fun addStationToAnUnknownLineDoesNothing() {
        val store = Network()
        val before = store.version

        store.addStation("missing", station("central"))

        assertTrue(store.stations.isEmpty())
        assertNull(store.lineOf("central"))
        assertEquals(before, store.version)
    }

    @Test
    fun addingAStationTwiceKeepsOneEntry() {
        val store = Network()
        store.addLine("red", "central")

        store.addStation("red", station("central"))

        assertEquals(listOf("central"), store.lines.getValue("red").stations)
    }

    @Test
    fun stationsOfLeavesOutIdsWithoutAStation() {
        val store = Network()
        store.addLine("red", "a").stations.add("unknown")

        assertEquals(listOf("a"), store.stationsOf("red").map { it.id })
        assertEquals(emptyList(), store.stationsOf("missing"))
    }

    @Test
    fun addStationMovesItBetweenLines() {
        val store = Network()
        store.addLine("red", "central")
        store.addLine("blue")
        store.addStation("blue", store.stations.getValue("central"))

        assertEquals("blue", store.lineIdOf("central"))
        assertTrue(
            store.lines
                .getValue("red")
                .stations
                .isEmpty(),
        )
        assertEquals(listOf("central"), store.lines.getValue("blue").stations)
    }

    @Test
    fun moveStationAppendsToTheNewLineOnly() {
        val store = Network()
        store.addLine("red", "central", "park")
        store.addLine("blue", "harbour")

        assertEquals(Network.MoveResult.MOVED, store.moveStation("central", "blue"))
        assertEquals("blue", store.lineIdOf("central"))
        assertEquals(listOf("park"), store.lines.getValue("red").stations)
        assertEquals(listOf("harbour", "central"), store.lines.getValue("blue").stations)

        val version = store.version
        assertEquals(Network.MoveResult.ALREADY_ON_LINE, store.moveStation("central", "blue"))
        assertEquals(Network.MoveResult.NO_LINE, store.moveStation("central", "missing"))
        assertEquals(Network.MoveResult.NO_STATION, store.moveStation("missing", "red"))
        assertEquals(listOf("harbour", "central"), store.lines.getValue("blue").stations)
        assertEquals(version, store.version)
    }

    @Test
    fun removeStationDropsItsEdgesOnly() {
        val store = Network()
        store.addLine("red", "central")
        store.connect("central:0:out" to "north:0:in", "north:0:out" to "third:0:in", "junction.j.x" to "central:1:in")

        assertEquals("central", store.removeStation("central")?.id)

        assertNull(store.stations["central"])
        assertNull(store.lineOf("central"))
        assertTrue(
            store.lines
                .getValue("red")
                .stations
                .isEmpty(),
        )
        assertEquals(listOf("north:0:out->third:0:in"), store.edges.keys.toList())
        assertNull(store.removeStation("central"))
    }

    @Test
    fun deleteLineCascadesToStationsAndEdges() {
        val store = Network()
        store.addLine("red", "central", "park")
        store.addLine("blue", "harbour")
        store.connect("central:0:out" to "park:0:in", "park:0:out" to "harbour:0:in", "harbour:0:out" to "x:0:in")

        assertEquals(2, store.deleteLine("red"))

        assertNull(store.lines["red"])
        assertEquals(listOf("harbour"), store.stations.keys.toList())
        assertNull(store.lineIdOf("central"))
        assertEquals(listOf("harbour:0:out->x:0:in"), store.edges.keys.toList())
        assertEquals(0, store.deleteLine("red"))
    }

    @Test
    fun removePlatformDropsOnlyThatPlatformsEdges() {
        val store = Network()
        store.addLine("red", "a")
        val station = store.stations.getValue("a")
        station.addPlatform()
        station.addPlatform()
        store.connect("a:0:out" to "b:0:in", "b:0:out" to "a:1:in", "a:1:in" to "a:1:eject", "b:1:out" to "c:0:in")

        assertEquals("1", store.removePlatform("a", "1")?.id)

        assertEquals(listOf("0"), station.platforms.map { it.id })
        assertEquals(listOf("a:0:out->b:0:in", "b:1:out->c:0:in"), store.edges.keys.toList())
        assertNull(store.removePlatform("a", "1"))
        assertNull(store.removePlatform("missing", "0"))
    }

    @Test
    fun removeJunctionDropsItsEdgesOnly() {
        val store = Network()
        store.putJunction(junction("depot"))
        store.putJunction(junction("depot2"))
        store.connect(
            "junction.depot.x" to "north:0:in",
            "north:0:out" to "junction.depot.z",
            "north:1:out" to "junction.depot2.x",
        )

        assertEquals("depot", store.removeJunction("depot")?.id)

        assertEquals(listOf("depot2"), store.junctions.keys.toList())
        assertEquals(listOf("north:1:out->junction.depot2.x"), store.edges.keys.toList())
        assertNull(store.removeJunction("depot"))
    }

    @Test
    fun putJunctionAndPutEdgeReplaceByIdKeepingTheOrder() {
        val store = Network()
        store.putJunction(junction("a"))
        store.putJunction(junction("b"))
        store.putJunction(junction("a").copy(kind = Junction.Kind.JOIN))
        store.putEdge(edge("e1", "x", "y"))
        store.putEdge(edge("e2", "x", "z"))
        store.putEdge(edge("e1", "x", "w"))

        assertEquals(listOf("a", "b"), store.junctions.keys.toList())
        assertEquals(Junction.Kind.JOIN, store.junctions.getValue("a").kind)
        assertEquals(listOf("e1", "e2"), store.edges.keys.toList())
        assertEquals("w", store.edges.getValue("e1").toNode)
    }

    @Test
    fun replaceAutoEdgesKeepsHandAuthoredOnes() {
        val store = Network()
        store.putEdge(edge("auto1", "a", "b"))
        store.putEdge(edge("manual1", "c", "d", auto = false))

        store.replaceAutoEdges(listOf(edge("auto2", "e", "f")))

        assertEquals(setOf("manual1", "auto2"), store.edges.keys)
    }

    @Test
    fun everyMutatorThatChangesSomethingBumpsTheVersion() {
        val store = Network()
        val changes =
            listOf<() -> Unit>(
                { store.createLine("red", "Red", "COMPASS") },
                { store.createLine("blue", "Blue", "COMPASS") },
                { store.addStation("red", station("a").apply { addPlatform() }) },
                { store.moveStation("a", "blue") },
                { store.putJunction(junction("j")) },
                { store.putEdge(edge("e", "a:0:out", "a:0:in")) },
                { store.replaceAutoEdges(emptyList()) },
                { store.removePlatform("a", "0") },
                { store.removeJunction("j") },
                { store.removeStation("a") },
                { store.deleteLine("red") },
            )
        for ((index, change) in changes.withIndex()) {
            val before = store.version
            change()
            assertTrue(store.version > before, "change $index")
        }
    }
}
