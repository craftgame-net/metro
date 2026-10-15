package net.craftgame.metro.edit

import kotlin.test.Test
import kotlin.test.assertEquals

class MenuLayoutTest {
    @Test
    fun fewButtonsAreSpacedAroundTheCentre() {
        assertEquals(listOf(4), MenuLayout.row(1))
        assertEquals(listOf(3, 5), MenuLayout.row(2))
        assertEquals(listOf(2, 4, 6), MenuLayout.row(3))
        assertEquals(listOf(1, 3, 5, 7), MenuLayout.row(4))
    }

    @Test
    fun manyButtonsSitSideBySideAwayFromHeaderAndBack() {
        assertEquals(listOf(2, 3, 4, 5, 6), MenuLayout.row(5))
        assertEquals(listOf(1, 2, 3, 4, 5, 6), MenuLayout.row(6))
        assertEquals((1..7).toList(), MenuLayout.row(7))
    }

    @Test
    fun listsHaveEntriesThenAnEmptyRowThenControls() {
        assertEquals(27, MenuLayout.listSize(0))
        assertEquals(27, MenuLayout.listSize(9))
        assertEquals(36, MenuLayout.listSize(10))
        assertEquals(9, MenuLayout.listCapacity(27))
    }

    @Test
    fun overflowingListsShrinkEntriesAndKeepControls() {
        assertEquals(54, MenuLayout.listSize(100))
        assertEquals(36, MenuLayout.listCapacity(54))
        assertEquals(53, MenuLayout.control(54, MenuLayout.Control.BACK))
    }

    @Test
    fun listControlsSitAtFixedColumns() {
        assertEquals(21, MenuLayout.control(27, MenuLayout.Control.CREATE))
        assertEquals(23, MenuLayout.control(27, MenuLayout.Control.SETTINGS))
        assertEquals(26, MenuLayout.control(27, MenuLayout.Control.BACK))
        assertEquals(22, MenuLayout.control(27, MenuLayout.Control.CREATE, alone = true))
    }

    @Test
    fun actionsAndAnchorMenusUseExplicitSlots() {
        assertEquals(27, MenuLayout.Actions.SIZE)
        assertEquals((0..6).toList(), MenuLayout.Actions.ANCHORS)
        assertEquals(8, MenuLayout.Actions.CREATE_JUNCTION)
        assertEquals(listOf(18, 19, 20, 22, 24, 26), MenuLayout.Actions.NAVIGATION)
        assertEquals(listOf(2, 4, 6, 8), MenuLayout.AnchorPoint.BUTTONS)
        assertEquals(listOf(0, 1, 2), MenuLayout.JunctionPoint.KINDS)
        assertEquals(listOf(4, 5), MenuLayout.JunctionPoint.SETTINGS)
        assertEquals(8, MenuLayout.JunctionPoint.DELETE)
        assertEquals(listOf(0, 2, 4, 6), MenuLayout.LineSettings.BUTTONS)
    }
}
