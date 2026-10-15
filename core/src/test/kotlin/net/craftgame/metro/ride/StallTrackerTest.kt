package net.craftgame.metro.ride

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StallTrackerTest {
    @Test
    fun aRideThatKeepsMovingNeverStalls() {
        val tracker = StallTracker(limitTicks = 5, minProgress = 0.5)
        repeat(100) { assertFalse(tracker.update(it * 0.2)) }
    }

    @Test
    fun aRideStuckInPlaceStallsAfterTheLimit() {
        val tracker = StallTracker(limitTicks = 5, minProgress = 0.5)
        assertFalse(tracker.update(10.0))
        repeat(4) { assertFalse(tracker.update(10.1)) }
        assertTrue(tracker.update(10.2))
    }

    @Test
    fun progressAndResetStartTheCountOver() {
        val tracker = StallTracker(limitTicks = 3, minProgress = 0.5)
        tracker.update(0.0)
        tracker.update(0.0)
        tracker.update(0.0)
        assertFalse(tracker.update(1.0))
        assertFalse(tracker.update(1.0))
        assertFalse(tracker.update(1.0))
        tracker.reset()
        assertFalse(tracker.update(1.0))
        assertFalse(tracker.update(1.0))
        assertFalse(tracker.update(1.0))
        assertTrue(tracker.update(1.0))
    }
}
