package net.craftgame.metro.route

import net.craftgame.metro.data.Junction
import net.craftgame.metro.geo.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JunctionGeometryTest {
    @Test
    fun splitAndJoinNeedOneOpposedPairPlusOneSide() {
        val x = JunctionGeometry.axes(Junction.Kind.SPLIT, xPair = true, xCount = 2, zPair = false, zCount = 1)
        assertEquals(listOf("x"), x)
        val z = JunctionGeometry.axes(Junction.Kind.JOIN, xPair = false, xCount = 1, zPair = true, zCount = 2)
        assertEquals(listOf("z"), z)
    }

    @Test
    fun splitRejectsShapesThatAreNotAThreeWaySwitch() {
        assertNull(JunctionGeometry.axes(Junction.Kind.SPLIT, xPair = true, xCount = 2, zPair = false, zCount = 0))
        assertNull(JunctionGeometry.axes(Junction.Kind.SPLIT, xPair = true, xCount = 3, zPair = false, zCount = 1))
        // Clamped: two rails on one side are not a straight path.
        assertNull(JunctionGeometry.axes(Junction.Kind.SPLIT, xPair = false, xCount = 2, zPair = false, zCount = 1))
        // A crossing is not a split.
        assertNull(JunctionGeometry.axes(Junction.Kind.SPLIT, xPair = true, xCount = 2, zPair = true, zCount = 2))
    }

    @Test
    fun crossNeedsTwoOpposedPairs() {
        val both = JunctionGeometry.axes(Junction.Kind.CROSS, xPair = true, xCount = 2, zPair = true, zCount = 2)
        assertEquals(listOf("x", "z"), both)
        assertNull(JunctionGeometry.axes(Junction.Kind.CROSS, xPair = true, xCount = 2, zPair = false, zCount = 1))
    }

    @Test
    fun axisOfFollowsTheLargerHorizontalComponent() {
        assertEquals("x", JunctionGeometry.axisOf(Vec3(1.0, 0.0, 0.0)))
        assertEquals("x", JunctionGeometry.axisOf(Vec3(-1.0, 0.0, 0.0)))
        assertEquals("z", JunctionGeometry.axisOf(Vec3(0.0, 0.0, -1.0)))
        assertEquals("x", JunctionGeometry.axisOf(Vec3(0.9, 0.0, 0.2)))
    }

    private fun path(vararg xz: Pair<Int, Int>): List<Vec3> = xz.map { (x, z) -> block(x, z) }

    private fun block(
        x: Int,
        z: Int,
    ) = Vec3(x.toDouble(), 64.0, z.toDouble())

    private val east = Vec3(1.0, 0.0, 0.0)

    @Test
    fun aBranchGoingOnWithTheTravelDoesNotTurnBack() {
        val forward = path(0 to 0, 0 to 1, 1 to 1, 1 to 2, 2 to 2)
        assertFalse(JunctionGeometry.turnsBack(forward, east))
    }

    @Test
    fun aBranchMarkedAgainstTheTravelTurnsBack() {
        val backward = path(0 to 0, 0 to 1, -1 to 1, -1 to 2, -2 to 2)
        assertTrue(JunctionGeometry.turnsBack(backward, east))
    }

    @Test
    fun aBranchSquareToTheTrackOrTooShortDoesNotTurnBack() {
        assertFalse(JunctionGeometry.turnsBack(path(0 to 0, 0 to 1, 0 to 2, 0 to 3), east))
        assertFalse(JunctionGeometry.turnsBack(path(0 to 0), east))
        // The turn comes only after the lookahead.
        val late = path(0 to 0, 0 to 1, 0 to 2, 0 to 3, -1 to 3)
        assertFalse(JunctionGeometry.turnsBack(late, east, lookahead = 3))
        assertTrue(JunctionGeometry.turnsBack(late, east, lookahead = 4))
    }
}
