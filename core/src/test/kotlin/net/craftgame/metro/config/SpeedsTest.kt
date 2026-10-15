package net.craftgame.metro.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpeedsTest {
    private fun validate(speeds: Speeds): Pair<Speeds, List<String>> {
        val warnings = ArrayList<String>()
        return speeds.validated { warnings += it } to warnings
    }

    @Test
    fun defaultsAreValidAndWarnNothing() {
        val (speeds, warnings) = validate(Speeds())
        assertEquals(Speeds(), speeds)
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun maxIsClampedToTheVanillaLimit() {
        val (speeds, warnings) = validate(Speeds(max = 3.0, maxCruise = 3.0, cruise = 2.0))
        assertEquals(Speeds.VANILLA_LIMIT, speeds.max)
        assertEquals(Speeds.VANILLA_LIMIT, speeds.maxCruise)
        assertEquals(Speeds.VANILLA_LIMIT, speeds.cruise)
        assertEquals(3, warnings.size)
    }

    @Test
    fun nonPositiveMaxFallsBackToTheDefault() {
        val (speeds, warnings) = validate(Speeds(max = 0.0))
        assertEquals(Speeds.DEFAULT_MAX, speeds.max)
        assertEquals(1, warnings.size)
    }

    @Test
    fun cruiseStaysWithinMaxCruiseWithinMax() {
        val (speeds, warnings) = validate(Speeds(max = 0.8, maxCruise = 1.0, cruise = 0.9))
        assertEquals(0.8, speeds.maxCruise)
        assertEquals(0.8, speeds.cruise)
        assertEquals(2, warnings.size)
    }

    @Test
    fun otherSpeedsArePositiveAndCappedByMax() {
        val input = Speeds(max = 0.5, maxCruise = 0.5, cruise = 0.5, station = -1.0, through = 0.55, turn = 2.0)
        val (speeds, warnings) = validate(input)
        assertEquals(Speeds.DEFAULT_STATION, speeds.station)
        assertEquals(0.5, speeds.through)
        assertEquals(0.5, speeds.turn)
        assertEquals(Speeds.DEFAULT_BRANCH, speeds.branch)
        assertEquals(3, warnings.size)
    }

    @Test
    fun speedsBelowTheMinimumOrNotANumberFallBackToTheDefaults() {
        val input = Speeds(max = Double.NaN, station = Speeds.MIN / 2, through = Double.POSITIVE_INFINITY)
        val (speeds, warnings) = validate(input)
        assertEquals(Speeds.DEFAULT_MAX, speeds.max)
        assertEquals(Speeds.DEFAULT_STATION, speeds.station)
        assertEquals(Speeds.DEFAULT_THROUGH, speeds.through)
        assertEquals(3, warnings.size)
    }

    @Test
    fun validSpeedsAreFiniteAndAtLeastTheMinimum() {
        assertTrue(Speeds.valid(Speeds.MIN))
        assertTrue(Speeds.valid(1.0))
        assertFalse(Speeds.valid(Speeds.MIN / 2))
        assertFalse(Speeds.valid(0.0))
        assertFalse(Speeds.valid(Double.NaN))
        assertFalse(Speeds.valid(Double.POSITIVE_INFINITY))
    }
}
