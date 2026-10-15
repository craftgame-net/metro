package net.craftgame.metro.route

import net.craftgame.metro.TestNetwork
import net.craftgame.metro.addLine
import net.craftgame.metro.at
import net.craftgame.metro.config.Speeds
import net.craftgame.metro.connect
import net.craftgame.metro.data.Edge
import net.craftgame.metro.data.Network
import net.craftgame.metro.data.Platform
import net.craftgame.metro.data.Station
import net.craftgame.metro.edge
import net.craftgame.metro.straightEdge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RouterTest {
    private fun ids(route: List<Edge>?): List<String>? = route?.map { it.id }

    private fun Network.terminals(vararg ids: String) {
        for (id in ids) stations.getValue(id).terminal = true
    }

    /** One station `s` on line `red` whose platform 0 has [platform]'s anchors. */
    private fun anchoredStation(
        store: Network,
        platform: Platform = Platform(begin = at(0), outbound = at(1)),
    ): Station {
        store.addLine("red")
        val station = Station("s", "S", "world")
        station.addPlatform(platform)
        store.addStation("red", station)
        return station
    }

    /**
     * Three stations on one line: forward platforms (`0`) run `a -> b -> c`, return platforms (`1`) run
     * `c -> b -> a`, and each end loops onto the other platform. Pocket edges are authored directly, since there are
     * no anchors for the router's virtual edges.
     */
    private fun ringNetwork(): TestNetwork {
        val store = TestNetwork()
        store.addLine("red", "a", "b", "c")
        store.connect(
            "a:0:begin" to "a:0:out",
            "a:1:begin" to "a:1:out",
            "b:0:begin" to "b:0:out",
            "b:1:begin" to "b:1:out",
            "c:0:begin" to "c:0:out",
            "c:1:begin" to "c:1:out",
            "a:1:in" to "a:1:eject",
            "b:0:in" to "b:0:eject",
            "b:1:in" to "b:1:eject",
            "c:0:in" to "c:0:eject",
            "a:0:out" to "b:0:in",
            "b:0:in" to "b:0:out",
            "b:0:out" to "c:0:in",
            "c:1:out" to "b:1:in",
            "b:1:in" to "b:1:out",
            "b:1:out" to "a:1:in",
            "c:0:in" to "c:1:out",
            "a:1:in" to "a:0:out",
        )
        return store
    }

    /** Line red (`t` a possible terminal, `x`, `x2`) and line blue (`y`, `u`), linked through junction `j`. */
    private fun twoLineNetwork(): TestNetwork {
        val store = TestNetwork()
        store.addLine("red", "t", "x", "x2")
        store.addLine("blue", "y", "u")
        store.putEdge(straightEdge("toT", "x:0:out", "t:0:in", 3.0))
        store.putEdge(straightEdge("loop", "t:0:in", "t:1:out", 1.0))
        store.putEdge(straightEdge("through", "t:1:out", "t:1:in", 1.0))
        store.putEdge(straightEdge("leave", "t:1:in", "junction.j.x", 2.0))
        store.putEdge(straightEdge("toY", "junction.j.x", "y:0:in", 4.0))
        store.putEdge(straightEdge("toX2", "junction.j.x", "x2:0:in", 4.0))
        store.putEdge(straightEdge("slowToY", "x:0:out", "y:0:in", 9.0, speed = 0.2))
        store.putEdge(straightEdge("yToU", "y:0:in", "u:0:in", 5.0))
        store.putEdge(straightEdge("uLoop", "u:0:in", "u:1:out", 1.0))
        store.putEdge(straightEdge("uBack", "u:1:out", "y:1:in", 5.0))
        store.putEdge(straightEdge("yBack", "y:1:in", "junction.j.y", 2.0))
        store.putEdge(straightEdge("toX", "junction.j.y", "x:1:in", 4.0))
        store.putEdge(straightEdge("xTurn", "x:1:in", "x:0:out", 1.0))
        return store
    }

    /** Station `sc` whose platform 0 leads through a junction back onto platform 1, which runs on to `sb`. */
    private fun loopBackNetwork(): TestNetwork {
        val store = TestNetwork()
        store.addLine("red", "sc", "sb")
        store.connect(
            "sc:0:out" to "junction.cross.x",
            "junction.cross.x" to "sc:1:out",
            "sc:1:out" to "sc:1:in",
            "sc:1:in" to "sb:1:in",
        )
        return store
    }

    @Test
    fun followsExplicitEdges() {
        val store = TestNetwork()
        store.connect("a" to "b", "b" to "c")
        val router = Router(store)

        assertEquals(setOf("a", "b", "c"), router.reachableFrom("a"))
        assertEquals(listOf("a->b", "b->c"), ids(router.findRoute("a", "c")))
    }

    @Test
    fun aNewSlopeSpeedRebuildsTheCosts() {
        val store = TestNetwork()
        store.putEdge(straightEdge("flat", "a", "b", 14.0))
        val hill = listOf(at(0), at(2), at(8, y = 6), at(10, y = 6))
        store.putEdge(edge("hill", "a", "b", points = hill))
        var slopeSpeed = Slopes.OFF
        val router = Router(store, slopeSpeed = { slopeSpeed })

        assertEquals(listOf("hill"), ids(router.findRoute("a", "b")))
        slopeSpeed = 0.1
        assertEquals(listOf("flat"), ids(router.findRoute("a", "b")))
    }

    @Test
    fun theRouteToTheOriginItselfIsEmpty() {
        assertEquals(emptyList(), Router(TestNetwork()).findRoute("a", "a"))
    }

    @Test
    fun unreachableDestinationHasNoRoute() {
        val store = TestNetwork()
        store.connect("a" to "b")
        assertNull(Router(store).findRoute("a", "z"))
    }

    @Test
    fun synthesizesVirtualPocketEdgesFromPlatformAnchors() {
        val store = TestNetwork()
        anchoredStation(store, Platform(begin = at(0), outbound = at(1), inbound = at(5), eject = at(7)))
        val router = Router(store)

        assertEquals(listOf("virtual:s:0:begin->s:0:out"), ids(router.findRoute("s:0:begin", "s:0:out")))
        val inbound = router.findRoute("s:0:in", "s:0:eject")
        assertEquals(listOf("virtual:s:0:in->s:0:eject"), ids(inbound))
        assertNull(inbound?.single()?.cruiseSpeed)
        assertEquals(listOf(at(5), at(7)), inbound?.single()?.points?.toList())
    }

    /** A route needs `out`, see [Platform.boards]. */
    @Test
    fun aBeginWithoutOutLeadsNowhere() {
        val store = TestNetwork()
        val platform = Platform(board = at(0, y = 1), begin = at(0), inbound = at(5), eject = at(4))
        anchoredStation(store, platform)
        store.putEdge(edge("main", "s:0:in", "t:0:in"))

        assertEquals(setOf("s:0:begin"), Router(store).reachableFrom("s:0:begin"))
        assertFalse(platform.boards)
    }

    @Test
    fun explicitEdgeSuppressesTheVirtualOne() {
        val store = TestNetwork()
        anchoredStation(store)
        store.putEdge(edge("explicit", "s:0:begin", "s:0:out"))

        assertEquals(listOf("explicit"), ids(Router(store).findRoute("s:0:begin", "s:0:out")))
    }

    @Test
    fun virtualFallbackIsReportedToTheCallback() {
        val store = TestNetwork()
        anchoredStation(store)
        val reported = ArrayList<String>()

        Router(store) { from, to -> reported.add("$from->$to") }.reachableFrom("s:0:begin")

        assertEquals(listOf("s:0:begin->s:0:out"), reported)
    }

    @Test
    fun anchorPointReadsTheStoredAnchor() {
        val store = TestNetwork()
        anchoredStation(store)
        val router = Router(store)

        assertEquals(at(1), router.anchorPoint("s:0:out"))
        assertNull(router.anchorPoint("s:0:eject"))
        assertNull(router.anchorPoint("junction.j.x"))
        assertNull(router.anchorPoint("missing:0:out"))
    }

    @Test
    fun ridingToATerminalIsAllowed() {
        val store = ringNetwork()
        store.terminals("c")

        assertNotNull(Router(store).findRoute("b:0:begin", "c:0:eject"))
    }

    @Test
    fun transitThroughATerminalIsBlocked() {
        val store = ringNetwork()
        store.terminals("c")
        assertNull(Router(store).findRoute("b:0:begin", "b:1:eject"))

        store.stations.getValue("c").terminal = false
        store.save()
        assertNotNull(Router(store).findRoute("b:0:begin", "b:1:eject"))
    }

    @Test
    fun leavingFromTheOriginPlatformIsAllowed() {
        val store = ringNetwork()
        store.terminals("c")

        assertNotNull(Router(store).findRoute("c:1:begin", "b:1:eject"))
    }

    @Test
    fun boardingOnTheWrongPlatformOfATerminalStillBlocksTransit() {
        val store = ringNetwork()
        store.terminals("c")
        // c:0:out normally dead-ends; wire it into the turnaround to check that the terminal check follows the
        // platform edges crossed, not how c was entered.
        store.connect("c:0:out" to "c:1:out")

        assertNull(Router(store).findRoute("c:0:begin", "b:1:eject"))
    }

    @Test
    fun doesNotRouteBackThroughAnotherPlatformOfTheOriginStation() {
        val router = Router(loopBackNetwork())

        // Boarding on platform 0 cannot loop back through platform 1.
        assertNull(router.findRoute("sc:0:out", "sb:1:in"))
        // Boarding on the platform that runs toward sb can.
        assertNotNull(router.findRoute("sc:1:out", "sb:1:in"))
        // The other side of the same station is still reachable as the destination.
        assertNotNull(router.findRoute("sc:0:out", "sc:1:in"))
    }

    @Test
    fun aTerminalAllowsTransitToAnotherLineButBlocksItsOwnLoop() {
        val store = twoLineNetwork()
        store.terminals("t")
        val router = Router(store)

        // Passing through the terminal to reach another line is fine.
        assertEquals(listOf("toT", "loop", "through", "leave", "toY"), ids(router.findRoute("x:0:out", "y:0:in")))
        // Looping around the terminal's own line back to it is not.
        assertNull(router.findRoute("x:0:out", "x2:0:in"))
    }

    @Test
    fun aTerminalBlocksLeavingThroughAnotherPlatformsJunctionEdge() {
        val store = TestNetwork()
        store.addLine("red", "sd", "dest")
        store.terminals("sd")
        store.connect("sd:0:out" to "sd:1:in", "sd:1:in" to "junction.nw.x", "junction.nw.x" to "dest:0:in")
        val router = Router(store)

        // Boarding on platform 0 cannot leave through platform 1's junction edge.
        assertNull(router.findRoute("sd:0:out", "dest:0:in"))
        // Boarding on the departure platform itself can.
        assertNotNull(router.findRoute("sd:1:in", "dest:0:in"))
    }

    @Test
    fun reachableEjectsExcludesStationsPastATerminal() {
        val store = ringNetwork()
        store.terminals("c")

        val ejects = Router(store).reachableEjects("b:0:begin")

        assertFalse("b:1:eject" in ejects)
        assertTrue("c:0:eject" in ejects)
    }

    @Test
    fun terminalsActIndependently() {
        val store = ringNetwork()
        store.terminals("a", "c")
        val router = Router(store)

        assertNotNull(router.findRoute("c:1:begin", "a:1:eject"))
        assertNull(router.findRoute("c:1:begin", "b:0:eject"))
        assertNotNull(router.findRoute("b:0:begin", "c:0:eject"))
        assertNull(router.findRoute("b:0:begin", "b:1:eject"))

        store.terminals("b")
        store.save()
        assertNull(router.findRoute("a:0:begin", "c:0:eject"))
        assertNull(router.findRoute("c:1:begin", "a:1:eject"))
        assertNotNull(router.findRoute("b:0:begin", "c:0:eject"))
    }

    @Test
    fun routesThroughJunctionNodesAndKeepsTheTerminalRuleThere() {
        val store = TestNetwork()
        store.addLine("red", "a", "b", "c")
        store.connect(
            "b:0:out" to "junction.depot.x",
            "junction.depot.x" to "c:0:in",
            "c:0:in" to "c:0:out",
            "c:0:out" to "a:0:in",
        )
        val router = Router(store)
        assertEquals(
            listOf("b:0:out->junction.depot.x", "junction.depot.x->c:0:in", "c:0:in->c:0:out", "c:0:out->a:0:in"),
            ids(router.findRoute("b:0:out", "a:0:in")),
        )

        store.terminals("c")
        store.save()
        assertNull(router.findRoute("b:0:out", "a:0:in"))
    }

    @Test
    fun choosesTheQuickerRouteNotTheOneWithFewerEdges() {
        val store = TestNetwork()
        store.putEdge(straightEdge("direct", "a", "c", 10.0))
        store.putEdge(straightEdge("ab", "a", "b", 1.0))
        store.putEdge(straightEdge("bc", "b", "c", 1.0))

        assertEquals(listOf("ab", "bc"), ids(Router(store).findRoute("a", "c")))
    }

    @Test
    fun aShorterButSlowerRouteLosesToALongerFasterOne() {
        val store = TestNetwork()
        store.putEdge(straightEdge("slow", "a", "c", 10.0, speed = 0.1))
        store.putEdge(straightEdge("ab", "a", "b", 8.0))
        store.putEdge(straightEdge("bc", "b", "c", 7.0))
        val router = Router(store)

        assertEquals(listOf("ab", "bc"), ids(router.findRoute("a", "c")))
        assertEquals(10.0, router.routeLength(listOf(store.edges.getValue("slow"))), 1e-9)
    }

    @Test
    fun aTurnHeavyPathLosesToAStraightOneOfSimilarLength() {
        val store = TestNetwork()
        val stairs = (0..10).map { at((it + 1) / 2, z = it / 2) }
        store.putEdge(edge("stairs", "a", "c", 0.6, stairs))
        store.putEdge(straightEdge("straight", "a", "c", 11.0, speed = 0.6))

        assertEquals(listOf("straight"), ids(Router(store).findRoute("a", "c")))
    }

    @Test
    fun followsSpeedChangesWithoutANewRouter() {
        val store = TestNetwork()
        store.putEdge(straightEdge("direct", "a", "c", 10.0, speed = null))
        store.putEdge(straightEdge("ab", "a", "b", 8.0))
        store.putEdge(straightEdge("bc", "b", "c", 7.0))
        var speeds = Speeds(station = 0.3)
        val router = Router(store, { speeds })

        assertEquals(listOf("ab", "bc"), ids(router.findRoute("a", "c")))
        speeds = Speeds(station = 1.0)
        assertEquals(listOf("direct"), ids(router.findRoute("a", "c")))
    }

    @Test
    fun aReplacedEdgeIsCostedAgain() {
        val store = TestNetwork()
        store.putEdge(straightEdge("direct", "a", "c", 10.0, speed = 0.1))
        store.putEdge(straightEdge("ab", "a", "b", 8.0))
        store.putEdge(straightEdge("bc", "b", "c", 7.0))
        val router = Router(store)
        assertEquals(listOf("ab", "bc"), ids(router.findRoute("a", "c")))

        store.putEdge(straightEdge("direct", "a", "c", 10.0, speed = 1.0))

        assertEquals(listOf("direct"), ids(router.findRoute("a", "c")))
    }

    /** Every node of the edges plus every pocket node of every platform. */
    private fun nodes(store: Network): List<String> {
        val nodes = LinkedHashSet<String>()
        for (edge in store.edges.values) {
            nodes.add(edge.fromNode)
            nodes.add(edge.toNode)
        }
        for (station in store.stations.values) {
            for (platform in station.platforms) {
                for (role in listOf("begin", "out", "in", "eject")) nodes.add("${station.id}:${platform.id}:$role")
            }
        }
        return nodes.toList()
    }

    private fun assertSingleSourceMatches(store: Network) {
        val router = Router(store)
        val nodes = nodes(store)
        for (origin in nodes) {
            val routes = router.routesFrom(origin, nodes)
            for (target in nodes) {
                assertEquals(ids(router.findRoute(origin, target)), ids(routes[target]), "$origin -> $target")
            }
        }
    }

    @Test
    fun singleSourceRoutesMatchPerDestinationRoutes() {
        for (terminals in listOf(emptyList(), listOf("c"), listOf("a", "c"), listOf("a", "b", "c"))) {
            assertSingleSourceMatches(ringNetwork().apply { terminals(*terminals.toTypedArray()) })
        }
        for (terminals in listOf(emptyList(), listOf("t"), listOf("t", "u"), listOf("t", "x", "y"))) {
            assertSingleSourceMatches(twoLineNetwork().apply { terminals(*terminals.toTypedArray()) })
        }
        assertSingleSourceMatches(loopBackNetwork())
    }

    @Test
    fun singleSourceKeepsTheNoDoublingBackRule() {
        val routes = Router(loopBackNetwork()).routesFrom("sc:0:out", listOf("sb:1:in", "sc:1:in", "sc:1:out"))

        assertEquals(setOf("sc:1:in", "sc:1:out"), routes.keys)
    }

    @Test
    fun singleSourceAppliesTheTerminalRulePerDestination() {
        val store = twoLineNetwork()
        store.terminals("t")

        val routes = Router(store).routesFrom("x:0:out", listOf("y:0:in", "x2:0:in", "x:0:out"))

        assertEquals(listOf("toT", "loop", "through", "leave", "toY"), ids(routes["y:0:in"]))
        assertNull(routes["x2:0:in"])
        assertEquals(emptyList(), routes["x:0:out"])
    }

    @Test
    fun routesToEjectsMatchesReachableEjectsThenRoutesFrom() {
        for (terminals in listOf(emptyList(), listOf("c"), listOf("a", "c"))) {
            val store = ringNetwork().apply { terminals(*terminals.toTypedArray()) }
            val router = Router(store)
            for (origin in nodes(store)) {
                val station = origin.substringBefore(':')
                val ejects = router.reachableEjects(origin).filter { it.substringBefore(':') != station }
                val routes = router.routesFrom(origin, ejects)
                val expected = ejects.filter { it in routes }.map { it to ids(routes.getValue(it)) }
                val actual = router.routesToEjects(origin, station).map { (eject, route) -> eject to ids(route) }
                assertEquals(expected, actual, "from $origin with terminals $terminals")
            }
        }
    }

    @Test
    fun routesToEjectsListsEjectsInTheOrderTheyAreReached() {
        val router = Router(ringNetwork())

        // a:1:eject is never offered: reaching it would pass through another platform of the origin station.
        val all = router.routesToEjects("a:0:begin", skipStation = null)
        assertEquals(listOf("b:0:eject", "c:0:eject", "b:1:eject"), all.keys.toList())
        assertEquals(listOf("c:0:eject"), router.routesToEjects("a:0:begin", "b").keys.toList())
    }

    @Test
    fun theGraphIsBuiltOnceUntilTheDataChanges() {
        val store = TestNetwork()
        anchoredStation(store)
        val reported = ArrayList<String>()
        val router = Router(store) { from, to -> reported.add("$from->$to") }

        repeat(3) { router.findRoute("s:0:begin", "s:0:out") }
        router.reachableFrom("s:0:begin")
        router.routesFrom("s:0:begin", listOf("s:0:out"))
        assertEquals(1, reported.size)

        store.save()
        router.findRoute("s:0:begin", "s:0:out")
        assertEquals(2, reported.size)
    }

    @Test
    fun anAnchorMovedInPlaceCountsOnceSaved() {
        val store = TestNetwork()
        val station = anchoredStation(store)
        val router = Router(store)
        assertEquals(1, router.findRoute("s:0:begin", "s:0:out")?.size)

        station.platforms.first().outbound = null
        store.save()

        assertNull(router.findRoute("s:0:begin", "s:0:out"))
    }

    @Test
    fun aSpeedChangeRebuildsTheGraph() {
        val store = TestNetwork()
        anchoredStation(store)
        var speeds = Speeds(station = 0.3)
        var builds = 0
        val router = Router(store, { speeds }) { _, _ -> builds++ }

        router.findRoute("s:0:begin", "s:0:out")
        router.findRoute("s:0:begin", "s:0:out")
        speeds = Speeds(station = 0.4)
        router.findRoute("s:0:begin", "s:0:out")

        assertEquals(2, builds)
    }

    @Test
    fun turningReportingOnRebuildsTheGraphSoVirtualEdgesAreReported() {
        val store = TestNetwork()
        anchoredStation(store)
        var reporting = false
        val reported = ArrayList<String>()
        val router =
            Router(store, { Speeds() }, { reporting }) { from, to -> if (reporting) reported.add("$from->$to") }

        router.findRoute("s:0:begin", "s:0:out")
        assertTrue(reported.isEmpty())
        reporting = true
        router.findRoute("s:0:begin", "s:0:out")
        assertEquals(listOf("s:0:begin->s:0:out"), reported)
    }
}
