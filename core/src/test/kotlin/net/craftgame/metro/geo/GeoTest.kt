package net.craftgame.metro.geo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GeoTest {
    @Test
    fun blocksRoundDownAlsoBelowZero() {
        assertEquals(BlockPos(-1, 64, 0), Point("world", -0.5, 64.9, 0.0).block())
        assertEquals(BlockPos(-2, -1, 3), Vec3(-1.01, -0.2, 3.99).block())
    }

    @Test
    fun sameBlockNeedsTheSameWorld() {
        val here = Point("world", 1.2, 64.0, 1.8)
        assertTrue(here.sameBlock(Point("world", 1.9, 64.5, 1.0)))
        assertFalse(here.sameBlock(Point("nether", 1.2, 64.0, 1.8)))
        assertFalse(here.sameBlock(Point("world", 2.0, 64.0, 1.8)))
    }

    @Test
    fun vectorArithmetic() {
        val a = Vec3(3.0, 0.0, 4.0)
        assertEquals(5.0, a.length())
        assertEquals(Vec3(0.6, 0.0, 0.8), a.normalized())
        assertEquals(Vec3(4.0, 1.0, 4.0), a + Vec3(1.0, 1.0, 0.0))
        assertEquals(Vec3(-6.0, -2.0, -8.0), Vec3(3.0, 1.0, 4.0) * -2.0)
        assertEquals(25.0, a.distanceSquared(Vec3.ZERO))
    }

    @Test
    fun cardinalsReadTheirNamesInAnyCase() {
        assertEquals(Cardinal.EAST, Cardinal.of("east"))
        assertEquals(Vec3(0.0, 0.0, -1.0), Cardinal.of("NORTH")?.direction)
        assertNull(Cardinal.of("up"))
    }
}
