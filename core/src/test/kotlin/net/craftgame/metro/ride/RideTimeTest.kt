package net.craftgame.metro.ride

import net.craftgame.metro.geo.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RideTimeTest {
    private val physics = RideTime.Physics(accel = 0.02, decel = 0.03, turnSpeed = 0.35)

    private fun line(length: Int): List<Point> = (0..length).map { Point("world", it.toDouble(), 0.0, 0.0) }

    @Test
    fun emptyOrSinglePointPathTakesNoTime() {
        assertEquals(0, RideTime.ticks(emptyList(), emptyList(), physics))
        assertEquals(0, RideTime.ticks(line(0), listOf(0.0), physics))
    }

    @Test
    fun longerTrackTakesLonger() {
        val short = RideTime.ticks(line(50), List(51) { 0.6 }, physics)
        val long = RideTime.ticks(line(200), List(201) { 0.6 }, physics)
        assertTrue(long > short)
    }

    @Test
    fun longStraightRunApproachesCruiseTime() {
        val ticks = RideTime.ticks(line(600), List(601) { 0.6 }, physics)
        // 1000 ticks at a steady 0.6, plus a little for starting and stopping.
        assertTrue(ticks in 1000..1100, "was $ticks")
    }

    @Test
    fun slowerSegmentsTakeLonger() {
        val fast = RideTime.ticks(line(100), List(101) { 0.6 }, physics)
        val slow = RideTime.ticks(line(100), List(101) { 0.25 }, physics)
        assertTrue(slow > fast)
    }

    @Test
    fun sharpTurnsSlowTheRide() {
        val straight = line(40)
        val zigzag =
            (0..40).map { i -> Point("world", ((i + 1) / 2).toDouble(), 0.0, (i / 2).toDouble()) }
        val speeds = List(41) { 0.6 }
        assertTrue(RideTime.ticks(zigzag, speeds, physics) > RideTime.ticks(straight, speeds, physics))
    }

    @Test
    fun slopesSlowTheRideButALevelOneIsUnchanged() {
        val hill = (0..60).map { Point("world", it.toDouble(), (it - 20).coerceIn(0, 10).toDouble(), 0.0) }
        val speeds = List(61) { 0.6 }
        val slowed = physics.copy(slope = SpeedProfile.Slope(0.3, 2.0, 2.0))
        assertTrue(RideTime.ticks(hill, speeds, slowed) > RideTime.ticks(hill, speeds, physics))
        assertEquals(RideTime.ticks(line(60), speeds, physics), RideTime.ticks(line(60), speeds, slowed))
    }

    @Test
    fun walkTicksUseWalkingPace() {
        assertEquals(0, RideTime.walkTicks(0.0))
        assertTrue(RideTime.walkTicks(8.6) in 38..41)
    }
}
