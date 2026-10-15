package net.craftgame.metro.route

import net.craftgame.metro.data.Nodes
import net.craftgame.metro.geo.Point
import net.craftgame.metro.geo.Vec3
import kotlin.math.abs

/**
 * Orients the main-line points and links each to the next point ahead. Direction belongs to the track, not to a
 * platform, so `in` may come before `out`. Each hop passes its arrival direction on, so one node with a known
 * direction (a seed) orients a track.
 */
object TrackChain {
    /**
     * A null [heading] links without orienting (a ramp merging sideways into a join: the through track has its
     * direction).
     */
    data class Hop(
        val to: String,
        val path: List<Point>,
        val heading: Vec3?,
    )

    /**
     * [TRACK]: along the main line. [BRANCH]: a side exit of a `split`, or a ramp merging into a `join`. [TURN]: a turn
     * at a `cross`.
     */
    enum class LinkKind { TRACK, BRANCH, TURN }

    /** A path from node [from] to node [to]. */
    data class Link(
        val from: String,
        val to: String,
        val path: List<Point>,
        val kind: LinkKind = LinkKind.TRACK,
    )

    /** A null [heading] means [to] keeps its orientation. */
    data class BranchHop(
        val to: String,
        val path: List<Point>,
        val heading: Vec3?,
        val kind: LinkKind = LinkKind.BRANCH,
    )

    /**
     * The exits of a point. A [settled] point always gives the same [hops], so it is not asked again on later passes.
     */
    data class Branches(
        val hops: List<BranchHop>,
        val settled: Boolean = false,
    )

    /** The outcome of a walk. */
    data class Result(
        val links: List<Link>,
        /** Travel direction (a unit vector on the x/z plane) per oriented node. */
        val headings: Map<String, Vec3>,
        /** Nodes that were reached with opposite directions. */
        val conflicts: Set<String>,
    )

    /** Like [build], for a graph without branch exits. */
    fun walk(
        explicit: Map<String, Vec3>,
        derived: Map<String, Vec3>,
        step: (node: String, heading: Vec3) -> List<Hop>,
    ): Result = build(explicit, derived, step) { _, _, _ -> Branches(emptyList(), settled = true) }

    /**
     * Walks straight paths from the seeds, then follows branch exits of oriented points. A branch can orient new
     * points, so the walk repeats until nothing new appears. It ends because each state is oriented and each link
     * made once.
     *
     * [explicit] and [derived] map nodes to their travel direction; [explicit] seeds go first, and a [derived] one
     * that points the opposite way to an existing direction is a conflict. [step] and [branch] run synchronously on
     * the calling thread. [step] returns the hops ahead of a node. [branch] receives the directions found so far.
     */
    fun build(
        explicit: Map<String, Vec3>,
        derived: Map<String, Vec3>,
        step: (node: String, heading: Vec3) -> List<Hop>,
        branch: (node: String, heading: Vec3, oriented: Map<String, Vec3>) -> Branches,
    ): Result {
        val traversal = Traversal(step, branch)
        for ((node, heading) in explicit) traversal.seed(node, heading)
        traversal.drain()

        for ((node, heading) in derived) {
            val existing = traversal.headings[node]
            if (existing != null) {
                if (opposite(existing, heading)) traversal.conflicts += node
            } else {
                traversal.seed(node, heading)
                traversal.drain()
            }
        }

        traversal.runBranches()
        return traversal.result()
    }

    /** Returns the ids of stations that have a link between two of their platforms, where a ride can turn around. */
    fun turnaroundStations(links: List<Link>): Set<String> {
        val stations = LinkedHashSet<String>()
        for (link in links) {
            val from = Nodes.parse(link.from)
            val to = Nodes.parse(link.to)
            if (from == null || to == null) continue
            if (from.stationId == to.stationId && from.platform != to.platform) stations += from.stationId
        }
        return stations
    }

    /** Returns the one element of [options] that is not [except], or null when there are none or several. */
    fun <T> soleOther(
        options: List<T>,
        except: T,
    ): T? = options.singleOrNull { it != except }

    /** Returns the unit direction from [from] to [to] on the x/z plane, or null when they are in the same column. */
    fun direction(
        from: Vec3,
        to: Vec3,
    ): Vec3? {
        val vector = Vec3(to.x - from.x, 0.0, to.z - from.z)
        return if (vector.lengthSquared() < EPSILON) null else vector.normalized()
    }

    /** Returns `east`, `west`, `south` or `north`, whichever [vector] points to most on the x/z plane. */
    fun cardinal(vector: Vec3): String =
        if (abs(vector.x) >= abs(vector.z)) {
            if (vector.x >= 0) "east" else "west"
        } else {
            if (vector.z >= 0) "south" else "north"
        }
}

private class Traversal(
    private val step: (String, Vec3) -> List<TrackChain.Hop>,
    private val branch: (String, Vec3, Map<String, Vec3>) -> TrackChain.Branches,
) {
    val headings = LinkedHashMap<String, Vec3>()
    val conflicts = LinkedHashSet<String>()
    private val links = ArrayList<TrackChain.Link>()
    private val linkKeys = HashSet<Pair<String, String>>()
    private val queue = ArrayDeque<String>()

    private val settled = HashSet<String>()

    fun seed(
        node: String,
        heading: Vec3,
    ) {
        headings[node] = heading
        queue += node
    }

    fun drain() {
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            for (hop in step(node, headings.getValue(node))) {
                addLink(node, hop.to, hop.path, TrackChain.LinkKind.TRACK)
                val target = hop.heading ?: continue
                val existing = headings[hop.to]
                if (existing == null) {
                    headings[hop.to] = target
                    queue += hop.to
                } else if (opposite(existing, target)) {
                    conflicts += hop.to
                }
            }
        }
    }

    fun runBranches() {
        while (true) {
            var changed = false
            for (node in headings.keys.toList()) {
                if (node !in settled && applyBranches(node)) changed = true
            }
            if (!changed) break
            drain()
        }
    }

    private fun applyBranches(node: String): Boolean {
        val branches = branch(node, headings.getValue(node), headings)
        if (branches.settled) settled += node
        var changed = false
        for (hop in branches.hops) {
            if (addLink(node, hop.to, hop.path, hop.kind)) changed = true
            val target = hop.heading ?: continue
            val existing = headings[hop.to]
            if (existing == null) {
                headings[hop.to] = target
                queue += hop.to
                changed = true
            } else if (opposite(existing, target) && conflicts.add(hop.to)) {
                changed = true
            }
        }
        return changed
    }

    fun result(): TrackChain.Result = TrackChain.Result(links, headings, conflicts)

    private fun addLink(
        from: String,
        to: String,
        path: List<Point>,
        kind: TrackChain.LinkKind,
    ): Boolean {
        if (!linkKeys.add(from to to)) return false
        links += TrackChain.Link(from, to, path, kind)
        return true
    }
}

private fun opposite(
    a: Vec3,
    b: Vec3,
): Boolean = a.x * b.x + a.z * b.z < -EPSILON

private const val EPSILON = 1e-6
