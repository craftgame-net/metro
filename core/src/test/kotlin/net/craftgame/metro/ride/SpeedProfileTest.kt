package net.craftgame.metro.ride

import net.craftgame.metro.at
import net.craftgame.metro.config.Speeds
import net.craftgame.metro.geo.Point
import net.craftgame.metro.point
import net.craftgame.metro.railAt
import net.craftgame.metro.route.Slopes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpeedProfileTest {
    private val straight = listOf(at(0), at(1), at(2), at(3), at(4))
    private val withTurn = listOf(at(0), at(1), at(2), at(2, z = 1), at(2, z = 2))
    private val speeds = List(5) { 0.6 }

    @Test
    fun straightPathIsNotTurnLimited() {
        val limits = SpeedProfile.limits(straight, speeds, decel = 0.03, turnSpeed = 0.1)
        assertTrue(limits[1] > 0.1, "no turn, so the cap must not apply")
    }

    @Test
    fun sharpTurnCapsSpeed() {
        val limits = SpeedProfile.limits(withTurn, speeds, decel = 0.03, turnSpeed = 0.2)
        assertEquals(0.2, limits[2], 1e-9)
        assertTrue(limits[1] < limits[0], "the cart should already be braking before the turn")
    }

    @Test
    fun turnSpeedZeroDisablesTheCap() {
        val limits = SpeedProfile.limits(withTurn, speeds, decel = 0.03, turnSpeed = 0.0)
        assertTrue(limits[2] > 0.2, "with the cap off, only the braking envelope applies")
    }

    @Test
    fun turnSpeedAtOrAboveTheSegmentSpeedLeavesItAlone() {
        val limits = SpeedProfile.limits(withTurn, speeds, decel = 0.0, turnSpeed = 0.8)
        assertEquals(0.6, limits[2], 1e-9, "a cap above the segment speed changes nothing")
    }

    private val turnAt20 = (0..20).map { at(it) } + (1..20).map { at(20, z = it) }
    private val line40 = (0..40).map { at(it) }

    @Test
    fun zeroWideningMatchesThePlainProfile() {
        val zigzag = (0..60).map { i -> at((i + 1) / 2 + i / 7, i / 2) }
        val varied = List(61) { i -> if (i % 9 < 3) 0.3 else 0.6 }
        for (path in listOf(zigzag, turnAt20, line40, straight, withTurn)) {
            val speeds = varied.take(path.size)
            for (decel in listOf(0.0, 0.03)) {
                val expected = plainLimits(path, speeds, decel, turnSpeed = 0.35)
                val actual = SpeedProfile.limits(path, speeds, decel, 0.35, lead = 0.0, trail = 0.0)
                assertTrue(expected.contentEquals(actual), "${expected.toList()} vs ${actual.toList()}")
            }
        }
    }

    @Test
    fun turnCapCoversLeadBeforeAndTrailAfterTheTurn() {
        val speeds = List(turnAt20.size) { 0.6 }
        val limits =
            SpeedProfile.limits(
                turnAt20,
                speeds,
                decel = 0.0,
                turnSpeed = 0.2,
                lead = 6.0,
                trail = 2.0,
            )
        for (i in 14..22) assertEquals(0.2, limits[i], 1e-9, "point $i")
        assertEquals(0.6, limits[13], 1e-9)
        assertEquals(0.6, limits[23], 1e-9)
    }

    @Test
    fun slowerSegmentStartsLeadBlocksEarlier() {
        val speeds = List(line40.size) { if (it in 20..29) 0.3 else 0.6 }
        val limits =
            SpeedProfile.limits(
                line40,
                speeds,
                decel = 0.0,
                turnSpeed = 0.0,
                lead = 6.0,
                trail = 2.0,
            )
        for (i in 14..31) assertEquals(0.3, limits[i], 1e-9, "point $i")
        assertEquals(0.6, limits[13], 1e-9)
        assertEquals(0.6, limits[32], 1e-9)
    }

    @Test
    fun finalStopIsNotWidened() {
        val path = (0..19).map { at(it) }
        val speeds = List(path.size) { 0.6 }
        val limits =
            SpeedProfile.limits(
                path,
                speeds,
                decel = 0.03,
                turnSpeed = 0.0,
                lead = 16.0,
                trail = 2.0,
            )
        assertEquals(0.6, limits[0], 1e-9, "a big lead must not reach the stop")
        for (i in 0 until path.size - 1) assertTrue(limits[i] > 0.0, "point $i stalled")
        assertEquals(0.0, limits.last())
    }

    @Test
    fun envelopeBrakesIntoTheWidenedZone() {
        val speeds = List(line40.size) { if (it in 20..29) 0.3 else 0.6 }
        val limits =
            SpeedProfile.limits(
                line40,
                speeds,
                decel = 0.03,
                turnSpeed = 0.0,
                lead = 6.0,
                trail = 2.0,
            )
        assertEquals(0.3, limits[14], 1e-9)
        assertEquals(Math.sqrt(0.3 * 0.3 + 2 * 0.03), limits[13], 1e-9)
        assertTrue(limits[12] > limits[13])
    }

    /** Level up to x = 10, three blocks up to x = 13, then level again: segments 10 to 12 are slopes. */
    private val hill = (0..30).map { at(it, y = (it - 10).coerceIn(0, 3)) }
    private val downhill = (0..30).map { at(it, y = 3 - (it - 10).coerceIn(0, 3)) }
    private val hillSpeeds = List(hill.size) { 0.6 }

    private fun slopeLimits(
        path: List<Point>,
        speeds: List<Double> = hillSpeeds,
        slope: SpeedProfile.Slope,
        turnSpeed: Double = 0.0,
    ): DoubleArray = SpeedProfile.limits(path, speeds, decel = 0.0, turnSpeed = turnSpeed, slope = slope)

    @Test
    fun aLevelPathIgnoresTheSlopeCap() {
        val plain = SpeedProfile.limits(line40, List(line40.size) { 0.6 }, decel = 0.03, turnSpeed = 0.35)
        val slope =
            SpeedProfile.limits(
                line40,
                List(line40.size) { 0.6 },
                decel = 0.03,
                turnSpeed = 0.35,
                slope = SpeedProfile.Slope(0.42, 2.0, 2.0),
            )
        assertTrue(plain.contentEquals(slope))
    }

    @Test
    fun slopesUpAndDownRunAtTheSlopeSpeed() {
        for (path in listOf(hill, downhill)) {
            val limits = slopeLimits(path, slope = SpeedProfile.Slope(0.42))
            for (i in 10..12) assertEquals(0.42, limits[i], 1e-9, "point $i")
            assertEquals(0.6, limits[9], 1e-9)
            assertEquals(0.6, limits[13], 1e-9)
        }
    }

    @Test
    fun theSlopeCapCoversLeadBeforeAndTrailAfterTheSlope() {
        val limits = slopeLimits(hill, slope = SpeedProfile.Slope(0.42, lead = 2.0, trail = 2.0))
        for (i in 8..14) assertEquals(0.42, limits[i], 1e-9, "point $i")
        assertEquals(0.6, limits[7], 1e-9)
        assertEquals(0.6, limits[15], 1e-9)
    }

    @Test
    fun slopeOffLeavesEveryLimitAsBefore() {
        val zigzag = (0..60).map { i -> at((i + 1) / 2 + i / 7, i % 5, i / 2) }
        for (path in listOf(hill, downhill, zigzag)) {
            val speeds = List(path.size) { i -> if (i % 9 < 3) 0.3 else 0.6 }
            val plain = SpeedProfile.limits(path, speeds, 0.03, 0.35, 6.0, 2.0)
            val full = SpeedProfile.limits(path, speeds, 0.03, 0.35, 6.0, 2.0, SpeedProfile.Slope(Slopes.OFF, 2.0, 2.0))
            assertTrue(plain.contentEquals(full))
        }
    }

    @Test
    fun theLowestOfSlopeTurnAndSwitchZoneWins() {
        // A slower switch zone on the middle of the slope: it keeps its own speed.
        val zoned = hillSpeeds.mapIndexed { i, speed -> if (i == 11) 0.3 else speed }
        val limits = slopeLimits(hill, zoned, SpeedProfile.Slope(0.42))
        assertEquals(0.3, limits[11], 1e-9)
        assertEquals(0.42, limits[10], 1e-9)

        // A sharp turn right after the slope: the turn cap is lower than the slope cap.
        val turning = (0..13).map { at(it, y = (it - 10).coerceIn(0, 3)) } + (1..10).map { at(13, y = 3, z = it) }
        val turned =
            slopeLimits(
                turning,
                List(turning.size) { 0.6 },
                SpeedProfile.Slope(0.42, lead = 2.0, trail = 2.0),
                turnSpeed = 0.2,
            )
        assertEquals(0.2, turned[13], 1e-9)
        assertEquals(0.42, turned[12], 1e-9)
        assertEquals(0.42, turned[14], 1e-9)
    }

    @Test
    fun theSlopeCapNeverDropsBelowTheMinimumSpeed() {
        val limits = slopeLimits(hill, List(hill.size) { 0.06 }, SpeedProfile.Slope(0.01))
        assertEquals(Speeds.MIN, limits[11], 1e-9)
        assertEquals(0.06, limits[9], 1e-9)
    }

    @Test
    fun theTrailCountsFromTheEndOfALongSlowSegment() {
        // A slower switch zone on the last slope segment, which is about 1.41 blocks long.
        val zoned = hillSpeeds.mapIndexed { i, speed -> if (i == 12) 0.3 else speed }
        val limits = SpeedProfile.limits(hill, zoned, decel = 0.0, turnSpeed = 0.0, trail = 2.0)
        for (i in 12..14) assertEquals(0.3, limits[i], 1e-9, "point $i")
        assertEquals(0.6, limits[15], 1e-9)
    }

    @Test
    fun onlyRailSlopeStepsCountAsSlopes() {
        val slope = SpeedProfile.Slope(0.42, lead = 2.0, trail = 2.0)
        // The dense points of a straight virtual edge that climbs a quarter block per block.
        val virtual = (0..16).map { point(it + 0.5, 64.0625 + it * 0.25) }
        // An anchor left at the player's feet, just below the rail top.
        val unsnapped = listOf(point(0.5, 64.0)) + (1..16).map { railAt(it) }
        for (path in listOf(virtual, unsnapped)) {
            val speeds = List(path.size) { 0.6 }
            val plain = SpeedProfile.limits(path, speeds, decel = 0.03, turnSpeed = 0.35)
            val sloped = SpeedProfile.limits(path, speeds, decel = 0.03, turnSpeed = 0.35, slope = slope)
            assertTrue(plain.contentEquals(sloped))
        }
        // Rail centres over a slope rail at x = 5: the step from x = 5 to 6 climbs one block.
        val rails = (0..5).map { railAt(it) } + (6..16).map { railAt(it, y = 65) }
        val limits = SpeedProfile.limits(rails, List(rails.size) { 0.6 }, 0.0, 0.0, slope = SpeedProfile.Slope(0.42))
        assertEquals(0.42, limits[5], 1e-9)
        assertEquals(0.6, limits[4], 1e-9)
        assertEquals(0.6, limits[6], 1e-9)
    }

    private fun plainLimits(
        path: List<Point>,
        segmentSpeeds: List<Double>,
        decel: Double,
        turnSpeed: Double,
    ): DoubleArray {
        val limits = DoubleArray(path.size)
        if (path.isEmpty()) return limits
        for (i in path.size - 2 downTo 0) {
            val segment = path[i].toVec3().distance(path[i + 1].toVec3())
            val envelope =
                if (decel > 1e-9) Math.sqrt(limits[i + 1] * limits[i + 1] + 2 * decel * segment) else Double.MAX_VALUE
            var value = minOf(segmentSpeeds.getOrElse(i) { 0.0 }, envelope)
            if (i > 0 && turnSpeed > 0.0 && turnDeg(path, i) >= 90.0 - 1e-6) value = minOf(value, turnSpeed)
            limits[i] = value
        }
        return limits
    }

    private fun turnDeg(
        path: List<Point>,
        i: Int,
    ): Double {
        val ax = path[i].x - path[i - 1].x
        val az = path[i].z - path[i - 1].z
        val bx = path[i + 1].x - path[i].x
        val bz = path[i + 1].z - path[i].z
        val cos = ((ax * bx + az * bz) / (Math.hypot(ax, az) * Math.hypot(bx, bz))).coerceIn(-1.0, 1.0)
        return Math.toDegrees(Math.acos(cos))
    }
}
