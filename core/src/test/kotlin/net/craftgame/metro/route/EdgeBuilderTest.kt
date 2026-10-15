package net.craftgame.metro.route

import net.craftgame.metro.TestNetwork
import net.craftgame.metro.WORLD
import net.craftgame.metro.addPlatform
import net.craftgame.metro.at
import net.craftgame.metro.config.Speeds
import net.craftgame.metro.data.Platform
import net.craftgame.metro.data.Station
import net.craftgame.metro.edge
import net.craftgame.metro.edgeBetween
import net.craftgame.metro.geo.Cardinal
import net.craftgame.metro.geo.Point
import net.craftgame.metro.links
import net.craftgame.metro.rail.FakeRails
import net.craftgame.metro.railAt
import net.craftgame.metro.rebuild
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class EdgeBuilderTest {
    private val button = at(0, y = 70)

    /** A straight track from x = 0 to 40: `a` boards at x = 2 and leaves at 4, `b` arrives at 30 and stops at 33. */
    private fun straightLine(): Pair<TestNetwork, FakeRails> {
        val network = TestNetwork()
        network.addPlatform("a", Platform(board = button, begin = railAt(2), outbound = railAt(4)))
        network.addPlatform("b", Platform(inbound = railAt(30), eject = railAt(33)))
        return network to FakeRails().alongX(0, 40)
    }

    @Test
    fun aStraightLineLinksThePocketsAndTheMainLine() {
        val (network, rails) = straightLine()

        val report = network.rebuild(rails)

        assertEquals(
            setOf("a:0:begin" to "a:0:out", "a:0:out" to "b:0:in", "b:0:in" to "b:0:eject"),
            network.links(),
        )
        assertEquals(RebuildReport(), report)
        assertSame(report, EdgeBuilder.lastReport)
        EdgeBuilder.clearReport()
        assertEquals(null, EdgeBuilder.lastReport)
    }

    @Test
    fun edgesAreStoredCompressedWithTheirSpeeds() {
        val (network, rails) = straightLine()
        val speeds = Speeds(cruise = 0.7, station = 0.25)

        network.rebuild(rails, speeds)

        val main = network.edgeBetween("a:0:out", "b:0:in")
        assertEquals("a_0_out->b_0_in", main.id)
        assertEquals(listOf(railAt(4), railAt(30)), main.points)
        assertEquals((4..30).map { railAt(it) }, Polyline.densify(main.points).points)
        assertEquals(0.7, main.cruiseSpeed)
        assertEquals(null, main.startSwitchSpeed)
        assertEquals(null, main.endSwitchSpeed)
        assertTrue(main.auto)
        assertEquals(0.25, network.edgeBetween("a:0:begin", "a:0:out").cruiseSpeed)
        assertEquals(0.25, network.edgeBetween("b:0:in", "b:0:eject").cruiseSpeed)
    }

    @Test
    fun aLineWithItsOwnSpeedRunsAtItBetweenItsStations() {
        val (network, rails) = straightLine()
        network.lines.getValue("red").speed = 0.9

        network.rebuild(rails, Speeds(maxCruise = 0.8))

        assertEquals(0.8, network.edgeBetween("a:0:out", "b:0:in").cruiseSpeed)
    }

    @Test
    fun anExplicitHeadingAgainstTheTrackLinksNothingAhead() {
        val (network, rails) = straightLine()
        network.stations
            .getValue("a")
            .platform("0")
            ?.heading = Cardinal.WEST

        val report = network.rebuild(rails)

        assertEquals(
            setOf("a:0:begin" to "a:0:out", "b:0:in" to "b:0:eject"),
            network.links(),
        )
        assertTrue(report.conflicts.isEmpty())
        assertEquals(listOf("a:0:begin"), report.boardingDeadEnds)
    }

    @Test
    fun opposingHeadingsOnOneTrackAreConflicts() {
        val network = TestNetwork()
        network.addPlatform("a", Platform(outbound = railAt(5), heading = Cardinal.EAST))
        network.addPlatform("b", Platform(outbound = railAt(20), heading = Cardinal.WEST))

        val report = network.rebuild(FakeRails().alongX(0, 30))

        assertEquals(setOf("b:0:out", "a:0:out"), report.conflicts)
    }

    @Test
    fun aPlatformNoDirectionReachesIsUnoriented() {
        val (network, rails) = straightLine()
        network.addPlatform("c", Platform(outbound = railAt(5, z = 10)))
        rails.alongX(0, 10, z = 10)

        val report = network.rebuild(rails)

        assertEquals(listOf("c:0:out"), report.unoriented)
    }

    @Test
    fun aPocketOnASwitchGivesNoDirection() {
        val network = TestNetwork()
        network.addPlatform("a", Platform(board = button, begin = railAt(2), outbound = railAt(4)))
        network.addPlatform("b", Platform(inbound = railAt(30), eject = railAt(33)))
        // Side rails next to `out` and `in` make both of them switches.
        val rails = FakeRails().alongX(0, 40).alongZ(4, 1, 2).alongZ(30, 1, 2)

        val report = network.rebuild(rails)

        assertEquals(setOf("a:0:begin" to "a:0:out", "b:0:in" to "b:0:eject"), network.links())
        assertEquals(listOf("a:0:out", "b:0:in"), report.unoriented)
    }

    @Test
    fun aBrokenPocketBuildsNoEdgeAndGivesNoDirection() {
        val network = TestNetwork()
        network.addPlatform("a", Platform(board = button, begin = railAt(2, z = 5), outbound = railAt(4)))
        network.addPlatform("b", Platform(inbound = railAt(30), eject = railAt(33, z = 5)))
        val rails = FakeRails().alongX(0, 40).alongX(0, 3, z = 5).alongX(32, 34, z = 5)

        val report = network.rebuild(rails)

        assertTrue(network.edges.isEmpty())
        assertEquals(listOf("a:0:out", "b:0:in"), report.unoriented)
    }

    /**
     * A loop clockwise round x 0..20 and z 0..10. Platform `a:0` leaves eastwards at (5, 0); `b:0` sits on the
     * east side with its spawn on the track; `a:1` arrives on the west side, just before `a:0`.
     */
    private fun loop(): Pair<TestNetwork, FakeRails> {
        val network = TestNetwork()
        network.addPlatform("a", Platform(outbound = railAt(5), heading = Cardinal.EAST))
        network.addPlatform("a", Platform(inbound = railAt(0, z = 5)))
        val b = Platform(board = button, begin = railAt(20, z = 5), outbound = railAt(20, z = 7))
        network.addPlatform("b", b.copy(inbound = railAt(20, z = 3)))
        val rails =
            FakeRails()
                .alongX(0, 20)
                .alongX(0, 20, z = 10)
                .alongZ(0, 1, 9)
                .alongZ(20, 1, 9)
        return network to rails
    }

    @Test
    fun aLoopIsOrientedFromOneSeedAndTurnsAroundAtTheStationOfTwoPlatforms() {
        val (network, rails) = loop()
        network.stations.getValue("a").terminal = true

        val report = network.rebuild(rails)

        assertEquals(
            setOf(
                "b:0:begin" to "b:0:out",
                "a:0:out" to "b:0:in",
                "b:0:in" to "b:0:out",
                "b:0:out" to "a:1:in",
                "a:1:in" to "a:0:out",
            ),
            network.links(),
        )
        assertEquals(setOf("a"), report.turnarounds)
        assertEquals(setOf("a"), report.terminals)
        assertTrue(report.unoriented.isEmpty())
        assertTrue(report.conflicts.isEmpty())
        // No eject anywhere, so boarding at b leads nowhere.
        assertEquals(listOf("b:0:begin"), report.boardingDeadEnds)
    }

    @Test
    fun insideAStationTheTrackRunsAtStationSpeedOnlyWhereItStops() {
        val (network, rails) = loop()
        val speeds = Speeds(station = 0.3, through = 0.55)

        network.rebuild(rails, speeds)

        // b's spawn is on the track between its `in` and `out`.
        assertEquals(0.3, network.edgeBetween("b:0:in", "b:0:out").cruiseSpeed)
        assertEquals(0.55, network.edgeBetween("a:1:in", "a:0:out").cruiseSpeed)
        assertEquals(speeds.cruise, network.edgeBetween("a:0:out", "b:0:in").cruiseSpeed)
    }

    @Test
    fun aPlatformThatHasNoOutIsReported() {
        val network = TestNetwork()
        network.addPlatform("s", Platform(id = "noOut", board = button, begin = railAt(2)))
        network.addPlatform("s", Platform(id = "alightOnly", inbound = railAt(5), eject = railAt(6)))

        val report = network.rebuild(FakeRails().alongX(0, 10))

        assertEquals(listOf("s:noOut:begin"), report.missingOut)
        assertEquals(listOf("s:noOut:begin"), EdgeBuilder.missingOut(network.stations.values))
    }

    @Test
    fun aSearchThatGivesUpIsReportedOncePerStart() {
        val (network, _) = straightLine()

        // From b:0:in the rails end after six blocks, within the limit.
        val report = network.rebuild(FakeRails().alongX(0, 35), maxSearch = 10)

        assertEquals(listOf("search-limit a:0:out, 10"), report.searchLimitHits.map { it.text })
        assertEquals(listOf<IssueTarget?>(IssueTarget.Node("a:0:out")), report.searchLimitHits.map { it.target })
        assertTrue("a:0:out" to "b:0:in" !in network.links())
    }

    @Test
    fun twoPointsOnOneBlockKeepOneNode() {
        val network = TestNetwork()
        network.addPlatform("a", Platform(outbound = railAt(5), heading = Cardinal.EAST))
        network.addPlatform("b", Platform(inbound = railAt(5)))
        network.addPlatform("c", Platform(inbound = railAt(9)))

        val report = network.rebuild(FakeRails().alongX(0, 10))

        val shared = report.junctionWarnings.single()
        assertEquals("point-shared-block", shared.key)
        assertEquals("point-shared-block in b:0, out a:0, 5,64,0", shared.text)
        assertEquals(IssueTarget.Node("b:0:in"), shared.target)
        assertEquals(setOf("a:0:out" to "c:0:in"), network.links())
    }

    @Test
    fun handAuthoredEdgesSurviveAndBlockTheirAutoTwin() {
        val (network, rails) = straightLine()
        val dense = (4..30).map { railAt(it) }
        network.putEdge(edge("manual", "a:0:out", "b:0:in", speed = 0.2, points = dense, auto = false))
        network.putEdge(edge("stale", "x:0:out", "y:0:in"))

        network.rebuild(rails)

        val manual = network.edges.getValue("manual")
        assertEquals(network.edgeBetween("a:0:out", "b:0:in"), manual)
        assertEquals(0.2, manual.cruiseSpeed)
        // Its points were compressed and its switch marks read again.
        assertEquals(listOf(railAt(4), railAt(30)), manual.points)
        assertNotNull(manual.switches)
        assertTrue("stale" !in network.edges)
    }

    @Test
    fun aWorldThatIsNotLoadedBuildsNothing() {
        val (network, _) = straightLine()

        val report = network.rebuild(FakeRails(name = "nether").alongX(0, 40))

        assertTrue(network.edges.isEmpty())
        assertEquals(listOf("a:0:out", "b:0:in"), report.unoriented)
    }

    @Test
    fun snapToRailFindsTheNearestRailCentre() {
        val rails = FakeRails().alongX(0, 4)

        assertEquals(railAt(2), EdgeBuilder.snapToRail(rails, Point(WORLD, 2.2, 65.0, 0.9)))
        val far = at(2, y = 64, z = 5)
        assertEquals(far, EdgeBuilder.snapToRail(rails, far))
        val elsewhere = Point("nether", 2.0, 64.0, 0.0)
        assertEquals(elsewhere, EdgeBuilder.snapToRail(rails, elsewhere))
    }

    @Test
    fun junctionsWithoutAnyLinkAreUnused() {
        val points = listOf("a:p1:out", "junction.used.x", "junction.used.z", "junction.idle.x", "junction.idle.z")
        val links =
            listOf(
                TrackChain.Link("a:p1:out", "junction.used.x", emptyList()),
                TrackChain.Link("junction.used.x", "b:p1:in", emptyList()),
            )

        assertEquals(listOf("idle"), EdgeBuilder.unusedJunctions(points, links))
        val target = listOf(TrackChain.Link("a:p1:out", "junction.join.x", emptyList()))
        assertEquals(emptyList(), EdgeBuilder.unusedJunctions(listOf("junction.join.x"), target))
    }

    @Test
    fun aStationWithoutPlatformsIsSkipped() {
        val network = TestNetwork()
        network.createLine("red", "Red", "COMPASS")
        network.addStation("red", Station("empty", "Empty", WORLD))

        assertEquals(RebuildReport(), network.rebuild(FakeRails().alongX(0, 4)))
    }
}
