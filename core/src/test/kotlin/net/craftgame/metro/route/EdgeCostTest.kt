package net.craftgame.metro.route

import net.craftgame.metro.config.Speeds
import net.craftgame.metro.data.Edge
import net.craftgame.metro.point
import kotlin.test.Test
import kotlin.test.assertEquals

class EdgeCostTest {
    private val speeds = Speeds(max = 1.2, station = 0.3, turn = 0.35)

    private fun edge(
        speed: Double?,
        vararg points: Pair<Double, Double>,
    ): Edge = Edge("e", "a", "b", speed, points.map { (x, z) -> point(x, z = z) }.toMutableList())

    @Test
    fun straightEdgeTakesLengthOverSpeed() {
        assertEquals(20.0, EdgeCost.ticks(edge(0.5, 0.0 to 0.0, 10.0 to 0.0), speeds), 1e-9)
    }

    @Test
    fun aZeroStoredSpeedRunsAtTheMinimumSpeed() {
        assertEquals(10.0 / Speeds.MIN, EdgeCost.ticks(edge(0.0, 0.0 to 0.0, 10.0 to 0.0), speeds), 1e-9)
    }

    @Test
    fun edgeWithoutSpeedUsesTheStationSpeed() {
        assertEquals(30.0, EdgeCost.ticks(edge(null, 0.0 to 0.0, 9.0 to 0.0), speeds), 1e-9)
    }

    @Test
    fun speedIsCappedByMax() {
        assertEquals(10.0, EdgeCost.ticks(edge(2.0, 0.0 to 0.0, 12.0 to 0.0), speeds), 1e-9)
    }

    @Test
    fun sharpTurnsSlowTheSegmentsAfterThem() {
        val stairs = edge(0.7, 0.0 to 0.0, 1.0 to 0.0, 1.0 to 1.0, 2.0 to 1.0, 2.0 to 2.0)
        // The first segment runs at 0.7, the three after a turn at 0.35.
        assertEquals(1 / 0.7 + 3 / 0.35, EdgeCost.ticks(stairs, speeds), 1e-9)
    }

    @Test
    fun aLongStraightRunOnlySlowsAtTheCorner() {
        val corner = edge(0.7, 0.0 to 0.0, 5.0 to 0.0, 5.0 to 5.0)
        // Densified to one point per block: only the segment leaving the corner is capped.
        assertEquals(9 / 0.7 + 1 / 0.35, EdgeCost.ticks(corner, speeds), 1e-9)
    }

    @Test
    fun gentleTurnsAreNotCapped() {
        val gentle = edge(0.7, 0.0 to 0.0, 4.0 to 0.0, 8.0 to 4.0)
        val length = 4.0 + 4.0 * Math.sqrt(2.0)
        assertEquals(length / 0.7, EdgeCost.ticks(gentle, speeds), 1e-9)
    }

    @Test
    fun noTurnCapWhenTheTurnSpeedIsAboveTheEdgeSpeed() {
        val stairs = edge(0.2, 0.0 to 0.0, 1.0 to 0.0, 1.0 to 1.0)
        assertEquals(10.0, EdgeCost.ticks(stairs, speeds), 1e-9)
        assertEquals(0.0, EdgeCost.ticks(edge(0.2, 0.0 to 0.0), speeds))
    }

    private fun denseTicks(
        edge: Edge,
        speeds: Speeds,
        slopeSpeed: Double = Slopes.OFF,
    ): Double {
        val path = Polyline.densify(edge.points).points
        if (path.size < 2) return 0.0
        val base = minOf(edge.cruiseSpeed ?: speeds.station, speeds.max).coerceAtLeast(1e-3)
        var total = 0.0
        val count = path.size - 1
        for (i in 0 until count) {
            val length = path[i].toVec3().distance(path[i + 1].toVec3())
            var speed = SwitchZone.speed(base, edge.startSwitchSpeed, edge.endSwitchSpeed, i, count)
            if (Slopes.isSlope(path[i], path[i + 1])) speed = Slopes.cap(speed, slopeSpeed)
            if (speeds.turn > 0.0 && Turns.isSharp(path, i)) speed = minOf(speed, speeds.turn)
            total += length / speed
        }
        return total
    }

    private fun edge3(
        speed: Double?,
        vararg points: Triple<Double, Double, Double>,
    ): Edge = Edge("e", "a", "b", speed, points.map { (x, y, z) -> point(x, y, z) }.toMutableList())

