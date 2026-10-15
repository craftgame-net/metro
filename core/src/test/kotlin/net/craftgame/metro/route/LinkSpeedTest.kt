package net.craftgame.metro.route

import net.craftgame.metro.config.Speeds
import net.craftgame.metro.railAt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LinkSpeedTest {
    private val speeds = Speeds(max = 1.2, maxCruise = 1.0, cruise = 0.6, station = 0.3, through = 0.55, branch = 0.3)

    private fun end(
        station: String,
        line: String? = "red",
        speed: Double? = null,
    ) = LinkSpeed.End(station, line, speed)

    @Test
    fun sameLineWithItsOwnSpeedRunsAtIt() {
        assertEquals(0.9, LinkSpeed.track(end("a", speed = 0.9), end("b", speed = 0.9), false, speeds))
    }

    @Test
    fun sameLineWithoutASpeedRunsAtCruise() {
        assertEquals(0.6, LinkSpeed.track(end("a"), end("b"), false, speeds))
    }

    @Test
    fun differentLinesRunAtCruise() {
        assertEquals(0.6, LinkSpeed.track(end("a", "red", 0.9), end("b", "blue", 0.9), false, speeds))
    }

    @Test
    fun aJunctionEndRunsAtCruise() {
        assertEquals(0.6, LinkSpeed.track(end("a", speed = 0.9), null, false, speeds))
        assertEquals(0.6, LinkSpeed.track(null, end("b", speed = 0.9), false, speeds))
        assertEquals(0.6, LinkSpeed.track(null, null, false, speeds))
    }

    @Test
    fun lineSpeedIsClampedToMaxCruise() {
        assertEquals(1.0, LinkSpeed.track(end("a", speed = 1.4), end("b", speed = 1.4), false, speeds))
    }

    @Test
    fun stoppingInsideAStationRunsAtStationSpeed() {
        assertEquals(0.3, LinkSpeed.track(end("a", speed = 0.9), end("a", speed = 0.9), true, speeds))
    }

    @Test
    fun passingAStationTakesTheSlowerOfThroughAndTheLine() {
        assertEquals(0.55, LinkSpeed.track(end("a", speed = 0.9), end("a", speed = 0.9), false, speeds))
        assertEquals(0.4, LinkSpeed.track(end("a", speed = 0.4), end("a", speed = 0.4), false, speeds))
        assertEquals(0.55, LinkSpeed.track(end("a"), end("a"), false, speeds))
    }

    @Test
    fun everythingIsCappedByMax() {
        val low = speeds.copy(max = 0.5)
        assertEquals(0.5, LinkSpeed.track(end("a", speed = 0.9), end("b", speed = 0.9), false, low))
        assertEquals(0.5, LinkSpeed.track(null, null, false, low))
        assertEquals(0.5, LinkSpeed.branch(0.8, low))
        assertEquals(0.5, LinkSpeed.turn(low))
    }

    @Test
    fun branchesUseTheJunctionSpeedOrTheBranchSpeed() {
        assertEquals(0.45, LinkSpeed.branch(0.45, speeds))
        assertEquals(0.3, LinkSpeed.branch(null, speeds))
    }

    @Test
    fun aSwitchZoneTakesTheJunctionSpeedOnlyAtASwitch() {
        assertEquals(0.45, LinkSpeed.switchZone(true, 0.45, speeds))
        assertEquals(0.3, LinkSpeed.switchZone(true, null, speeds))
        assertEquals(null, LinkSpeed.switchZone(false, 0.45, speeds))
        assertEquals(0.5, LinkSpeed.switchZone(true, 0.8, speeds.copy(max = 0.5)))
    }

    @Test
    fun aPathArrivingAcrossTheAxisIsSideways() {
        val ramp = listOf(railAt(0, z = -3), railAt(0, z = -2), railAt(0, z = -1), railAt(0, z = 0))
        assertTrue(LinkSpeed.sideways(ramp, "x"))
        assertFalse(LinkSpeed.sideways(ramp, "z"))
        val along = listOf(railAt(-3, z = 0), railAt(-2, z = 0), railAt(-1, z = 0), railAt(0, z = 0))
        assertFalse(LinkSpeed.sideways(along, "x"))
        assertFalse(LinkSpeed.sideways(listOf(railAt(0, z = 0)), "x"))
    }
}
