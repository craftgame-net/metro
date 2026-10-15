package net.craftgame.metro.rail

import net.craftgame.metro.geo.BlockPos
import net.craftgame.metro.geo.Point
import net.craftgame.metro.geo.Vec3

/**
 * Finds paths along rail blocks. All searches read blocks from a [RailWorld], so call them on the server thread with
 * the chunks loaded.
 *
 * Level connectivity ignores rail shape, because a shape holds only two directions and junction blocks would break
 * the search. A step up or down needs a slope pointing along it.
 */
object RailPathfinder {
    private val OFFSETS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)

    /**
     * The outcome of a search. [path] holds the block-centre locations from start to goal, or is null when none was
     * found. [limitReached] tells a search that gave up at its block limit from one that ran out of rails.
     */
    data class PathResult(
        val path: List<Point>?,
        val limitReached: Boolean = false,
    )

    /** Limits on where a search may begin. */
    data class Departure(
        /**
         * Direction of travel; only x and z count. Limits the first step to rails ahead, or to the side when there are
         * none, never behind. Null: no limit.
         */
        val heading: Vec3? = null,
        /** With a [heading]: never step to the side, so a junction state stays on its own axis. */
        val straightOnly: Boolean = false,
        /** The start block does not count as a goal. */
        val skipStart: Boolean = false,
    )

    /** The outcome of [bfs]. [goal] is null when none was found. */
    internal data class BfsResult(
        val goal: BlockPos?,
        val limitReached: Boolean,
    )

    /**
     * Returns the path along rails from the rail nearest [from] to the rail nearest [to], visiting at most [maxNodes]
     * blocks.
     */
    fun findPath(
        world: RailWorld,
        from: Point,
        to: Point,
        maxNodes: Int,
    ): PathResult {
        val goal = nearestRail(world, to) ?: return PathResult(null)
        return findNearest(world, from, setOf(goal), maxNodes = maxNodes)
    }

    /**
     * Returns the shortest path along rails from the rail nearest [from] to the closest block in [targets], visiting
     * at most [maxNodes] blocks. With a [Departure.heading] the search leaves in that direction, so on a loop with
     * rails both ways it takes the way ahead.
     */
    fun findNearest(
        world: RailWorld,
        from: Point,
        targets: Set<BlockPos>,
        departure: Departure = Departure(),
        maxNodes: Int,
    ): PathResult {
        val startVec = nearestRail(world, from) ?: return PathResult(null)
        val previous = HashMap<BlockPos, BlockPos>()
        val heading = departure.heading
        val isTarget: (BlockPos) -> Boolean =
            if (departure.skipStart) {
                { it != startVec && it in targets }
            } else {
                { it in targets }
            }
        val result =
            bfs(startVec, isTarget, maxNodes, previous) { current ->
                if (current == startVec && heading != null) {
                    firstStep(world, current, heading, departure.straightOnly)
                } else {
                    connections(world, current)
                }
            }
        val path = result.goal?.let { reconstruct(world, previous, startVec, it) }
        return PathResult(path, result.limitReached)
    }

    /**
     * Breadth-first search from [start]. Gives up after [maxNodes] blocks, and says so when blocks were still queued.
     * Fills [previous] with the way back from each block reached. [neighbours] lists the blocks to step to.
     */
    internal fun bfs(
        start: BlockPos,
        isTarget: (BlockPos) -> Boolean,
        maxNodes: Int,
        previous: MutableMap<BlockPos, BlockPos>,
        neighbours: (BlockPos) -> List<BlockPos>,
    ): BfsResult {
        val queue = ArrayDeque<BlockPos>()
        val seen = HashSet<BlockPos>()
        queue.add(start)
        seen.add(start)

        var nodes = 0
        var goal: BlockPos? = null
        while (queue.isNotEmpty() && goal == null && nodes < maxNodes) {
            nodes++
            val current = queue.removeFirst()
            if (isTarget(current)) {
                goal = current
                continue
            }
            for (vector in neighbours(current)) {
                if (seen.add(vector)) {
                    previous[vector] = current
                    queue.add(vector)
                }
            }
        }
        return BfsResult(goal, limitReached = goal == null && queue.isNotEmpty())
    }

    private fun firstStep(
        world: RailWorld,
        from: BlockPos,
        heading: Vec3,
        straightOnly: Boolean,
    ): List<BlockPos> {
        val neighbors = connections(world, from)
        val strict = neighbors.filter { dot(from, it, heading) > EPSILON }
        if (strict.isNotEmpty()) return strict
        return if (straightOnly) emptyList() else neighbors.filter { dot(from, it, heading) >= -EPSILON }
    }

    private fun dot(
        from: BlockPos,
        to: BlockPos,
        heading: Vec3,
    ): Double {
        val dx = (to.x - from.x).toDouble()
        val dz = (to.z - from.z).toDouble()
        return dx * heading.x + dz * heading.z
    }

    /** Returns the rail block nearest to [location] within [radius] blocks, or null when there is none. */
    fun nearestRail(
        world: RailWorld,
        location: Point,
        radius: Int = 1,
    ): BlockPos? {
        // Compare from the block centre. Otherwise the clicked rail and its neighbour tie, and iteration order decides.
        val reference = Vec3(location.blockX + CENTER, location.blockY + CENTER, location.blockZ + CENTER)
        var best: BlockPos? = null
        var bestDistance = Double.MAX_VALUE
        for (dx in -radius..radius) {
            for (dy in -radius..radius) {
                for (dz in -radius..radius) {
                    val block = BlockPos(location.blockX + dx, location.blockY + dy, location.blockZ + dz)
                    if (world.railShape(block) == null) continue
                    val distance = center(world, block).toVec3().distanceSquared(reference)
                    if (distance < bestDistance) {
                        bestDistance = distance
                        best = block
                    }
                }
            }
        }
        return best
    }

    /** Returns the point on top of the rail at the centre of [block] in [world]. */
    fun center(
        world: RailWorld,
        block: BlockPos,
    ): Point = Point(world.name, block.x + CENTER, block.y + RAIL_TOP, block.z + CENTER)

    /**
     * Returns the rail blocks [block] connects to. Level neighbours always count, whatever their shape. One block up
     * or down only along a slope.
     */
    fun connections(
        world: RailWorld,
        block: BlockPos,
    ): List<BlockPos> {
        val shape = world.railShape(block)
        val result = ArrayList<BlockPos>(12)
        for ((dx, dz) in OFFSETS) {
            for (dy in -1..1) {
                val candidate = BlockPos(block.x + dx, block.y + dy, block.z + dz)
                val rail = world.railShape(candidate) ?: continue
                if (RailSlopes.connects(dx, dy, dz, shape, rail)) result.add(candidate)
            }
        }
        return result
    }

    private fun reconstruct(
        world: RailWorld,
        previous: Map<BlockPos, BlockPos>,
        start: BlockPos,
        goal: BlockPos,
    ): List<Point> {
        val path = ArrayList<BlockPos>()
        var current: BlockPos? = goal
        while (current != null) {
            path.add(current)
            if (current == start) break
            current = previous[current]
        }
        path.reverse()
        return path.map { center(world, it) }
    }

    /** A rail with this many rail neighbours or more is a switch. */
    const val SWITCH_MIN_NEIGHBOURS = 3

    private const val EPSILON = 1e-6

    /** Offset from a block corner to its centre, in blocks. */
    private const val CENTER = 0.5

    /** Height of a flat rail's top above its block's floor, in blocks. */
    private const val RAIL_TOP = 0.0625
}
