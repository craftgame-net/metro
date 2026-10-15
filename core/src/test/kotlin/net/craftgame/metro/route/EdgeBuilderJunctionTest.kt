package net.craftgame.metro.route

import net.craftgame.metro.TestNetwork
import net.craftgame.metro.addJunction
import net.craftgame.metro.addPlatform
import net.craftgame.metro.config.Speeds
import net.craftgame.metro.data.Junction
import net.craftgame.metro.data.Platform
import net.craftgame.metro.edgeBetween
import net.craftgame.metro.geo.Cardinal
import net.craftgame.metro.links
import net.craftgame.metro.rail.FakeRails
import net.craftgame.metro.railAt
import net.craftgame.metro.rebuild
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EdgeBuilderJunctionTest {
    private val speeds = Speeds(cruise = 0.6, branch = 0.3)

    /** A main line east along z = 0 from x = 0 to 30, with `a` leaving at x = 3 and `b` arriving at x = 25. */
    private fun mainLine(): Pair<TestNetwork, FakeRails> {
        val network = TestNetwork()
        network.addPlatform("a", Platform(outbound = railAt(3), heading = Cardinal.EAST))
        network.addPlatform("b", Platform(inbound = railAt(25)))
        return network to FakeRails().alongX(0, 30)
    }

    private fun RebuildReport.warningKeys(): List<String> = junctionWarnings.map { it.key }

    @Test
    fun aSplitSendsItsSideRailToTheBranch() {
        val (network, rails) = mainLine()
        rails.alongZ(10, 1, 10)
        network.addJunction("j", Junction.Kind.SPLIT, railAt(10), speed = 0.4)
        network.addPlatform("c", Platform(inbound = railAt(10, z = 8)))

        val report = network.rebuild(rails, speeds)

        assertEquals(
            setOf("a:0:out" to "junction.j.x", "junction.j.x" to "b:0:in", "junction.j.x" to "c:0:in"),
            network.links(),
        )
        val branch = network.edgeBetween("junction.j.x", "c:0:in")
        assertEquals(0.6, branch.cruiseSpeed)
        assertEquals(0.4, branch.startSwitchSpeed)
        assertEquals(null, branch.endSwitchSpeed)
        val through = network.edgeBetween("junction.j.x", "b:0:in")
        assertEquals(null, through.startSwitchSpeed)
        // The junction rail is the first point of the edges that leave it, and a switch mark there.
        assertEquals(railAt(10), through.points.first())
        assertEquals(0, through.switches?.first())
        val arriving = network.edgeBetween("a:0:out", "junction.j.x")
        assertEquals(arriving.points.lastIndex, arriving.switches?.last())
        assertTrue(report.junctionErrors.isEmpty())
        assertTrue(report.junctionWarnings.isEmpty())
        assertTrue(report.unoriented.isEmpty())
    }

    @Test
    fun aBranchWithoutAJunctionSpeedUsesTheBranchSpeed() {
        val (network, rails) = mainLine()
        rails.alongZ(10, 1, 10)
        network.addJunction("j", Junction.Kind.SPLIT, railAt(10))
        network.addPlatform("c", Platform(inbound = railAt(10, z = 8)))

        network.rebuild(rails, speeds)

        assertEquals(0.3, network.edgeBetween("junction.j.x", "c:0:in").startSwitchSpeed)
    }

    @Test
    fun anUnmarkedSwitchIsReportedOnTheLinkThatPassesIt() {
        val (network, rails) = mainLine()
        rails.alongZ(10, 1, 10)
        network.addPlatform("c", Platform(inbound = railAt(10, z = 8)))

        val report = network.rebuild(rails, speeds)

        // The branch is nearer than b, so the search takes it.
        assertEquals(setOf("a:0:out" to "c:0:in"), network.links())
        val unmarked = report.junctionWarnings.single()
        assertEquals("junction-unmarked", unmarked.key)
        assertEquals(IssueTarget.Block("world", 10, 64, 0), unmarked.target)
        assertEquals(listOf("b:0:in"), report.unoriented)
    }

    @Test
    fun aTrackEnteringASplitFromTheSideIsNotLinked() {
        val (network, rails) = mainLine()
        rails.alongZ(10, 1, 10)
        network.addJunction("j", Junction.Kind.SPLIT, railAt(10))
        network.addPlatform("d", Platform(outbound = railAt(10, z = 6), heading = Cardinal.NORTH))

        val report = network.rebuild(rails, speeds)

        assertTrue("d:0:out" to "junction.j.x" !in network.links())
        val side = report.junctionWarnings.filter { it.key == "junction-side-split" }
        assertEquals(listOf<IssueTarget?>(IssueTarget.Junction("j")), side.map { it.target })
    }

    @Test
    fun aSplitBranchThatBendsBackIsNotLinked() {
        val (network, rails) = mainLine()
        // The side rail leaves south, then turns west, against the travel on the main line.
        rails.alongZ(10, 1, 2).alongX(3, 9, z = 2)
        network.addJunction("j", Junction.Kind.SPLIT, railAt(10))
        network.addPlatform("c", Platform(inbound = railAt(5, z = 2)))

        val report = network.rebuild(rails, speeds)

        assertEquals(setOf("a:0:out" to "junction.j.x", "junction.j.x" to "b:0:in"), network.links())
        assertEquals(listOf("junction-side-split"), report.warningKeys())
        assertEquals(listOf("c:0:in"), report.unoriented)
    }

    @Test
    fun aSplitBranchThatEndsNowhereIsADeadEnd() {
        val (network, rails) = mainLine()
        rails.alongZ(10, 1, 4)
        network.addJunction("j", Junction.Kind.SPLIT, railAt(10))

        val report = network.rebuild(rails, speeds)

        assertEquals(listOf("junction-branch-dead-end"), report.warningKeys())
        assertEquals(IssueTarget.Junction("j"), report.junctionWarnings.single().target)
    }

    @Test
    fun aRampMergesIntoAJoinWithASwitchZoneAtItsEnd() {
        val (network, rails) = mainLine()
        rails.alongZ(20, 1, 8)
        network.addJunction("k", Junction.Kind.JOIN, railAt(20), speed = 0.25)
        network.addPlatform("d", Platform(outbound = railAt(20, z = 6), heading = Cardinal.NORTH))

        val report = network.rebuild(rails, speeds)

        assertEquals(
            setOf("a:0:out" to "junction.k.x", "junction.k.x" to "b:0:in", "d:0:out" to "junction.k.x"),
            network.links(),
        )
        val ramp = network.edgeBetween("d:0:out", "junction.k.x")
        assertEquals(0.6, ramp.cruiseSpeed)
        assertEquals(null, ramp.startSwitchSpeed)
        assertEquals(0.25, ramp.endSwitchSpeed)
        assertEquals(null, network.edgeBetween("a:0:out", "junction.k.x").endSwitchSpeed)
        assertTrue(report.junctionWarnings.isEmpty())
        assertTrue(report.conflicts.isEmpty())
    }

    @Test
    fun aRampMergingAgainstTheTravelIsDropped() {
        val (network, rails) = mainLine()
        // The ramp runs west along z = 2, then turns north into the join: a cart would have to turn back.
        rails.alongZ(20, 1, 2).alongX(21, 27, z = 2)
        network.addJunction("k", Junction.Kind.JOIN, railAt(20))
        network.addPlatform("e", Platform(outbound = railAt(25, z = 2), heading = Cardinal.WEST))

        val report = network.rebuild(rails, speeds)

        assertEquals(setOf("a:0:out" to "junction.k.x", "junction.k.x" to "b:0:in"), network.links())
        val backward = report.junctionWarnings.single()
        assertEquals("junction-join-backward", backward.key)
        assertEquals("junction-join-backward k, 20,64,0", backward.text)
    }

    /** Line `a` to `b` along x and line `n` to `s` along z, crossing at (10, 0). */
    private fun crossing(turns: Boolean): Pair<TestNetwork, FakeRails> {
        val network = TestNetwork()
        network.addPlatform("a", Platform(outbound = railAt(3), heading = Cardinal.EAST))
        network.addPlatform("b", Platform(inbound = railAt(17)))
        network.addPlatform("n", Platform(outbound = railAt(10, z = -7), heading = Cardinal.SOUTH))
        network.addPlatform("s", Platform(inbound = railAt(10, z = 7)))
        network.addJunction("x", Junction.Kind.CROSS, railAt(10), turns = turns)
        return network to FakeRails().alongX(0, 20).alongZ(10, -10, 10)
    }

    @Test
    fun aCrossingLinksBothTracksAndTurnsBetweenThem() {
        val (network, rails) = crossing(turns = true)

        val report = network.rebuild(rails, speeds.copy(turn = 0.2))

        assertEquals(
            setOf(
                "a:0:out" to "junction.x.x",
                "junction.x.x" to "b:0:in",
                "n:0:out" to "junction.x.z",
                "junction.x.z" to "s:0:in",
                "junction.x.x" to "s:0:in",
                "junction.x.z" to "b:0:in",
            ),
            network.links(),
        )
        // A turn runs at cruise speed; the sharp-turn cap slows it on the ride.
        val turn = network.edgeBetween("junction.x.x", "s:0:in")
        assertEquals(0.6, turn.cruiseSpeed)
        assertEquals(null, turn.startSwitchSpeed)
        assertTrue(report.junctionWarnings.isEmpty())
    }

    @Test
    fun aCrossingWithoutTurnsOnlyLinksStraightThrough() {
        val (network, rails) = crossing(turns = false)

        network.rebuild(rails, speeds)

        assertEquals(
            setOf(
                "a:0:out" to "junction.x.x",
                "junction.x.x" to "b:0:in",
                "n:0:out" to "junction.x.z",
                "junction.x.z" to "s:0:in",
            ),
            network.links(),
        )
    }

    @Test
    fun aCrossingTrackThatNothingOrientsIsReported() {
        val (network, rails) = mainLine()
        rails.alongZ(10, -5, 5)
        network.addJunction("x", Junction.Kind.CROSS, railAt(10))

        val report = network.rebuild(rails, speeds)

        assertEquals(setOf("a:0:out" to "junction.x.x", "junction.x.x" to "b:0:in"), network.links())
        assertEquals(listOf("junction-unoriented x, z"), report.junctionWarnings.map { it.text })
    }

    @Test
    fun junctionsThatDoNotFitTheRailsAreErrors() {
        val (network, rails) = mainLine()
        rails.alongZ(10, 1, 3)
        network.addJunction("nowhere", Junction.Kind.SPLIT, railAt(10, z = 40))
        network.addJunction("straight", Junction.Kind.SPLIT, railAt(5))
        network.addJunction("first", Junction.Kind.SPLIT, railAt(10))
        network.addJunction("second", Junction.Kind.JOIN, railAt(10))

        val report = network.rebuild(rails, speeds)

        assertEquals(
            listOf(
                "junction-no-rail nowhere",
                "junction-bad-block straight, split, 5,64,0",
                "junction-duplicate second, 10,64,0",
            ),
            report.junctionErrors.map { it.text },
        )
        assertTrue(report.junctionErrors.all { it.error })
        // The straight track runs through the bad junction as if it were not there.
        assertTrue("a:0:out" to "junction.first.x" in network.links())
    }

    @Test
    fun aJunctionNoTrackReachesIsUnusedAndNotAlsoUnoriented() {
        val (network, rails) = mainLine()
        rails.alongX(0, 10, z = 20).alongZ(5, 21, 22)
        network.addJunction("idle", Junction.Kind.SPLIT, railAt(5, z = 20))

        val report = network.rebuild(rails, speeds)

        assertEquals(listOf("junction-unused idle"), report.junctionWarnings.map { it.text })
    }

    @Test
    fun aPointOnAJunctionBlockIsReported() {
        val (network, rails) = mainLine()
        rails.alongZ(10, 1, 3)
        network.addJunction("j", Junction.Kind.SPLIT, railAt(10))
        network.addPlatform("c", Platform(inbound = railAt(10)))

        val report = network.rebuild(rails, speeds)

        val warning = report.junctionWarnings.first { it.key == "point-on-junction" }
        assertEquals("point-on-junction in c:0, j, 10,64,0", warning.text)
        assertEquals(IssueTarget.Node("c:0:in"), warning.target)
    }
}
