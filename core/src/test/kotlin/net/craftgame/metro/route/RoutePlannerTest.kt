package net.craftgame.metro.route

import net.craftgame.metro.at
import net.craftgame.metro.config.Speeds
import net.craftgame.metro.data.Edge
import net.craftgame.metro.railAt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoutePlannerTest {
    private fun edge(
        speed: Double?,
        vararg x: Int,
    ): Edge = Edge("e-$speed-${x.first()}", "a", "b", speed, x.map(::at).toMutableList(), switches = emptyList())

    private fun marked(
        switches: List<Int>?,
        vararg x: Int,
    ): Edge = Edge("m-${x.first()}", "a", "b", 0.5, x.map(::at).toMutableList(), switches = switches)

    @Test
    fun pathJoinsEdgesWithoutRepeatingTheJoint() {
        val path = RoutePlanner.buildPath(listOf(edge(0.3, 0, 1), edge(0.6, 1, 2, 3)))
        assertEquals(listOf(0, 1, 2, 3), path.map { it.blockX })
    }

    @Test
    fun planCarriesEachSegmentSpeedAndATrailingZero() {
        val plan = RoutePlanner.buildPlan(listOf(edge(0.3, 0, 1), edge(0.6, 1, 2, 3)), fallbackSpeed = 0.25)
        assertEquals(listOf(0, 1, 2, 3), plan.points.map { it.blockX })
        assertEquals(listOf(0.3, 0.6, 0.6, 0.0), plan.segmentSpeeds)
    }

    @Test
    fun edgesWithoutSpeedUseTheFallback() {
        val plan = RoutePlanner.buildPlan(listOf(edge(null, 0, 1)), fallbackSpeed = 0.25)
        assertEquals(0.25, plan.segmentSpeeds.first())
    }

    @Test
    fun noSegmentRunsFasterThanTheHardCap() {
        val route = listOf(edge(1.4, 0, 1), edge(0.6, 1, 2), edge(null, 2, 3))
        val plan = RoutePlanner.buildPlan(route, fallbackSpeed = 2.0, maxSpeed = 1.2)
        assertEquals(listOf(1.2, 0.6, 1.2, 0.0), plan.segmentSpeeds)
    }

    @Test
    fun assembleClampsAnchorsAndKeepsSpeedsAligned() {
        val plan = RoutePlanner.buildPlan(listOf(edge(0.5, 1, 2), edge(0.6, 2, 3)), fallbackSpeed = 0.1)
        val assembled = RoutePlanner.assemble(at(0), plan, at(4), fallback = 0.1)

        assertEquals(listOf(0, 1, 2, 3, 4), assembled.points.map { it.blockX })
        assertEquals(assembled.points.size, assembled.segmentSpeeds.size)
        assertEquals(0.5, assembled.segmentSpeeds[1], 1e-9)
        assertEquals(0.6, assembled.segmentSpeeds[2], 1e-9)
    }

    @Test
    fun assembleNeverGivesARealSegmentTheStopMarker() {
        val plan = RoutePlanner.buildPlan(listOf(edge(0.5, 1, 2), edge(0.6, 2, 3)), fallbackSpeed = 0.1)
        val assembled = RoutePlanner.assemble(at(0), plan, at(5), fallback = 0.1)

        assertEquals(listOf(0, 1, 2, 3, 5), assembled.points.map { it.blockX })
        assertEquals(listOf(0.5, 0.5, 0.6, 0.6, 0.0), assembled.segmentSpeeds)
    }

    @Test
    fun assembleOfASinglePointPlanUsesTheFallback() {
        val plan = RoutePlanner.buildPlan(listOf(edge(0.5, 2)), fallbackSpeed = 0.1)
        val assembled = RoutePlanner.assemble(at(0), plan, at(4), fallback = 0.2)

        assertEquals(listOf(0, 2, 4), assembled.points.map { it.blockX })
        assertEquals(listOf(0.2, 0.2, 0.0), assembled.segmentSpeeds)
    }

    @Test
    fun switchMarksMapOntoTheJoinedPath() {
        // The second edge shares its first point with the first edge's last one.
        val route = listOf(marked(listOf(2), 0, 1, 2), marked(listOf(0, 2), 2, 3, 4))
        val plan = RoutePlanner.buildPlan(route, fallbackSpeed = 0.1)
        assertEquals(listOf(0, 1, 2, 3, 4), plan.points.map { it.blockX })
        assertEquals(listOf(2, 4), plan.switchCandidates)
    }

    @Test
    fun switchMarksShiftPastAnEdgeThatDoesNotShareItsJoint() {
        val route = listOf(marked(emptyList(), 0, 1), marked(listOf(0, 1), 5, 6))
        val plan = RoutePlanner.buildPlan(route, fallbackSpeed = 0.1)
        assertEquals(listOf(0, 1, 5, 6), plan.points.map { it.blockX })
        assertEquals(listOf(2, 3), plan.switchCandidates)
    }

    @Test
    fun edgesWithUnknownOrStaleMarksAreCheckedEverywhere() {
        val plan =
            RoutePlanner.buildPlan(
                listOf(marked(emptyList(), 0, 1, 2), marked(null, 2, 3), marked(listOf(7), 3, 4, 5)),
                fallbackSpeed = 0.1,
            )
        assertEquals(listOf(0, 1, 2, 3, 4, 5), plan.points.map { it.blockX })
        assertEquals(listOf(2, 3, 4, 5), plan.switchCandidates)
    }

    @Test
    fun assembleKeepsSwitchMarksOnTheirPoints() {
        val route = listOf(marked(listOf(0, 1), 1, 2), marked(listOf(1), 2, 3))
        val plan = RoutePlanner.buildPlan(route, fallbackSpeed = 0.1)
        val assembled = RoutePlanner.assemble(at(0), plan, at(4), fallback = 0.1)
        assertEquals(listOf(0, 1, 2, 3, 4), assembled.points.map { it.blockX })
        assertEquals(listOf(1, 2, 3), assembled.switchCandidates)

        val clamped = RoutePlanner.assemble(at(1), plan, at(3), fallback = 0.1)
        assertEquals(listOf(1, 2, 3), clamped.points.map { it.blockX })
        assertEquals(listOf(0, 1, 2), clamped.switchCandidates)
    }

    @Test
    fun plansFromCompressedEdgesMatchPlansFromDenseOnes() {
        val first = (0..10).map { railAt(it) } + (1..4).map { railAt(10 + it, y = 64 + it) }
        val second = (0..12).map { railAt(14, y = 68, z = it) } + (1..6).map { railAt(14 + it, y = 68, z = 12) }
        val third = (0..8).map { railAt(20 + it, y = 68, z = 12) }
        val dense =
            listOf(
                Edge("a", "a", "b", 0.4, first.toMutableList(), switches = listOf(3, 14)),
                Edge("b", "b", "c", null, second.toMutableList(), switches = listOf(0, 7)),
                Edge("c", "c", "d", 0.6, third.toMutableList(), switches = null),
            )
        val compressed =
            dense.map { edge ->
                val result = Polyline.compress(edge.points, edge.switches.orEmpty())
                edge.copy(points = result.points.toMutableList(), switches = edge.switches?.map { result.indexMap[it] })
            }
        assertTrue(compressed.sumOf { it.points.size } < dense.sumOf { it.points.size })

        val expected = RoutePlanner.buildPlan(dense, fallbackSpeed = 0.25)
        val actual = RoutePlanner.buildPlan(compressed, fallbackSpeed = 0.25)
        assertEquals(expected.points, actual.points)
        assertEquals(expected.segmentSpeeds, actual.segmentSpeeds)
        assertEquals(expected.switchCandidates, actual.switchCandidates)

        // Route length reads stored points: compression keeps it.
        fun length(route: List<Edge>): Double {
            val path = RoutePlanner.buildPath(route)
            return (1 until path.size).sumOf { path[it - 1].toVec3().distance(path[it].toVec3()) }
        }
        assertEquals(length(dense), length(compressed), 1e-9)
    }

    private fun zoned(
        speed: Double,
        from: Int,
        to: Int,
        start: Double? = null,
        end: Double? = null,
    ): Edge = Edge("z-$from", "a", "b", speed, mutableListOf(at(from), at(to)), true, emptyList(), start, end)

    @Test
    fun aLongBranchIsSlowOnlyAtItsSwitch() {
        val branch = zoned(0.6, 0, 200, start = 0.3)
        val plan = RoutePlanner.buildPlan(listOf(branch), fallbackSpeed = 0.25)
        val speeds = plan.segmentSpeeds.dropLast(1)
        assertEquals(200, speeds.size)
        assertEquals(List(SwitchZone.SEGMENTS) { 0.3 }, speeds.take(SwitchZone.SEGMENTS))
        assertTrue(speeds.drop(SwitchZone.SEGMENTS).all { it == 0.6 })
    }

    @Test
    fun aRampIsSlowOnlyWhereItJoins() {
        val main = edge(0.6, 0, 10)
        val ramp = zoned(0.6, 10, 60, end = 0.2)
        val plan = RoutePlanner.buildPlan(listOf(main, ramp), fallbackSpeed = 0.25, maxSpeed = 1.2)
        val speeds = plan.segmentSpeeds.dropLast(1)
        assertEquals(60, speeds.size)
        assertTrue(speeds.dropLast(SwitchZone.SEGMENTS).all { it == 0.6 })
        assertEquals(List(SwitchZone.SEGMENTS) { 0.2 }, speeds.takeLast(SwitchZone.SEGMENTS))
    }

    @Test
    fun switchZonesAreCappedByMax() {
        val branch = zoned(1.4, 0, 5, start = 1.3)
        val plan = RoutePlanner.buildPlan(listOf(branch), fallbackSpeed = 0.25, maxSpeed = 1.0)
        assertTrue(plan.segmentSpeeds.dropLast(1).all { it == 1.0 })
    }

    @Test
    fun noSegmentRunsSlowerThanTheMinimumSpeed() {
        val route = listOf(edge(0.0, 0, 1), zoned(0.6, 1, 5, start = 0.0), edge(null, 5, 6))
        val plan = RoutePlanner.buildPlan(route, fallbackSpeed = -1.0, maxSpeed = 1.2)
        val speeds = plan.segmentSpeeds.dropLast(1)
        assertTrue(speeds.all { it >= Speeds.MIN })
        assertEquals(Speeds.MIN, speeds.first())
        assertEquals(0.0, plan.segmentSpeeds.last())
    }
}