    @Test
    fun matchesTheDensifiedComputation() {
        val shapes =
            listOf(
                edge(0.5, 0.5 to 0.5, 40.5 to 0.5),
                edge(0.7, 0.0 to 0.0, 1.0 to 0.0, 1.0 to 1.0, 2.0 to 1.0, 2.0 to 2.0, 3.0 to 2.0),
                edge(0.9, 0.5 to 0.5, 12.5 to 0.5, 12.5 to 20.5, -3.5 to 20.5),
                edge(null, 0.0 to 0.0, 5.0 to 0.0, 5.0 to 5.0, 0.0 to 5.0, 0.0 to 0.0),
                edge(0.8, 0.0 to 0.0, 4.0 to 0.0, 8.0 to 4.0, 8.0 to 9.0),
                edge(0.6, 0.0 to 0.0, 0.0 to 0.0, 3.0 to 0.0, 3.0 to 3.0),
                edge(0.2, 0.0 to 0.0, 6.0 to 0.0, 6.0 to 6.0),
                edge3(
                    0.9,
                    Triple(0.5, 64.0, 0.5),
                    Triple(10.5, 69.0, 0.5),
                    Triple(10.5, 69.0, 7.5),
                    Triple(10.5, 60.0, 7.5),
                    Triple(14.5, 60.0, 7.5),
                ),
                edge3(
                    1.1,
                    Triple(0.3, 64.0, 0.1),
                    Triple(7.9, 66.4, 2.6),
                    Triple(8.2, 66.4, 13.7),
                    Triple(-2.6, 63.1, 13.2),
                    Triple(-2.4, 63.1, 13.4),
                ),
                edge3(
                    0.9,
                    Triple(0.5, 64.0625, 0.5),
                    Triple(5.5, 64.0625, 0.5),
                    Triple(8.5, 67.0625, 0.5),
                    Triple(8.5, 67.0625, 6.5),
                    Triple(8.5, 64.0625, 9.5),
                    Triple(11.5, 67.0625, 9.5),
                    Triple(11.5, 67.0625, 3.5),
                ),
            )
        val zoned =
            shapes.flatMap {
                listOf(
                    it.copy(startSwitchSpeed = 0.3),
                    it.copy(endSwitchSpeed = 0.25),
                    it.copy(startSwitchSpeed = 0.1, endSwitchSpeed = 0.4),
                )
            }
        for (shape in shapes + zoned) {
            assertEquals(denseTicks(shape, speeds), EdgeCost.ticks(shape, speeds), 1e-9)
            val noTurns = speeds.copy(turn = 0.0)
            assertEquals(denseTicks(shape, noTurns), EdgeCost.ticks(shape, noTurns), 1e-9)
            assertEquals(denseTicks(shape, speeds, 0.42), EdgeCost.ticks(shape, speeds, 0.42), 1e-9)
            assertEquals(denseTicks(shape, noTurns, 0.24), EdgeCost.ticks(shape, noTurns, 0.24), 1e-9)
        }
    }

    @Test
    fun slopesRunAtTheSlopeSpeed() {
        val hill = edge3(0.6, Triple(0.5, 64.0, 0.5), Triple(10.5, 64.0, 0.5), Triple(13.5, 67.0, 0.5))
        val climb = 3 * Math.sqrt(2.0)
        assertEquals(10 / 0.6 + climb / 0.42, EdgeCost.ticks(hill, speeds, 0.42), 1e-9)
        assertEquals(10 / 0.6 + climb / 0.6, EdgeCost.ticks(hill, speeds, Slopes.OFF), 1e-9)
        assertEquals(EdgeCost.ticks(hill, speeds), EdgeCost.ticks(hill, speeds, Slopes.OFF))
        val flat = edge(0.6, 0.0 to 0.0, 10.0 to 0.0)
        assertEquals(EdgeCost.ticks(flat, speeds), EdgeCost.ticks(flat, speeds, 0.42))
    }

    @Test
    fun onlyRailSlopeStepsCountAsSlopes() {
        // A straight virtual edge between anchors at different heights climbs a quarter block per block.
        val virtual = edge3(0.6, Triple(0.5, 64.0625, 0.5), Triple(8.5, 66.0625, 0.5))
        // An anchor left at the player's feet, just below the rail top.
        val unsnapped = edge3(0.6, Triple(0.5, 64.0, 0.5), Triple(1.5, 64.0625, 0.5), Triple(9.5, 64.0625, 0.5))
        for (shape in listOf(virtual, unsnapped)) {
            assertEquals(EdgeCost.ticks(shape, speeds), EdgeCost.ticks(shape, speeds, 0.42))
        }
        val slope = edge3(0.6, Triple(0.5, 64.0625, 0.5), Triple(2.5, 66.0625, 0.5))
        assertEquals(2 * Math.sqrt(2.0) / 0.42, EdgeCost.ticks(slope, speeds, 0.42), 1e-9)
    }

    @Test
    fun aSwitchZoneSlowsOnlyItsEndOfALongBranch() {
        val branch = edge(0.6, 0.0 to 0.0, 100.0 to 0.0).copy(startSwitchSpeed = 0.3)
        val zone = SwitchZone.SEGMENTS
        assertEquals(zone / 0.3 + (100 - zone) / 0.6, EdgeCost.ticks(branch, speeds), 1e-9)
        val ramp = branch.copy(startSwitchSpeed = null, endSwitchSpeed = 0.2)
        assertEquals((100 - zone) / 0.6 + zone / 0.2, EdgeCost.ticks(ramp, speeds), 1e-9)
    }

    @Test
    fun aSwitchZoneNeverSpeedsAnEdgeUp() {
        val branch = edge(0.4, 0.0 to 0.0, 10.0 to 0.0).copy(startSwitchSpeed = 0.9)
        assertEquals(10 / 0.4, EdgeCost.ticks(branch, speeds), 1e-9)
    }
}
