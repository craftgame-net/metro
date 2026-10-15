package net.craftgame.metro.rail

import net.craftgame.metro.rail.RailShape
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RailSlopesTest {
    @Test
    fun levelNeighboursConnectWhateverTheirShape() {
        assertTrue(RailSlopes.connects(1, 0, 0, RailShape.EAST_WEST, RailShape.EAST_WEST))
        assertTrue(RailSlopes.connects(0, 0, 1, RailShape.EAST_WEST, RailShape.NORTH_SOUTH))
        assertTrue(RailSlopes.connects(-1, 0, 0, RailShape.NORTH_EAST, RailShape.SOUTH_WEST))
    }

    @Test
    fun aSlopeConnectsUpTheWayItRises() {
        assertTrue(RailSlopes.connects(1, 1, 0, RailShape.ASCENDING_EAST, RailShape.EAST_WEST))
        assertTrue(RailSlopes.connects(0, 1, -1, RailShape.ASCENDING_NORTH, RailShape.NORTH_SOUTH))
        assertFalse(RailSlopes.connects(-1, 1, 0, RailShape.ASCENDING_EAST, RailShape.EAST_WEST))
        assertFalse(RailSlopes.connects(0, 1, 1, RailShape.ASCENDING_EAST, RailShape.NORTH_SOUTH))
    }

    @Test
    fun aSlopeBelowConnectsWhenItRisesTowardsUs() {
        // Coming down: the lower rail one block west ascends east, up to us.
        assertTrue(RailSlopes.connects(-1, -1, 0, RailShape.EAST_WEST, RailShape.ASCENDING_EAST))
        assertFalse(RailSlopes.connects(-1, -1, 0, RailShape.EAST_WEST, RailShape.ASCENDING_WEST))
        assertFalse(RailSlopes.connects(1, -1, 0, RailShape.ASCENDING_EAST, RailShape.ASCENDING_EAST))
    }

    @Test
    fun aParallelTrackOneBlockHigherOrLowerIsNotConnected() {
        assertFalse(RailSlopes.connects(0, 1, 1, RailShape.EAST_WEST, RailShape.EAST_WEST))
        assertFalse(RailSlopes.connects(0, -1, 1, RailShape.EAST_WEST, RailShape.EAST_WEST))
        assertFalse(RailSlopes.connects(1, 1, 0, RailShape.EAST_WEST, RailShape.EAST_WEST))
        assertFalse(RailSlopes.connects(1, 1, 0, null, RailShape.EAST_WEST))
    }
}
