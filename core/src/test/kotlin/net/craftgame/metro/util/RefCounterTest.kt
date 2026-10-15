package net.craftgame.metro.util

import kotlin.test.Test
import kotlin.test.assertEquals

class RefCounterTest {
    private val events = ArrayList<String>()
    private val counter = RefCounter<String>({ events += "+$it" }, { events += "-$it" })

    @Test
    fun firstHoldAddsAndLastReleaseRemoves() {
        counter.acquire("a")
        counter.acquire("a")
        assertEquals(listOf("+a"), events)
        assertEquals(2, counter.count("a"))

        counter.release("a")
        assertEquals(listOf("+a"), events)
        counter.release("a")
        assertEquals(listOf("+a", "-a"), events)
        assertEquals(0, counter.count("a"))
    }

    @Test
    fun oneHolderReleasingKeepsAnotherHoldersKey() {
        counter.acquire("ride")
        counter.acquire("ride")
        counter.release("ride")
        counter.acquire("ride")
        counter.release("ride")
        assertEquals(listOf("+ride"), events)
        assertEquals(1, counter.count("ride"))
    }

    @Test
    fun releasingAnUnheldKeyDoesNothing() {
        counter.release("x")
        counter.acquire("x")
        counter.release("x")
        counter.release("x")
        assertEquals(listOf("+x", "-x"), events)
    }

    @Test
    fun keysAreCountedSeparately() {
        counter.acquire("a")
        counter.acquire("b")
        counter.release("a")
        assertEquals(listOf("+a", "+b", "-a"), events)
    }

    @Test
    fun releaseAllRemovesEachKeyOnce() {
        counter.acquire("a")
        counter.acquire("a")
        counter.acquire("b")
        counter.releaseAll()
        assertEquals(setOf("-a", "-b"), events.filter { it.startsWith("-") }.toSet())
        assertEquals(2, events.count { it.startsWith("-") })
        counter.releaseAll()
        assertEquals(2, events.count { it.startsWith("-") })
        counter.acquire("a")
        assertEquals("+a", events.last())
    }
}
