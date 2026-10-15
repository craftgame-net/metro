package net.craftgame.metro.debug

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RateLimiterTest {
    private var now = 100L
    private val limiter = RateLimiter(2, 1000L) { now }

    @Test
    fun dropsEventsOverTheLimitInAWindow() {
        assertTrue(limiter.admit())
        assertTrue(limiter.admit())
        assertFalse(limiter.admit())
        assertFalse(limiter.admit())
        assertEquals(2, limiter.takeDropped())
        assertEquals(0, limiter.takeDropped())
    }

    @Test
    fun admitsAgainInTheNextWindow() {
        limiter.admit()
        limiter.admit()
        now += 999
        assertFalse(limiter.admit())
        now += 1
        assertTrue(limiter.admit())
    }
}
