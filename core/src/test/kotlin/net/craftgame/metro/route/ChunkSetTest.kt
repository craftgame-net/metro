package net.craftgame.metro.route

import net.craftgame.metro.addPlatform
import net.craftgame.metro.data.Junction
import net.craftgame.metro.data.Network
import net.craftgame.metro.data.Platform
import net.craftgame.metro.edge
import net.craftgame.metro.point
import kotlin.test.Test
import kotlin.test.assertEquals

class ChunkSetTest {
    private fun chunks(
        set: ChunkSet,
        world: String = "w",
    ): Set<Pair<Int, Int>> =
        set
            .byWorld()[world]
            .orEmpty()
            .map { ChunkSet.chunkX(it) to ChunkSet.chunkZ(it) }
            .toSet()

    @Test
    fun `track point inside a chunk takes only that chunk`() {
        val set = ChunkSet()
        set.addTrackPoint("w", 5, 20)
        assertEquals(setOf(0 to 1), chunks(set))
    }

    @Test
    fun `track point on a border block takes the neighbour too`() {
        val set = ChunkSet()
        set.addTrackPoint("w", 15, 8)
        assertEquals(setOf(0 to 0, 1 to 0), chunks(set))

        val low = ChunkSet()
        low.addTrackPoint("w", 16, 8)
        assertEquals(setOf(0 to 0, 1 to 0), chunks(low))
    }

    @Test
    fun `track point on a corner takes four chunks`() {
        val set = ChunkSet()
        set.addTrackPoint("w", 0, 31)
        assertEquals(setOf(-1 to 1, 0 to 1, -1 to 2, 0 to 2), chunks(set))
    }

    @Test
    fun `negative coordinates map to the right chunks`() {
        val set = ChunkSet()
        set.addTrackPoint("w", -1, -20)
        assertEquals(setOf(-1 to -2, 0 to -2), chunks(set))
    }

    @Test
    fun `anchor takes a one chunk margin`() {
        val set = ChunkSet()
        set.addAround("w", -3, 40)
        val expected = (-2..0).flatMap { x -> (1..3).map { z -> x to z } }.toSet()
        assertEquals(expected, chunks(set))
    }

    @Test
    fun `chunks are deduplicated per world`() {
        val set = ChunkSet()
        for (x in 1..14) set.addTrackPoint("w", x, 3)
        set.addAround("w", 8, 8)
        set.addTrackPoint("other", 3, 3)
        assertEquals(9, chunks(set).size)
        assertEquals(setOf(0 to 0), chunks(set, "other"))
        assertEquals(10, set.size)
    }

    @Test
    fun `key round-trips extreme coordinates`() {
        for ((x, z) in listOf(0 to 0, -1 to -1, Int.MIN_VALUE to Int.MAX_VALUE, 1875000 to -1875000)) {
            val key = ChunkSet.key(x, z)
            assertEquals(x, ChunkSet.chunkX(key))
            assertEquals(z, ChunkSet.chunkZ(key))
        }
    }

    @Test
    fun `a long sparse track segment covers every chunk along it`() {
        val set = ChunkSet()
        val from = point(0.5, 64.0625, 8.5)
        val to = point(200.5, 64.0625, 8.5)
        set.addTrack("w", listOf(from, to))
        // Blocks 0..200 along x: chunks -1 (block 0 is on a chunk border) through 12 (block 200 is inside chunk 12,
        // not on a border).
        assertEquals((-1..12).map { it to 0 }.toSet(), chunks(set))
    }

    @Test
    fun `addTrack matches adding every densified point`() {
        val shapes =
            listOf(
                listOf(point(0.5, 64.0, 8.5), point(200.5, 64.0, 8.5)),
                listOf(point(-40.5, 70.0, -3.5), point(-40.5, 64.0, 60.5), point(30.5, 64.0, 60.5)),
                listOf(point(15.5, 64.0, 15.5), point(47.5, 80.0, 47.5), point(47.5, 80.0, -33.5)),
                listOf(point(0.3, 64.0, 0.1), point(31.9, 66.4, 2.6), point(32.2, 66.4, 33.7)),
                listOf(point(5.5, 64.0, 5.5)),
                emptyList(),
            )
        for (points in shapes) {
            val fast = ChunkSet()
            fast.addTrack("w", points)
            val dense = ChunkSet()
            for (point in Polyline.densify(points).points) dense.addTrackPoint("w", point.blockX, point.blockZ)
            assertEquals(chunks(dense), chunks(fast))
            assertEquals(dense.size, fast.size)
        }
    }

    @Test
    fun `a rebuild reads along every edge and around every anchor and junction`() {
        val network = Network()
        network.putEdge(edge("e", "a:0:out", "b:0:in", points = listOf(point(40.5, 64.0, 8.5), point(72.5, 64.0, 8.5))))
        network.putEdge(edge("empty", "b:0:out", "c:0:in", points = emptyList()))
        network.addPlatform("a", Platform(board = point(200.0, 64.0, 200.0), land = point(-20.0, 64.0, 8.0)))
        network.putJunction(Junction("j", "nether", point(8.0, 64.0, -40.0), Junction.Kind.SPLIT))

        val set = ChunkSet.forRebuild(network)

        // Blocks 40..72 along x at z = 8: chunks 2..4.
        val track = (2..4).map { it to 0 }
        val around = { x: Int, z: Int -> (x - 1..x + 1).flatMap { cx -> (z - 1..z + 1).map { cz -> cx to cz } } }
        assertEquals((track + around(12, 12) + around(-2, 0)).toSet(), chunks(set, "world"))
        assertEquals(around(0, -3).toSet(), chunks(set, "nether"))
    }
}
