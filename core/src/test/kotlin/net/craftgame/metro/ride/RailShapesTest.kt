package net.craftgame.metro.ride

import net.craftgame.metro.at
import net.craftgame.metro.rail.RailShape
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RailShapesTest {
    @Test
    fun directionFromOffsets() {
        assertEquals(RailShapes.Dir.EAST, RailShapes.dirOf(at(0), at(1)))
        assertEquals(RailShapes.Dir.WEST, RailShapes.dirOf(at(0), at(-1)))
        assertEquals(RailShapes.Dir.SOUTH, RailShapes.dirOf(at(0), at(0, z = 1)))
        assertEquals(RailShapes.Dir.NORTH, RailShapes.dirOf(at(0), at(0, z = -1)))
        assertNull(RailShapes.dirOf(at(0), at(0)))
    }

    @Test
    fun flatShapes() {
        assertEquals(RailShape.EAST_WEST, RailShapes.shapeFor(RailShapes.Dir.EAST, RailShapes.Dir.WEST))
        assertEquals(RailShape.NORTH_SOUTH, RailShapes.shapeFor(RailShapes.Dir.NORTH, RailShapes.Dir.SOUTH))
        assertEquals(RailShape.NORTH_EAST, RailShapes.shapeFor(RailShapes.Dir.NORTH, RailShapes.Dir.EAST))
        assertEquals(RailShape.SOUTH_WEST, RailShapes.shapeFor(RailShapes.Dir.SOUTH, RailShapes.Dir.WEST))
        assertNull(RailShapes.shapeFor(RailShapes.Dir.EAST, RailShapes.Dir.EAST))
    }

    @Test
    fun ascendingShapes() {
        assertEquals(RailShape.ASCENDING_EAST, RailShapes.ascending(RailShapes.Dir.EAST))
        assertEquals(RailShape.ASCENDING_WEST, RailShapes.ascending(RailShapes.Dir.WEST))
        assertEquals(RailShape.ASCENDING_NORTH, RailShapes.ascending(RailShapes.Dir.NORTH))
        assertEquals(RailShape.ASCENDING_SOUTH, RailShapes.ascending(RailShapes.Dir.SOUTH))
    }

    @Test
    fun straightFlatSectionStaysFlat() {
        assertEquals(RailShape.EAST_WEST, RailShapes.shapeAt(at(0), at(1), at(2)))
    }

    @Test
    fun flatTurnBecomesCurve() {
        assertEquals(RailShape.SOUTH_WEST, RailShapes.shapeAt(at(0), at(1), at(1, z = 1)))
    }

    @Test
    fun risingBlockAscendsTowardTheHigherNeighbour() {
        assertEquals(RailShape.ASCENDING_EAST, RailShapes.shapeAt(at(0), at(1), at(2, y = 1)))
    }

    @Test
    fun lowerBlockOfADescentAscendsBack() {
        assertEquals(RailShape.ASCENDING_WEST, RailShapes.shapeAt(at(1, y = 1), at(2), at(3)))
    }

    @Test
    fun topOfASlopeIsFlat() {
        assertEquals(RailShape.EAST_WEST, RailShapes.shapeAt(at(1), at(2, y = 1), at(3, y = 1)))
    }
}
