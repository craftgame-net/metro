package net.craftgame.metro.route

import net.craftgame.metro.at
import net.craftgame.metro.geo.Cardinal
import net.craftgame.metro.geo.Point
import net.craftgame.metro.geo.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrackChainTest {
    private val east = Cardinal.EAST.direction
    private val west = Cardinal.WEST.direction
    private val south = Cardinal.SOUTH.direction

    private fun path(): List<Point> = listOf(at(0), at(1))

    private fun singleHop(edges: Map<String, Pair<String, Vec3>>): (String, Vec3) -> List<TrackChain.Hop> =
        { node, _ -> edges[node]?.let { (to, heading) -> listOf(TrackChain.Hop(to, path(), heading)) }.orEmpty() }

    /** Wraps [hops] as a branch function whose result is never marked settled, so it is asked on every pass. */
    private fun unsettled(
        hops: (String, Vec3, Map<String, Vec3>) -> List<TrackChain.BranchHop>,
    ): (String, Vec3, Map<String, Vec3>) -> TrackChain.Branches =
        { node, heading, oriented -> TrackChain.Branches(hops(node, heading, oriented)) }

    @Test
    fun ringWhereInComesBeforeOut() {
        val edges =
            mapOf(
                "a:out" to ("b:in" to east),
                "b:in" to ("b:out" to east),
                "b:out" to ("a:in" to east),
                "a:in" to ("a:out" to east),
            )
        val result = TrackChain.walk(mapOf("a:out" to east), emptyMap(), singleHop(edges))

        assertEquals(setOf("a:in", "a:out", "b:in", "b:out"), result.headings.keys)
        assertEquals(4, result.links.size)
        assertTrue(result.conflicts.isEmpty())
    }

    @Test
    fun ringWhereOutComesBeforeIn() {
        val edges =
            mapOf(
                "a:out" to ("a:in" to east),
                "a:in" to ("b:out" to east),
                "b:out" to ("b:in" to east),
                "b:in" to ("a:out" to east),
            )
        val result = TrackChain.walk(mapOf("a:out" to east), emptyMap(), singleHop(edges))

        assertEquals(setOf("a:in", "a:out", "b:in", "b:out"), result.headings.keys)
        assertEquals(4, result.links.size)
        assertTrue(result.conflicts.isEmpty())
    }

    @Test
    fun oneSeedOrientsTheWholeRing() {
        val edges =
            mapOf(
                "p1" to ("p2" to east),
                "p2" to ("p3" to east),
                "p3" to ("p1" to east),
            )
        val result = TrackChain.walk(mapOf("p1" to east), emptyMap(), singleHop(edges))

        assertEquals(setOf("p1", "p2", "p3"), result.headings.keys)
    }

    @Test
    fun noSeedsOrientsNothing() {
        val result = TrackChain.walk(emptyMap(), emptyMap(), singleHop(emptyMap()))

        assertTrue(result.links.isEmpty())
        assertTrue(result.headings.isEmpty())
    }

    @Test
    fun twoOppositeExplicitSeedsConflictAtTheSecond() {
        val edges =
            mapOf(
                "a" to ("b" to east),
                "b" to ("c" to east),
                "c" to ("a" to east),
            )
        val explicit = linkedMapOf("a" to east, "b" to west)
        val result = TrackChain.walk(explicit, emptyMap(), singleHop(edges))

        assertEquals(setOf("b"), result.conflicts)
        assertEquals(west, result.headings["b"])
    }

    @Test
    fun explicitSeedOutranksDerivedOne() {
        val result = TrackChain.walk(mapOf("a" to east), mapOf("a" to west), singleHop(emptyMap()))

        assertEquals(setOf("a"), result.conflicts)
        assertEquals(east, result.headings["a"])
    }

    @Test
    fun eachPointIsExpandedExactlyOnce() {
        val edges =
            mapOf(
                "a:out" to ("b:in" to east),
                "b:in" to ("b:out" to east),
                "b:out" to ("a:in" to east),
                "a:in" to ("a:out" to east),
            )
        val calls = ArrayList<String>()
        val step: (String, Vec3) -> List<TrackChain.Hop> = { node, heading ->
            calls += node
            singleHop(edges)(node, heading)
        }
        TrackChain.walk(mapOf("a:out" to east), emptyMap(), step)

        assertEquals(listOf("a:out", "b:in", "b:out", "a:in"), calls)
    }

    @Test
    fun aTurnIsNotAConflict() {
        val edges =
            mapOf(
                "a" to ("b" to south),
                "c" to ("b" to east),
            )
        val explicit = linkedMapOf("a" to east, "c" to east)
        val result = TrackChain.walk(explicit, emptyMap(), singleHop(edges))

        assertTrue("b" !in result.conflicts)
        assertEquals(south, result.headings["b"])
    }

    @Test
    fun multipleExitsOrientEveryBranch() {
        val step: (String, Vec3) -> List<TrackChain.Hop> = { node, _ ->
            if (node == "a") {
                listOf(TrackChain.Hop("b", path(), east), TrackChain.Hop("c", path(), south))
            } else {
                emptyList()
            }
        }
        val result = TrackChain.walk(mapOf("a" to east), emptyMap(), step)

        assertEquals(setOf("a", "b", "c"), result.headings.keys)
        assertEquals(2, result.links.size)
    }

    @Test
    fun soleOtherPicksTheRemainingElement() {
        assertEquals("b", TrackChain.soleOther(listOf("a", "b"), "a"))
        assertNull(TrackChain.soleOther(listOf("a", "b", "c"), "a"))
        assertNull(TrackChain.soleOther(listOf("a"), "a"))
    }

    @Test
    fun directionIsNormalizedAndFlat() {
        val direction = TrackChain.direction(Vec3(0.0, 0.0, 0.0), Vec3(2.0, 5.0, 0.0))
        assertEquals(east, direction)
        assertNull(TrackChain.direction(Vec3(0.0, 3.0, 0.0), Vec3(0.0, 7.0, 0.0)))
    }

    private fun link(
        from: String,
        to: String,
    ): TrackChain.Link = TrackChain.Link(from, to, path())

    @Test
    fun turnaroundLinksTwoPlatformsOfTheSameStation() {
        assertEquals(setOf("c"), TrackChain.turnaroundStations(listOf(link("c:0:out", "c:1:in"))))
    }

    @Test
    fun aLinkWithinOnePlatformIsNotATurnaround() {
        assertEquals(emptySet(), TrackChain.turnaroundStations(listOf(link("c:0:in", "c:0:out"))))
    }

    @Test
    fun aLinkBetweenDifferentStationsIsNotATurnaround() {
        assertEquals(emptySet(), TrackChain.turnaroundStations(listOf(link("b:0:out", "c:0:in"))))
    }

    @Test
    fun unparsableNodesAreIgnored() {
        assertEquals(emptySet(), TrackChain.turnaroundStations(listOf(link("a", "c:1:in"))))
    }

    @Test
    fun cardinalPicksTheLargerAxis() {
        assertEquals("east", TrackChain.cardinal(east))
        assertEquals("west", TrackChain.cardinal(west))
        assertEquals("south", TrackChain.cardinal(south))
        assertEquals("north", TrackChain.cardinal(Cardinal.NORTH.direction))
    }

    @Test
    fun branchHopsOrientNewPointsAndThenWalkAgain() {
        val step: (String, Vec3) -> List<TrackChain.Hop> = { node, _ ->
            if (node == "y") listOf(TrackChain.Hop("z", path(), east)) else emptyList()
        }
        val branch =
            unsettled { node, _, _ ->
                if (node == "s") listOf(TrackChain.BranchHop("y", path(), south)) else emptyList()
            }
        val result = TrackChain.build(mapOf("s" to east), emptyMap(), step, branch)

        assertEquals(setOf("s", "y", "z"), result.headings.keys)
        assertEquals(south, result.headings["y"])
        assertEquals(east, result.headings["z"])
        assertEquals(TrackChain.LinkKind.BRANCH, result.links.first().kind)
        assertTrue(result.links.any { it.from == "y" && it.to == "z" })
    }

    @Test
    fun hopWithoutHeadingLinksButDoesNotOrient() {
        val step: (String, Vec3) -> List<TrackChain.Hop> = { node, _ ->
            if (node == "a") listOf(TrackChain.Hop("b", path(), null)) else emptyList()
        }
        val result = TrackChain.build(mapOf("a" to east), emptyMap(), step, unsettled { _, _, _ -> emptyList() })

        assertEquals(setOf("a"), result.headings.keys)
        assertTrue(result.links.any { it.from == "a" && it.to == "b" })
    }

    @Test
    fun branchIntoAnOrientedJoinKeepsItsHeading() {
        val step: (String, Vec3) -> List<TrackChain.Hop> = { node, _ ->
            if (node == "j") listOf(TrackChain.Hop("k", path(), east)) else emptyList()
        }
        val branch =
            unsettled { node, _, _ ->
                if (node == "s") listOf(TrackChain.BranchHop("j", path(), null)) else emptyList()
            }
        val result = TrackChain.build(mapOf("s" to south, "j" to east), emptyMap(), step, branch)

        assertEquals(east, result.headings["j"])
        assertTrue(result.conflicts.isEmpty())
        assertTrue(result.links.any { it.from == "s" && it.to == "j" })
    }

    @Test
    fun branchIsRetriedOnceItsDependencyIsOriented() {
        val branch =
            unsettled { node, _, oriented ->
                when {
                    node == "s" && "j" in oriented -> listOf(TrackChain.BranchHop("j", path(), null))
                    node == "t" -> listOf(TrackChain.BranchHop("j", path(), south))
                    else -> emptyList()
                }
            }
        val result = TrackChain.build(mapOf("s" to east, "t" to south), emptyMap(), { _, _ -> emptyList() }, branch)

        assertTrue(result.links.any { it.from == "s" && it.to == "j" })
        assertEquals(south, result.headings["j"])
    }

    @Test
    fun crossTurnsCarryTheTurnKind() {
        val branch =
            unsettled { node, _, _ ->
                val turn = TrackChain.BranchHop("t", path(), null, TrackChain.LinkKind.TURN)
                if (node == "x") listOf(turn) else emptyList()
            }
        val result = TrackChain.build(mapOf("x" to east, "t" to south), emptyMap(), { _, _ -> emptyList() }, branch)

        assertEquals(TrackChain.LinkKind.TURN, result.links.single().kind)
    }

    @Test
    fun branchOppositeToAnExistingHeadingIsAConflict() {
        val branch =
            unsettled { node, _, _ ->
                if (node == "s") listOf(TrackChain.BranchHop("j", path(), west)) else emptyList()
            }
        val result = TrackChain.build(mapOf("s" to south, "j" to east), emptyMap(), { _, _ -> emptyList() }, branch)

        assertEquals(setOf("j"), result.conflicts)
    }

    @Test
    fun settledBranchesAreAskedOnce() {
        val calls = HashMap<String, Int>()
        val branch: (String, Vec3, Map<String, Vec3>) -> TrackChain.Branches = { node, _, oriented ->
            calls.merge(node, 1, Int::plus)
            when (node) {
                "s" -> TrackChain.Branches(listOf(TrackChain.BranchHop("y", path(), south)), settled = true)
                // Gives hops only once "y" has a direction, which the first pass gives it.
                "t" -> {
                    val hops = if ("y" in oriented) listOf(TrackChain.BranchHop("u", path(), east)) else emptyList()
                    TrackChain.Branches(hops)
                }
                else -> TrackChain.Branches(emptyList(), settled = true)
            }
        }
        val result = TrackChain.build(mapOf("s" to east, "t" to south), emptyMap(), { _, _ -> emptyList() }, branch)

        assertEquals(setOf("s", "t", "y", "u"), result.headings.keys)
        assertEquals(1, calls["s"])
        assertEquals(1, calls["y"])
        assertTrue(calls.getValue("t") > 1)
    }
}
