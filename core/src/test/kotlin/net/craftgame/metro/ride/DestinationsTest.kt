package net.craftgame.metro.ride

import net.craftgame.metro.data.Station
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DestinationsTest {
    private fun dest(
        id: String,
        ticks: Int,
        onLine: Boolean = true,
    ): Destination = Destination(Station(id, id, "world"), "0", ticks, "L", onLine)

    private fun byId(vararg dests: Destination): Map<String, Destination> = dests.associateBy { it.station.id }

    private fun ids(list: List<Destination>): List<String> = list.map { it.station.id }

    private fun pairs(list: List<Hint>) = list.map { it.destination.station.id to it.platform }

    @Test
    fun tieGoesToTheCurrentPlatform() {
        val result = Destinations.assign(mapOf("a" to byId(dest("x", 10)), "b" to byId(dest("x", 10))), "b")
        assertEquals(listOf("x"), ids(result.own))
        assertTrue(result.slower.isEmpty() && result.unavailable.isEmpty())
    }

    @Test
    fun destinationReachableOnlyFromCurrentStaysEvenWhenLong() {
        val result = Destinations.assign(mapOf("a" to byId(dest("x", 900)), "b" to byId()), "a")
        assertEquals(listOf("x"), ids(result.own))
        assertTrue(result.slower.isEmpty() && result.unavailable.isEmpty())
    }

    @Test
    fun shorterRouteElsewhereIsSlowerButKeepsTheRouteFromHere() {
        val result = Destinations.assign(mapOf("a" to byId(dest("x", 500)), "b" to byId(dest("x", 50))), "a")
        assertTrue(result.own.isEmpty())
        assertEquals(listOf("x" to "b"), pairs(result.slower))
        assertEquals(
            500,
            result.slower
                .single()
                .destination.ticks,
        )
        assertTrue(result.unavailable.isEmpty())
        assertTrue(result.boardable)
    }

    @Test
    fun unreachableFromCurrentButReachableElsewhereIsUnavailable() {
        val result = Destinations.assign(mapOf("a" to byId(), "b" to byId(dest("x", 70))), "a")
        assertTrue(result.own.isEmpty() && result.slower.isEmpty())
        assertEquals(listOf("x" to "b"), pairs(result.unavailable))
        assertFalse(result.boardable)
    }

    @Test
    fun eachGroupIsOrderedByRideTimeWhateverTheLine() {
        val own = arrayOf(dest("far", 90), dest("near", 10), dest("other", 5, onLine = false))
        val a = byId(*own, dest("s2", 80), dest("s1", 70))
        val unavailable = arrayOf(dest("u2", 40), dest("u1", 30, onLine = false), dest("u3", 20))
        val b = byId(dest("s2", 8), dest("s1", 7), *unavailable)
        val result = Destinations.assign(mapOf("a" to a, "b" to b), "a")
        assertEquals(listOf("other", "near", "far"), ids(result.own))
        assertEquals(listOf("s1", "s2"), ids(result.slower.map { it.destination }))
        assertEquals(listOf("u3", "u1", "u2"), ids(result.unavailable.map { it.destination }))
    }

    @Test
    fun walkingToTheOtherPlatformCounts() {
        val perPlatform = mapOf("a" to byId(dest("x", 100)), "b" to byId(dest("x", 80)))
        assertEquals(listOf("x"), ids(Destinations.assign(perPlatform, "a", mapOf("b" to 30)).own))
        assertEquals(listOf("x" to "b"), pairs(Destinations.assign(perPlatform, "a", mapOf("b" to 10)).slower))
    }

    @Test
    fun walkDoesNotApplyToTheCurrentPlatform() {
        val perPlatform = mapOf("a" to byId(dest("x", 100)), "b" to byId(dest("x", 120)))
        assertEquals(listOf("x"), ids(Destinations.assign(perPlatform, "a", mapOf("a" to 500, "b" to 0)).own))
    }

    @Test
    fun emptyInputsGiveEmptyOptions() {
        assertFalse(Destinations.assign(emptyMap(), "a").boardable)
        val none = Destinations.assign(mapOf("a" to byId(), "b" to byId()), "a")
        assertTrue(none.own.isEmpty() && none.slower.isEmpty() && none.unavailable.isEmpty())
    }
}
