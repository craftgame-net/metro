package net.craftgame.metro.rail

import net.craftgame.metro.at
import net.craftgame.metro.edge
import net.craftgame.metro.geo.Point
import net.craftgame.metro.railAt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SwitchMarksTest {
    private val branchAtTwo = setOf(at(2, z = 1))

    private fun line(): List<Point> = (0..4).map { at(it) }

    private fun neighboursOn(rails: Set<Point>): (Point) -> List<Point> =
        { point ->
            rails.filter { other ->
                val dx = Math.abs(other.blockX - point.blockX)
                val dz = Math.abs(other.blockZ - point.blockZ)
                dx + dz == 1 && Math.abs(other.blockY - point.blockY) <= 1
            }
        }

    @Test
    fun plainStraightRailsAreNoCandidatesButOpenEndsAre() {
        val rails = line().toSet()
        val marks = SwitchMarks.candidates(line(), neighboursOn(rails)) { RailShape.EAST_WEST }
        // Both ends are dead ends: their other side is unknown, so they stay candidates.
        assertEquals(listOf(0, 4), marks)
    }

    @Test
    fun aRailWithThreeNeighboursIsAlwaysACandidate() {
        val path = line().subList(1, 4)
        val rails = line().toSet() + branchAtTwo
        val marks = SwitchMarks.candidates(path, neighboursOn(rails)) { RailShape.EAST_WEST }
        assertEquals(listOf(1), marks)
    }

    @Test
    fun aPlainRailInTheWrongShapeIsACandidate() {
        val path = line().subList(1, 4)
        val rails = line().toSet()
        val bent: (Point) -> RailShape = { if (it.blockX == 2) RailShape.NORTH_SOUTH else RailShape.EAST_WEST }
        val marks = SwitchMarks.candidates(path, neighboursOn(rails), bent)
        assertEquals(listOf(1), marks)
    }

    @Test
    fun anEndBetweenTwoRailsItJoinsIsNoCandidate() {
        val path = line().subList(1, 3)
        val rails = line().toSet()
        val marks = SwitchMarks.candidates(path, neighboursOn(rails)) { RailShape.EAST_WEST }
        assertEquals(emptyList(), marks)
    }

    @Test
    fun anEndOnASwitchIsACandidate() {
        val path = line().subList(0, 3)
        val rails = line().toSet() + branchAtTwo
        val marks = SwitchMarks.candidates(path, neighboursOn(rails)) { RailShape.EAST_WEST }
        assertEquals(listOf(0, 2), marks)
    }

    @Test
    fun blocksThatAreNoRailAreSkipped() {
        val rails = line().toSet() + branchAtTwo
        val marks = SwitchMarks.candidates(line(), neighboursOn(rails)) { null }
        assertEquals(emptyList(), marks)
    }

    /** Rails from x = -1 to 5 with a side rail at x = 2, so only that block is a switch on the path 0..4. */
    private fun railsWithASwitch(): FakeRails = FakeRails().alongX(-1, 5).alongZ(2, 1, 1)

    private val railPath = (0..4).map { railAt(it) }

    @Test
    fun readFindsTheSwitchOnLoadedRails() {
        val rails = railsWithASwitch()

        assertEquals(listOf(2), SwitchMarks.read(rails, railPath))
        assertNull(SwitchMarks.read(rails, emptyList()))
        assertNull(SwitchMarks.read(FakeRails(name = "nether"), railPath))
    }

    @Test
    fun storedKeepsTheSwitchAsAPointOfTheCompressedPath() {
        val stored = SwitchMarks.stored(railsWithASwitch(), railPath)

        assertEquals(listOf(railAt(0), railAt(2), railAt(4)), stored.points)
        assertEquals(listOf(1), stored.marks)
    }

    @Test
    fun remarkReplacesStaleMarksAndCompressesThePoints() {
        val edge = edge("e", "a", "b", points = railPath)
        edge.switches = null

        SwitchMarks.remark(railsWithASwitch(), edge)

        assertEquals(listOf(railAt(0), railAt(2), railAt(4)), edge.points)
        assertEquals(listOf(1), edge.switches)

        SwitchMarks.remark(FakeRails().alongX(-1, 5), edge)

        assertEquals(listOf(railAt(0), railAt(4)), edge.points)
        assertEquals(emptyList(), edge.switches)
    }
}
