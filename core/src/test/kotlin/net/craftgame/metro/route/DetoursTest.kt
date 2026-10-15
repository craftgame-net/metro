package net.craftgame.metro.route

import net.craftgame.metro.TestNetwork
import net.craftgame.metro.addPlatform
import net.craftgame.metro.at
import net.craftgame.metro.data.Platform
import net.craftgame.metro.edge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DetoursTest {
    @Test
    fun picksTheLongWayOfALopsidedPair() {
        val detours = Detours.pick(mapOf(("a" to "b") to 90.0, ("b" to "a") to 30.0))
        assertEquals(listOf(Triple("a", "b", 3.0)), detours.map { Triple(it.from, it.to, it.ratio) })
    }

    @Test
    fun ignoresBalancedPairs() {
        assertTrue(Detours.pick(mapOf(("a" to "b") to 50.0, ("b" to "a") to 40.0)).isEmpty())
    }

    @Test
    fun needsBothWays() {
        assertTrue(Detours.pick(mapOf(("a" to "b") to 500.0)).isEmpty())
    }

    @Test
    fun worstFirstAndLimited() {
        val trips =
            mapOf(
                ("a" to "b") to 60.0,
                ("b" to "a") to 20.0,
                ("c" to "d") to 100.0,
                ("d" to "c") to 20.0,
                ("e" to "f") to 50.0,
                ("f" to "e") to 20.0,
            )
        assertEquals(listOf("c", "a"), Detours.pick(trips, limit = 2).map { it.from })
    }

    @Test
    fun findMeasuresTheQuickestTripBothWaysFromEveryBoardingPlatform() {
        val network = TestNetwork()
        val button = at(0, y = 70)
        network.addPlatform("a", Platform(board = button, begin = at(0, z = 10), outbound = at(1, z = 10)))
        network.addPlatform("b", Platform(board = button, begin = at(0), outbound = at(1)))
        network.addPlatform("a", Platform(inbound = at(91), eject = at(92)))
        network.addPlatform("b", Platform(inbound = at(21, z = 10), eject = at(22, z = 10)))
        // a to b is short; b to a goes the long way round.
        network.putEdge(edge("ab", "a:0:out", "b:1:in", points = listOf(at(1, z = 10), at(21, z = 10))))
        network.putEdge(edge("ba", "b:0:out", "a:1:in", points = listOf(at(1), at(91))))

        val detours = Detours.find(network, Router(network))

        assertEquals(listOf("b" to "a"), detours.map { it.from to it.to })
        // Each trip includes the one-block virtual pocket edges at both ends.
        assertEquals(92.0, detours.single().there, 1e-9)
        assertEquals(22.0, detours.single().back, 1e-9)
    }

    @Test
    fun aMissingWayBackOrAZeroLengthOneIsNoDetour() {
        assertTrue(Detours.pick(mapOf(("a" to "b") to 50.0, ("b" to "a") to 0.0)).isEmpty())
    }
}
