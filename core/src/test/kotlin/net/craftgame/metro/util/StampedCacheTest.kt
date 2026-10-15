package net.craftgame.metro.util

import kotlin.test.Test
import kotlin.test.assertEquals

class StampedCacheTest {
    @Test
    fun reusesValuesUntilTheStampChanges() {
        val cache = StampedCache<String, Int>()
        var computed = 0
        val compute = { ++computed }

        assertEquals(1, cache.get("a", 1L to 0L, compute))
        assertEquals(1, cache.get("a", 1L to 0L, compute))
        assertEquals(2, cache.get("b", 1L to 0L, compute))
        assertEquals(3, cache.get("a", 2L to 0L, compute))
        assertEquals(4, cache.get("b", 2L to 0L, compute))
        assertEquals(5, cache.get("a", 2L to 1L, compute))
    }
}
