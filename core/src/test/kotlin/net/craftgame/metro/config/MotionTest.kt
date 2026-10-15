package net.craftgame.metro.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MotionTest {
    private fun validate(
        motion: Motion,
        maxSpeed: Double = Speeds.DEFAULT_MAX,
    ): Pair<Motion, List<String>> {
        val warnings = ArrayList<String>()
        return motion.validated(maxSpeed) { warnings += it } to warnings
    }

    @Test
    fun defaultsAreValidAndWarnNothing() {
        val (motion, warnings) = validate(Motion())
        assertEquals(Motion(), motion)
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun zeroNegativeOrNonFiniteRatesFallBackToTheDefaults() {
        val (motion, warnings) = validate(Motion(acceleration = 0.0, deceleration = -0.1))
        assertEquals(Motion.DEFAULT_ACCELERATION, motion.acceleration)
        assertEquals(Motion.DEFAULT_DECELERATION, motion.deceleration)
        assertEquals(2, warnings.size)

        val (odd, oddWarnings) = validate(Motion(acceleration = Double.NaN, deceleration = Double.POSITIVE_INFINITY))
        assertEquals(Motion.DEFAULT_ACCELERATION, odd.acceleration)
        assertEquals(Motion.DEFAULT_DECELERATION, odd.deceleration)
        assertEquals(2, oddWarnings.size)
    }

    @Test
    fun ratesAreCappedBySpeedMax() {
        val (motion, warnings) = validate(Motion(acceleration = 5.0, deceleration = 0.5), maxSpeed = 0.8)
        assertEquals(0.8, motion.acceleration)
        assertEquals(0.5, motion.deceleration)
        assertEquals(1, warnings.size)
    }

    @Test
    fun slowZonesAreClampedToTheirRange() {
        val (motion, warnings) = validate(Motion(slowLead = -1.0, slowTrail = 100.0))
        assertEquals(0.0, motion.slowLead)
        assertEquals(Motion.MAX_SLOW_ZONE, motion.slowTrail)
        assertEquals(2, warnings.size)

        val (nan, nanWarnings) = validate(Motion(slowLead = Double.NaN))
        assertEquals(Motion.DEFAULT_SLOW_LEAD, nan.slowLead)
        assertEquals(1, nanWarnings.size)
    }

    @Test
    fun slopeSpeedMustBeAtLeastTheMinimumAndAtMostSpeedMax() {
        for (bad in listOf(0.0, Speeds.MIN / 2, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            val (motion, warnings) = validate(Motion(slopeSpeed = bad))
            assertEquals(Motion.DEFAULT_SLOPE_SPEED, motion.slopeSpeed, "for $bad")
            assertEquals(1, warnings.size, "for $bad")
        }
        for (good in listOf(Speeds.MIN, 0.3, Speeds.DEFAULT_MAX)) {
            val (motion, warnings) = validate(Motion(slopeSpeed = good))
            assertEquals(good, motion.slopeSpeed)
            assertTrue(warnings.isEmpty())
        }
        val (capped, warnings) = validate(Motion(slopeSpeed = 1.0), maxSpeed = 0.8)
        assertEquals(0.8, capped.slopeSpeed)
        assertEquals(1, warnings.size)
        assertEquals(0.5, validate(Motion(), maxSpeed = 0.5).first.slopeSpeed)
    }

    @Test
    fun slopeZonesAreClampedToTheirRange() {
        val (motion, warnings) = validate(Motion(slopeLead = -1.0, slopeTrail = 17.0))
        assertEquals(0.0, motion.slopeLead)
        assertEquals(Motion.MAX_SLOW_ZONE, motion.slopeTrail)
        assertEquals(2, warnings.size)
    }
}
