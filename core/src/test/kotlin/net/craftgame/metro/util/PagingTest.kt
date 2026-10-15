package net.craftgame.metro.util

import kotlin.test.Test
import kotlin.test.assertEquals

class PagingTest {
    @Test
    fun pagesRoundUpAndNeverDropBelowOne() {
        assertEquals(1, Paging.pages(0, 10))
        assertEquals(1, Paging.pages(10, 10))
        assertEquals(2, Paging.pages(11, 10))
        assertEquals(3, Paging.pages(25, 10))
    }

    @Test
    fun slicesAPageAndClampsOutOfRangePages() {
        val items = (1..25).toList()
        assertEquals((1..10).toList(), Paging.slice(items, 1, 10))
        assertEquals((21..25).toList(), Paging.slice(items, 3, 10))
        assertEquals((21..25).toList(), Paging.slice(items, 9, 10))
        assertEquals((1..10).toList(), Paging.slice(items, 0, 10))
        assertEquals(emptyList(), Paging.slice(emptyList<Int>(), 2, 10))
    }

    @Test
    fun clampKeepsPagesInRange() {
        assertEquals(1, Paging.clamp(-3, 25, 10))
        assertEquals(3, Paging.clamp(7, 25, 10))
        assertEquals(2, Paging.clamp(2, 25, 10))
    }
}
