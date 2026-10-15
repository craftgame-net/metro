package net.craftgame.metro.rail

import net.craftgame.metro.geo.BlockPos
import net.craftgame.metro.geo.Cardinal
import net.craftgame.metro.railAt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RailPathfinderTest {
    private fun block(
        x: Int,
        z: Int = 0,
        y: Int = 64,
    ) = BlockPos(x, y, z)

    /** Neighbours on a track from x = 0 to [last]. */
    private fun line(last: Int): (BlockPos) -> List<BlockPos> =
        { current -> listOf(current.x - 1, current.x + 1).filter { it in 0..last }.map { block(it) } }

    private val endless: (BlockPos) -> List<BlockPos> = { current -> listOf(block(current.x + 1)) }

    private fun bfs(
        goal: Int,
        maxNodes: Int,
        neighbours: (BlockPos) -> List<BlockPos>,
    ) = RailPathfinder.bfs(block(0), setOf(block(goal))::contains, maxNodes, HashMap(), neighbours)

    @Test
    fun bfsFindsTheTargetWithinTheLimitAndRecordsTheWayBack() {
        val previous = HashMap<BlockPos, BlockPos>()
        val result = RailPathfinder.bfs(block(0), setOf(block(10))::contains, 100, previous, line(20))
        assertEquals(block(10), result.goal)
        assertFalse(result.limitReached)
        assertEquals(block(9), previous[block(10)])
    }

    @Test
    fun railsEndingIsNotTheLimit() {
        val result = bfs(goal = 50, maxNodes = 100, line(20))
        assertNull(result.goal)
        assertFalse(result.limitReached)
    }

    @Test
    fun givingUpIsTheLimit() {
        val result = bfs(goal = 50, maxNodes = 10, endless)
        assertNull(result.goal)
        assertTrue(result.limitReached)
    }

    @Test
    fun theLimitCountsVisitedBlocks() {
        // Blocks 0..9 are visited within a limit of 10; block 10 would be the eleventh.
        assertTrue(bfs(goal = 10, maxNodes = 10, endless).limitReached)
        assertEquals(block(9), bfs(goal = 9, maxNodes = 10, endless).goal)
        // Track 0..9 has ten blocks: the search visits them all and the rails end.
        assertFalse(bfs(goal = 50, maxNodes = 10, line(9)).limitReached)
    }

    @Test
    fun findPathFollowsTheRailsBlockByBlock() {
        val rails = FakeRails().alongX(0, 5)

        val path = RailPathfinder.findPath(rails.world, railAt(0), railAt(5), maxNodes = 100).path

        assertEquals((0..5).map { railAt(it) }, path)
    }

    @Test
    fun aStepUpNeedsASlope() {
        val rails = FakeRails().alongX(0, 1).alongX(2, 2, y = 65)

        assertEquals(listOf(block(0)), RailPathfinder.connections(rails.world, block(1)))
        rails.rail(1, 64, 0, RailShape.ASCENDING_EAST)
        assertEquals(setOf(block(0), block(2, y = 65)), RailPathfinder.connections(rails.world, block(1)).toSet())
    }

    @Test
    fun aPathCrossesAHillOnItsSlopes() {
        val rails =
            FakeRails()
                .alongX(0, 2)
                .rail(3, 64, 0, RailShape.ASCENDING_EAST)
                .alongX(4, 5, y = 65)
                .rail(6, 64, 0, RailShape.ASCENDING_WEST)
                .alongX(7, 9)

        val path = RailPathfinder.findPath(rails.world, railAt(0), railAt(9), maxNodes = 100).path

        val heights = listOf(64, 64, 64, 64, 65, 65, 64, 64, 64, 64)
        assertEquals((0..9).map { railAt(it, y = heights[it]) }, path)
    }

    @Test
    fun aSlopeThatPointsTheWrongWayBlocksTheHill() {
        val rails =
            FakeRails()
                .alongX(0, 2)
                .rail(3, 64, 0, RailShape.ASCENDING_WEST)
                .alongX(4, 5, y = 65)

        val result = RailPathfinder.findPath(rails.world, railAt(0), railAt(5, y = 65), maxNodes = 100)

        assertNull(result.path)
        assertFalse(result.limitReached)
    }

    @Test
    fun aGapInTheTrackIsADeadEndNotTheLimit() {
        val rails = FakeRails().alongX(0, 4).alongX(6, 9)

        val result = RailPathfinder.findPath(rails.world, railAt(0), railAt(9), maxNodes = 100)

        assertNull(result.path)
        assertFalse(result.limitReached)
    }

    @Test
    fun noRailAtEitherEndFindsNothing() {
        val rails = FakeRails().alongX(0, 4)

        assertNull(RailPathfinder.findPath(rails.world, railAt(0, z = 5), railAt(4), maxNodes = 100).path)
        assertNull(RailPathfinder.findPath(rails.world, railAt(0), railAt(4, z = 5), maxNodes = 100).path)
    }

    @Test
    fun aLongTrackHitsTheLimit() {
        val rails = FakeRails().alongX(0, 50)

        val result = RailPathfinder.findPath(rails.world, railAt(0), railAt(50), maxNodes = 20)

        assertNull(result.path)
        assertTrue(result.limitReached)
    }

    @Test
    fun theNearestRailIsTheBlockItselfThenTheClosestNeighbour() {
        val rails = FakeRails().alongX(0, 4).rail(8, 65, 1, RailShape.EAST_WEST)

        assertEquals(block(2), RailPathfinder.nearestRail(rails.world, railAt(2)))
        assertEquals(block(2), RailPathfinder.nearestRail(rails.world, railAt(2, y = 65, z = 1)))
        assertEquals(block(8, z = 1, y = 65), RailPathfinder.nearestRail(rails.world, railAt(7)))
        assertNull(RailPathfinder.nearestRail(rails.world, railAt(2, z = 2)))
        assertEquals(block(2), RailPathfinder.nearestRail(rails.world, railAt(2, z = 2), radius = 2))
    }

    @Test
    fun theCentreIsOnTopOfTheRail() {
        assertEquals(railAt(3, y = 70, z = -2), RailPathfinder.center(FakeRails().world, block(3, z = -2, y = 70)))
    }

    /** A square loop with corners at x and z 0 and 4. */
    private fun loop(): FakeRails =
        FakeRails()
            .alongX(0, 4)
            .alongX(0, 4, z = 4)
            .alongZ(0, 1, 3)
            .alongZ(4, 1, 3)

    @Test
    fun aHeadingTakesTheWayAheadOnALoop() {
        val rails = loop()
        val goal = setOf(block(2, z = 4))

        val east = RailPathfinder.Departure(Cardinal.EAST.direction)
        val eastPath = RailPathfinder.findNearest(rails.world, railAt(2), goal, east, maxNodes = 100).path
        assertEquals(railAt(3), eastPath?.get(1))
        assertEquals(9, eastPath?.size)

        val west = RailPathfinder.Departure(Cardinal.WEST.direction)
        val westPath = RailPathfinder.findNearest(rails.world, railAt(2), goal, west, maxNodes = 100).path
        assertEquals(railAt(1), westPath?.get(1))
    }

    @Test
    fun withNothingAheadTheFirstStepGoesToTheSideButNeverBack() {
        // Track along x; the search starts on it heading south, where there is no rail.
        val rails = FakeRails().alongX(0, 9).alongZ(5, -3, -1)
        val south = RailPathfinder.Departure(Cardinal.SOUTH.direction)
        val sideways = RailPathfinder.findNearest(rails.world, railAt(5), setOf(block(9)), south, maxNodes = 100)
        assertEquals(railAt(9), sideways.path?.last())

        val behind = setOf(block(5, z = -3))
        assertNull(RailPathfinder.findNearest(rails.world, railAt(5), behind, south, maxNodes = 100).path)
    }

    @Test
    fun straightOnlyNeverStepsToTheSide() {
        val rails = FakeRails().alongX(0, 9)
        val departure = RailPathfinder.Departure(Cardinal.SOUTH.direction, straightOnly = true)

        val result = RailPathfinder.findNearest(rails.world, railAt(5), setOf(block(9)), departure, maxNodes = 100)

        assertNull(result.path)
        assertFalse(result.limitReached)
    }

    @Test
    fun skipStartLooksPastTheStartBlock() {
        val rails = FakeRails().alongX(0, 9)
        val goals = setOf(block(5), block(8))

        val here = RailPathfinder.findNearest(rails.world, railAt(5), goals, maxNodes = 100).path
        assertEquals(listOf(railAt(5)), here)

        val skip = RailPathfinder.Departure(Cardinal.EAST.direction, skipStart = true)
        val next = RailPathfinder.findNearest(rails.world, railAt(5), goals, skip, maxNodes = 100).path
        assertEquals((5..8).map { railAt(it) }, next)
    }
}
