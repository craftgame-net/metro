package net.craftgame.metro.ride

import net.craftgame.metro.geo.Point
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReturnGuardTest {
    private val id = UUID.randomUUID()
    private val plate = Point("world", 10.0, 64.0, 10.0)

    @Test
    fun holdsTheRememberedPlate() {
        val guard = ReturnGuard()
        guard.set(id, plate)
        assertTrue(guard.holds(id, Point("world", 10.4, 64.0, 10.9)))
    }

    @Test
    fun doesNotHoldAnotherPlate() {
        val guard = ReturnGuard()
        guard.set(id, plate)
        assertFalse(guard.holds(id, Point("world", 11.0, 64.0, 10.0)))
        assertFalse(guard.holds(id, Point("nether", 10.0, 64.0, 10.0)))
    }

    @Test
    fun holdsUntilPruned() {
        val guard = ReturnGuard()
        guard.set(id, plate)
        guard.prune { _, _ -> true }
        assertTrue(guard.holds(id, plate))
    }

    @Test
    fun unknownPlayerIsNotGuarded() {
        assertFalse(ReturnGuard().holds(id, plate))
    }

    @Test
    fun pruneForgetsPlayersWhoLeftThePlate() {
        val guard = ReturnGuard()
        val gone = UUID.randomUUID()
        guard.set(id, plate)
        guard.set(gone, plate)

        guard.prune { uuid, _ -> uuid == id }

        assertTrue(guard.holds(id, plate))
        assertFalse(guard.holds(gone, plate))
    }
}
