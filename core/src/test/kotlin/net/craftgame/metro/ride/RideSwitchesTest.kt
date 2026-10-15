package net.craftgame.metro.ride

import net.craftgame.metro.geo.BlockPos
import net.craftgame.metro.geo.Point
import net.craftgame.metro.rail.FakeRails
import net.craftgame.metro.rail.RailShape
import net.craftgame.metro.railAt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RideSwitchesTest {
    /** A path east along z = 0 that turns south at x = 5. Point i is i blocks along it. */
    private val path = (0..5).map { railAt(it) } + (1..3).map { railAt(5, z = it) }

    /** The rails under [path], all straight, so the corner at x = 5 needs reshaping. */
    private fun rails(): FakeRails = FakeRails().alongX(0, 5).alongZ(5, 1, 3)

    private fun FakeRails.corner(): RailShape? = shape(5, 64, 0)

    private fun planned(
        candidates: List<Int> = listOf(5),
        on: List<Point> = path,
    ): RideSwitches = RideSwitches().apply { plan(on, candidates) { it.toDouble() } }

    @Test
    fun setsTheRailNearTheCartAndRestoresItAfterwards() {
        val rails = rails()
        val switches = planned()

        switches.tend(rails, 1.0)
        assertEquals(RailShape.EAST_WEST, rails.corner())
        switches.tend(rails, 4.0)
        assertEquals(RailShape.SOUTH_WEST, rails.corner())
        // Someone flips it while the cart is on it: the ride sets it back.
        rails.rail(5, 64, 0, RailShape.NORTH_SOUTH)
        switches.tend(rails, 5.5)
        assertEquals(RailShape.SOUTH_WEST, rails.corner())
        switches.tend(rails, 8.0)
        assertEquals(RailShape.EAST_WEST, rails.corner())
    }

    @Test
    fun leavesARailThatSomeoneElseChanged() {
        val rails = rails()
        val switches = planned()

        switches.tend(rails, 4.0)
        rails.rail(5, 64, 0, RailShape.NORTH_SOUTH)
        switches.restoreAll(rails)

        assertEquals(RailShape.NORTH_SOUTH, rails.corner())
    }

    @Test
    fun aRailAlreadyInShapeIsLeftAsItIs() {
        val rails = rails().rail(5, 64, 0, RailShape.SOUTH_WEST)
        val switches = planned()

        switches.tend(rails, 4.0)
        switches.tend(rails, 8.0)
        switches.restoreAll(rails)

        assertEquals(RailShape.SOUTH_WEST, rails.corner())
    }

    @Test
    fun restoreAllPutsBackOnlyTheRailsThatWereSet() {
        // A second wrong rail at x = 2, far behind the corner.
        val rails = rails().rail(2, 64, 0, RailShape.NORTH_SOUTH)
        val switches = planned(listOf(2, 5))

        switches.tend(rails, 1.0)
        assertEquals(RailShape.EAST_WEST, rails.shape(2, 64, 0))
        assertEquals(RailShape.EAST_WEST, rails.corner())

        switches.restoreAll(rails)

        assertEquals(RailShape.NORTH_SOUTH, rails.shape(2, 64, 0))
        assertEquals(RailShape.EAST_WEST, rails.corner())
    }

    @Test
    fun switchesAreTendedInRouteOrder() {
        val rails = rails().rail(2, 64, 0, RailShape.NORTH_SOUTH)
        val switches = planned(listOf(5, 2))

        switches.tend(rails, 1.0)
        switches.tend(rails, 4.5)
        assertEquals(RailShape.NORTH_SOUTH, rails.shape(2, 64, 0))
        assertEquals(RailShape.SOUTH_WEST, rails.corner())
        switches.tend(rails, 8.0)
        assertEquals(RailShape.EAST_WEST, rails.corner())
    }

    @Test
    fun theEndsOfThePathAreNeverPlanned() {
        val rails = rails().rail(0, 64, 0, RailShape.NORTH_SOUTH)
        val switches = planned(listOf(0, path.lastIndex))

        switches.tend(rails, 0.0)
        switches.tend(rails, path.lastIndex.toDouble())

        assertEquals(RailShape.NORTH_SOUTH, rails.shape(0, 64, 0))
    }

    @Test
    fun aMissingWorldOrRailIsSkipped() {
        val elsewhere = FakeRails(name = "nether").alongX(0, 5).alongZ(5, 1, 3)
        planned().tend(elsewhere, 5.0)
        assertEquals(RailShape.EAST_WEST, elsewhere.corner())

        val rails = rails()
        rails.shapes.remove(BlockPos(5, 64, 0))
        val switches = planned()
        switches.tend(rails, 5.0)
        switches.restoreAll(rails)
        assertNull(rails.corner())
    }
}
