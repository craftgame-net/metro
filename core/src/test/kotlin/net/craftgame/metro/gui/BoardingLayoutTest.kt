package net.craftgame.metro.gui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BoardingLayoutTest {
    @Test
    fun aMenuThatFitsIsUnpagedWithTheExitLast() {
        assertFalse(BoardingLayout.paged(53))
        assertEquals(1, BoardingLayout.pages(53))
        assertEquals(54, BoardingLayout.size(53))
        assertEquals(53, BoardingLayout.exitSlot(53))
        assertEquals(9, BoardingLayout.size(0))
        assertEquals(18, BoardingLayout.size(9))
        assertEquals(17, BoardingLayout.exitSlot(9))
    }

    @Test
    fun aMenuThatDoesNotFitHasFortyFiveEntriesAPage() {
        assertTrue(BoardingLayout.paged(54))
        assertEquals(2, BoardingLayout.pages(54))
        assertEquals(2, BoardingLayout.pages(90))
        assertEquals(3, BoardingLayout.pages(91))
        assertEquals(54, BoardingLayout.size(100))
    }

    @Test
    fun theControlRowHasPreviousExitAndNext() {
        assertEquals(45, BoardingLayout.PREVIOUS_SLOT)
        assertEquals(49, BoardingLayout.EXIT_SLOT)
        assertEquals(53, BoardingLayout.NEXT_SLOT)
        assertEquals(49, BoardingLayout.exitSlot(60))
    }
}
