package net.craftgame.metro.route

import net.craftgame.metro.data.Network
import net.craftgame.metro.data.Platform
import net.craftgame.metro.geo.Point
import kotlin.math.floor

/** Chunks to preload, per world name. Built from block coordinates only, so it needs no live world. */
class ChunkSet {
    private val worlds = LinkedHashMap<String, LinkedHashSet<Long>>()

    /** Number of chunks over all worlds. */
    val size: Int get() = worlds.values.sumOf { it.size }

    /** Returns the chunk keys (see [key]) per world name. */
    fun byWorld(): Map<String, Set<Long>> = worlds

    /**
     * Adds the chunk of the block. Also adds the neighbour chunk when the block is on a chunk border, because the
     * path search reads the blocks next to it.
     */
    fun addTrackPoint(
        world: String,
        blockX: Int,
        blockZ: Int,
    ) {
        for (chunkX in borderSpan(blockX)) {
            for (chunkZ in borderSpan(blockZ)) add(world, chunkX, chunkZ)
        }
    }

    /**
     * Steps along stored segments like [Polyline.densify], so long straight segments count without building
     * dense points.
     */
    fun addTrack(
        world: String,
        points: List<Point>,
    ) {
        if (points.isEmpty()) return
        addTrackPoint(world, points[0].blockX, points[0].blockZ)
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            val dx = b.x - a.x
            val dz = b.z - a.z
            val steps = Polyline.steps(dx, b.y - a.y, dz)
            for (k in 1 until steps) {
                val blockX = floor(a.x + dx * k / steps).toInt()
                val blockZ = floor(a.z + dz * k / steps).toInt()
                addTrackPoint(world, blockX, blockZ)
            }
            addTrackPoint(world, b.blockX, b.blockZ)
        }
    }

    /** Adds the chunk of the block and the eight chunks around it. */
    fun addAround(
        world: String,
        blockX: Int,
        blockZ: Int,
    ) {
        val centerX = blockX shr CHUNK_SHIFT
        val centerZ = blockZ shr CHUNK_SHIFT
        for (chunkX in centerX - 1..centerX + 1) {
            for (chunkZ in centerZ - 1..centerZ + 1) add(world, chunkX, chunkZ)
        }
    }

    private fun add(
        world: String,
        chunkX: Int,
        chunkZ: Int,
    ) {
        worlds.getOrPut(world) { LinkedHashSet() }.add(key(chunkX, chunkZ))
    }

    private fun borderSpan(block: Int): IntRange {
        val chunk = block shr CHUNK_SHIFT
        val offset = block and CHUNK_MASK
        val low = if (offset == 0) chunk - 1 else chunk
        val high = if (offset == CHUNK_MASK) chunk + 1 else chunk
        return low..high
    }

    companion object {
        /** A chunk is 16 blocks wide, so a block coordinate shifted right by 4 is the chunk coordinate. */
        private const val CHUNK_SHIFT = 4

        /** Position of a block inside its chunk is its coordinate and 15. */
        private const val CHUNK_MASK = 15
        private const val LOW_BITS = 0xFFFFFFFFL

        /** Packs the chunk coordinates into one Long. */
        fun key(
            chunkX: Int,
            chunkZ: Int,
        ): Long = (chunkX.toLong() shl Int.SIZE_BITS) or (chunkZ.toLong() and LOW_BITS)

        /** Returns the chunk x of a [key]. */
        fun chunkX(key: Long): Int = (key shr Int.SIZE_BITS).toInt()

        /** Returns the chunk z of a [key]. */
        fun chunkZ(key: Long): Int = key.toInt()

        /**
         * Returns the chunks a rebuild of [network] reads: along every stored edge, plus a one-chunk margin around
         * every anchor and junction.
         */
        fun forRebuild(network: Network): ChunkSet {
            val chunks = ChunkSet()
            for (edge in network.edges.values) {
                val world = edge.points.firstOrNull()?.world ?: continue
                chunks.addTrack(world, edge.points)
            }
            for (station in network.stations.values) {
                for (platform in station.platforms) {
                    for (role in Platform.ROLES) {
                        val anchor = platform.anchor(role) ?: continue
                        chunks.addAround(station.world, anchor.blockX, anchor.blockZ)
                    }
                }
            }
            for (junction in network.junctions.values) {
                chunks.addAround(junction.world, junction.location.blockX, junction.location.blockZ)
            }
            return chunks
        }
    }
}
