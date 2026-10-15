package net.craftgame.metro.route

import net.craftgame.metro.config.Speeds
import net.craftgame.metro.data.Edge
import net.craftgame.metro.data.Junction
import net.craftgame.metro.data.Network
import net.craftgame.metro.data.Nodes
import net.craftgame.metro.data.Platform
import net.craftgame.metro.data.Station
import net.craftgame.metro.debug.DebugLevel
import net.craftgame.metro.debug.DebugLog
import net.craftgame.metro.geo.BlockPos
import net.craftgame.metro.geo.Point
import net.craftgame.metro.geo.Vec3
import net.craftgame.metro.rail.RailPathfinder
import net.craftgame.metro.rail.RailWorld
import net.craftgame.metro.rail.RailWorlds
import net.craftgame.metro.rail.SwitchMarks
import net.craftgame.metro.util.blockCoords
import java.util.UUID

/**
 * Rebuilds the auto edges from platform anchors and junctions. [TrackChain] walks the main line (the track between
 * stations, not the short edges inside a platform) from seeds with a known direction (an explicit
 * [Platform.heading], or one read from the rails).
 *
 * A junction is a transit point on its straight path, so the walk goes through without turning. Branch exits and
 * crossing turns come later, once both tracks through the junction have a direction.
 */
object EdgeBuilder {
    /** Null until the first rebuild since start or `/metro reload`. */
    var lastReport: RebuildReport? = null
        private set

    /** Forgets [lastReport]. */
    fun clearReport() {
        lastReport = null
    }

    /** What a rebuild reads, and where its texts come from. */
    class Input(
        /** Gets the new auto edges. */
        val network: Network,
        /** Routes over [network], to find boarding platforms that reach no other station. */
        val router: Router,
        val rails: RailWorlds,
        val speeds: Speeds,
        /** Rail blocks one search may visit before it gives up. */
        val maxSearch: Int,
        val text: WarningText,
        val debugLog: DebugLog = DebugLog.OFF,
        /** Returns the teleport target of the block that contains a point, or null when its world is not loaded. */
        val blockTarget: (Point) -> IssueTarget?,
    )

    /**
     * Replaces the network's auto edges only when all are built; the caller saves. Needs the chunks loaded,
     * since the path search reads blocks. Debug lines are about the player [subject]. Server thread only.
     */
    fun rebuild(
        input: Input,
        subject: UUID? = null,
    ): RebuildReport {
        val store = input.network
        val text = input.text
        val debug = input.debugLog

        fun note(message: () -> String) = debug.log(DebugLevel.DECISION, subject, message)
        val manual =
            store.edges.values
                .filter { !it.auto }
                .map { it.fromNode to it.toNode }
                .toHashSet()
        // Hand-authored points may have changed since the switch marks were saved; stale marks would skip a rail
        // that a ride has to reshape.
        for (edge in store.edges.values) SwitchMarks.remark(input.rails, edge)

        val findings = Findings(input.maxSearch)
        val run = Run(input, manual, findings)
        val points = run.points
        val errors = ArrayList<RebuildWarning>()

        for (station in store.stations.values) {
            for (platform in station.platforms) {
                buildPockets(run, station, platform)
            }
        }

        note { "rebuild pockets: ${findings.edges.size} edge(s) in ${store.stations.size} station(s)" }

        val targets = buildTargets(run, findings.warnings)
        collectJunctions(run, store, targets, errors)
        note { "rebuild junctions: ${store.junctions.size} junction(s), ${points.size} point(s)" }

        val result =
            TrackChain.build(
                run.explicit,
                run.derived,
                step = { node, heading -> step(run, targets, node, heading) },
                branch = { node, heading, oriented -> branches(run, targets, BranchFrom(node, heading, oriented)) },
            )
        logChain(debug, subject, result)
        val links = GraphChecks.dropBackwardRamps(text, store, result, findings.warnings, debug, subject)

        for (link in links) {
            createEdge(run, link.from, link.to, link.path, LinkSpeed.of(store, link, input.speeds))
        }
        // Only now, with every edge built: a failure above leaves the saved graph as it was.
        store.replaceAutoEdges(findings.edges)
        note { "rebuild edges swapped: ${findings.edges.size} auto edge(s)" }

        warnUnmarkedJunctions(run, links, targets)
        val unused = unusedJunctions(points.keys, links)
        unused.mapTo(findings.warnings) { id -> text.warning("junction-unused", IssueTarget.Junction(id), "id" to id) }
        warnUnorientedJunctions(text, points.keys, result.headings, unused, findings.warnings)

        val unoriented = points.keys.filter { Nodes.parseJunction(it) == null && it !in result.headings }
        val report =
            RebuildReport(
                conflicts = result.conflicts,
                unoriented = unoriented,
                turnarounds = TrackChain.turnaroundStations(links),
                junctionErrors = errors,
                junctionWarnings = findings.warnings.toList(),
                boardingDeadEnds = boardingDeadEnds(store, input.router),
                missingOut = missingOut(store.stations.values),
                terminals =
                    store.stations.values
                        .filter { it.terminal }
                        .mapTo(LinkedHashSet()) { it.id },
                searchLimitHits = findings.limitWarnings(text),
            )
        lastReport = report
        return report
    }

    /**
     * Returns the centre of the rail block nearest to [point], or [point] itself when no rail is within 1 block or its
     * world is not loaded.
     */
    fun snapToRail(
        rails: RailWorlds,
        point: Point,
    ): Point {
        val world = rails.world(point.world) ?: return point
        val rail = RailPathfinder.nearestRail(world, point)
        return if (rail != null) RailPathfinder.center(world, rail) else point
    }

    private class WorldTargets(
        val world: RailWorld,
    ) {
        /** Station point node by the rail block it sits on. */
        val pointByBlock = HashMap<BlockPos, String>()

        /** Junction by the rail block it sits on. */
        val junctionByBlock = HashMap<BlockPos, JunctionNode>()

        /** Rail blocks where a path search may stop: every point and every junction block. */
        val goals = HashSet<BlockPos>()

        fun isTarget(key: BlockPos): Boolean = key in pointByBlock || key in junctionByBlock
    }

    private class JunctionNode(
        val id: String,
        val kind: Junction.Kind,
        val turns: Boolean,
        /** Node id of each axis (`x`, `z`) the junction has. */
        val states: Map<String, String>,
    )

    private class BranchPoint(
        val junction: JunctionNode,
        val axis: String,
        val block: BlockPos,
        val worldTargets: WorldTargets,
    )

    private fun buildTargets(
        run: Run,
        warnings: MutableSet<RebuildWarning>,
    ): LinkedHashMap<String, WorldTargets> {
        val targets = LinkedHashMap<String, WorldTargets>()
        for ((node, anchor) in run.points) {
            val world = run.input.rails.world(anchor.world)
            val isPoint = Nodes.parseJunction(node) == null
            val block = if (world != null && isPoint) RailPathfinder.nearestRail(world, anchor) else null
            if (world != null && block != null) {
                addPoint(run.input.text, node, block, targets.getOrPut(world.name) { WorldTargets(world) }, warnings)
            }
        }
        return targets
    }

    /** Registers the station point [node] on its rail [block], or reports it when another point is there already. */
    private fun addPoint(
        text: WarningText,
        node: String,
        block: BlockPos,
        worldTargets: WorldTargets,
        warnings: MutableSet<RebuildWarning>,
    ) {
        val existing = worldTargets.pointByBlock[block]
        if (existing != null) {
            warnings +=
                text.warning(
                    "point-shared-block",
                    IssueTarget.Node(node),
                    "point" to Nodes.label(node),
                    "other" to Nodes.label(existing),
                    "coords" to blockCoords(block),
                )
        } else {
            worldTargets.pointByBlock[block] = node
        }
        worldTargets.goals += block
    }

    private fun collectJunctions(
        run: Run,
        store: Network,
        targets: MutableMap<String, WorldTargets>,
        errors: MutableList<RebuildWarning>,
    ) {
        for (junction in store.junctions.values) {
            addJunction(run, junction, targets, errors)
        }
        warnPointsOnJunctions(run.input.text, targets, run.findings.warnings)
    }

    private fun addJunction(
        run: Run,
        junction: Junction,
        targets: MutableMap<String, WorldTargets>,
        errors: MutableList<RebuildWarning>,
    ) {
        val text = run.input.text
        val world = run.input.rails.world(junction.location.world)
        val block = if (world != null) RailPathfinder.nearestRail(world, junction.location) else null
        if (world == null || block == null) {
            errors += text.warning("junction-no-rail", IssueTarget.Junction(junction.id), "id" to junction.id)
            return
        }
        val targetWorld = targets.getOrPut(world.name) { WorldTargets(world) }
        val axes = axesOf(world, block, junction.kind)
        when {
            targetWorld.junctionByBlock.containsKey(block) ->
                errors +=
                    text.warning(
                        "junction-duplicate",
                        IssueTarget.Junction(junction.id),
                        "id" to junction.id,
                        "coords" to blockCoords(block),
                    )
            axes == null ->
                errors +=
                    text.warning(
                        "junction-bad-block",
                        IssueTarget.Junction(junction.id),
                        "id" to junction.id,
                        "kind" to junction.kind.name.lowercase(),
                        "coords" to blockCoords(block),
                    )
            else -> {
                val states = LinkedHashMap<String, String>()
                for (axis in axes) {
                    val node = Nodes.junction(junction.id, axis)
                    run.points[node] = RailPathfinder.center(world, block)
                    states[axis] = node
                }
                targetWorld.junctionByBlock[block] = JunctionNode(junction.id, junction.kind, junction.turns, states)
                targetWorld.goals += block
            }
        }
    }

    /** Returns the axes (`x`, `z`) of the junction at [block], or null when the rails around it do not fit [kind]. */
    private fun axesOf(
        world: RailWorld,
        block: BlockPos,
        kind: Junction.Kind,
    ): List<String>? {
        var xCount = 0
        var zCount = 0
        var xFlatPos = 0
        var xFlatNeg = 0
        var zFlatPos = 0
        var zFlatNeg = 0
        for (neighbor in RailPathfinder.connections(world, block)) {
            val dx = neighbor.x - block.x
            val dz = neighbor.z - block.z
            val flat = neighbor.y == block.y
            if (dx > 0) {
                xCount++
                if (flat) xFlatPos++
            } else if (dx < 0) {
                xCount++
                if (flat) xFlatNeg++
            }
            if (dz > 0) {
                zCount++
                if (flat) zFlatPos++
            } else if (dz < 0) {
                zCount++
                if (flat) zFlatNeg++
            }
        }
        return JunctionGeometry.axes(
            kind,
            xPair = xFlatPos > 0 && xFlatNeg > 0,
            xCount = xCount,
            zPair = zFlatPos > 0 && zFlatNeg > 0,
            zCount = zCount,
        )
    }

    private data class StepTargets(
        val world: RailWorld,
        val anchor: Point,
        val targets: WorldTargets,
    )

    private fun stepTargets(
        run: Run,
        node: String,
        targetsByWorld: Map<String, WorldTargets>,
    ): StepTargets? {
        val anchor = run.points[node]
        val world = anchor?.let { run.input.rails.world(it.world) }
        val targets = world?.let { targetsByWorld[it.name] }
        if (anchor == null || world == null || targets == null) return null
        val ownBlock = RailPathfinder.nearestRail(world, anchor)
        return if (onlyOwn(targets.goals, ownBlock)) null else StepTargets(world, anchor, targets)
    }

    /**
     * True when no goal other than the search's own start block [own] exists, so a search could find nothing.
     * A search skips its start block ([RailPathfinder.Departure.skipStart]), so the goals need not be copied
     * without it.
     */
    private fun onlyOwn(
        goals: Set<BlockPos>,
        own: BlockPos?,
    ): Boolean = goals.isEmpty() || (goals.size == 1 && own != null && own in goals)

    private fun step(
        run: Run,
        targetsByWorld: Map<String, WorldTargets>,
        node: String,
        heading: Vec3,
    ): List<TrackChain.Hop> {
        val targets = stepTargets(run, node, targetsByWorld) ?: return emptyList()
        val departure = RailPathfinder.Departure(heading, Nodes.parseJunction(node) != null, skipStart = true)
        val path = run.findings.nearest(node, targets.world, targets.anchor, targets.targets.goals, departure).path
        val reached = path?.let { arrival(run, targets.targets, it, heading) }
        return if (path == null || reached == null) {
            emptyList()
        } else {
            listOf(TrackChain.Hop(reached.node, path, reached.heading))
        }
    }

    /** The node a path ends at. A null [heading] links the node without giving it a travel direction. */
    private class Arrival(
        val node: String,
        val heading: Vec3?,
    )

    /**
     * Returns the node [path] ends at. Null when the path is too short or ends at nothing a cart can enter.
     * [fallback] is the travel direction used when the last step of the path has no direction.
     */
    private fun arrival(
        run: Run,
        targets: WorldTargets,
        path: List<Point>,
        fallback: Vec3,
    ): Arrival? {
        if (path.size < 2) return null
        val lastKey = path.last().block()
        val direction = TrackChain.direction(path[path.size - 2].toVec3(), path.last().toVec3()) ?: fallback
        val junction = targets.junctionByBlock[lastKey]
        val entry =
            if (junction == null) {
                targets.pointByBlock[lastKey]?.let { JunctionEntry(it, true) }
            } else {
                resolveJunction(run, junction, lastKey, direction)
            }
        return entry?.let { Arrival(it.node, if (it.orient) direction else null) }
    }

    private data class JunctionEntry(
        val node: String,
        val orient: Boolean,
    )

    /**
     * Picks the junction state that a path ending at [block] enters, or null. A path along an axis of the junction
     * enters that axis. A `join` entered from the side links to its first axis without a direction, because the
     * straight track through it has its own. A `split` entered from the side is reported and not linked.
     */
    private fun resolveJunction(
        run: Run,
        junction: JunctionNode,
        block: BlockPos,
        arrival: Vec3,
    ): JunctionEntry? {
        junction.states[JunctionGeometry.axisOf(arrival)]?.let { return JunctionEntry(it, true) }
        return when (junction.kind) {
            Junction.Kind.JOIN -> {
                val through = junction.states.values.firstOrNull()
                if (through != null) JunctionEntry(through, false) else null
            }
            Junction.Kind.SPLIT -> {
                run.findings.warnings +=
                    run.input.text.warning(
                        "junction-side-split",
                        IssueTarget.Junction(junction.id),
                        "id" to junction.id,
                        "coords" to blockCoords(block),
                    )
                null
            }
            Junction.Kind.CROSS -> null
        }
    }

    /** Returns the ids of junctions none of whose axis nodes is the end of any link. */
    internal fun unusedJunctions(
        points: Collection<String>,
        links: Collection<TrackChain.Link>,
    ): List<String> {
        val linked = HashSet<String>()
        for (link in links) {
            linked += link.from
            linked += link.to
        }
        return points
            .mapNotNull { node -> Nodes.parseJunction(node)?.let { it.id to node } }
            .groupBy({ it.first }, { it.second })
            .filterValues { nodes -> nodes.none { it in linked } }
            .keys
            .toList()
    }

    /** Returns the `begin` nodes of platforms that have `board` and `begin` but no `out`. */
    internal fun missingOut(stations: Collection<Station>): List<String> =
        stations.flatMap { station ->
            station.platforms
                .filter { it.board != null && it.begin != null && it.outbound == null }
                .map { Nodes.of(station.id, it.id, "begin") }
        }

    /** Returns the `begin` nodes of boarding platforms from which no eject of another station can be reached. */
    private fun boardingDeadEnds(
        store: Network,
        router: Router,
    ): List<String> =
        store.stations.values.flatMap { station ->
            station.platforms
                .filter { it.boards }
                .filter { platform ->
                    router
                        .reachableEjects(Nodes.of(station.id, platform.id, "begin"))
                        .none { Nodes.parse(it)?.stationId != station.id }
                }.map { Nodes.of(station.id, it.id, "begin") }
        }

    /** What one rebuild works with: the anchors found so far and where its edges and warnings go. */
    private class Run(
        val input: Input,
        /** Node pairs of hand-authored edges: no auto edge is built for them. */
        val manual: Set<Pair<String, String>>,
        val findings: Findings,
    ) {
        /** Every station point and junction state, by node id. */
        val points = LinkedHashMap<String, Point>()

        /** Travel directions set by a platform heading. */
        val explicit = LinkedHashMap<String, Vec3>()

        /** Travel directions read from the pocket rails. */
        val derived = LinkedHashMap<String, Vec3>()
    }

    private class BranchFrom(
        val node: String,
        val heading: Vec3,
        val oriented: Map<String, Vec3>,
    )

    /**
     * Returns the exits of a junction state: none for a `join`. The result is marked settled, so it is not asked
     * again, except at a turning `cross` whose other axis has no direction yet. A direction never changes once set
     * and the rails do not change during a rebuild, so a repeated search would give the same result.
     */
    private fun branches(
        run: Run,
        targetsByWorld: Map<String, WorldTargets>,
        from: BranchFrom,
    ): TrackChain.Branches {
        val point = branchPoint(run, from.node, targetsByWorld) ?: return TrackChain.Branches(emptyList(), true)
        return when (point.junction.kind) {
            Junction.Kind.SPLIT -> TrackChain.Branches(splitBranch(run, from, point), true)
            Junction.Kind.CROSS -> {
                val hops = crossTurns(run, targetsByWorld, point, from.oriented)
                val waiting =
                    point.junction.turns &&
                        point.junction.states.values
                            .any { it !in from.oriented }
                TrackChain.Branches(hops, settled = !waiting)
            }
            Junction.Kind.JOIN -> TrackChain.Branches(emptyList(), true)
        }
    }

    private fun branchPoint(
        run: Run,
        node: String,
        targetsByWorld: Map<String, WorldTargets>,
    ): BranchPoint? {
        val ref = Nodes.parseJunction(node)
        val anchor = run.points[node]
        val targets = anchor?.let { run.input.rails.world(it.world) }?.let { targetsByWorld[it.name] }
        val block = if (anchor != null && targets != null) RailPathfinder.nearestRail(targets.world, anchor) else null
        return if (ref != null && block != null && targets != null) {
            targets.junctionByBlock[block]?.let { BranchPoint(it, ref.axis, block, targets) }
        } else {
            null
        }
    }

    /**
     * Returns the exit of a `split` along its side rail. A side rail that turns back against the through travel
     * ([JunctionGeometry.turnsBack]) is reported and not linked.
     */
    private fun splitBranch(
        run: Run,
        from: BranchFrom,
        point: BranchPoint,
    ): List<TrackChain.BranchHop> {
        val findings = run.findings
        val text = run.input.text
        val node = from.node
        val block = point.block
        val world = point.worldTargets.world
        val sideAxis = if (point.axis == "x") "z" else "x"
        val side = sideNeighbour(world, block, sideAxis)
        val direction = side?.let { TrackChain.direction(block.toVec3(), it.toVec3()) }
        val goals = point.worldTargets.goals
        if (direction == null || onlyOwn(goals, block)) return emptyList()
        val departure = RailPathfinder.Departure(direction, skipStart = true)
        val result = findings.nearest(node, world, RailPathfinder.center(world, block), goals, departure)
        val path = result.path
        val target = IssueTarget.Junction(point.junction.id)
        return when {
            result.limitReached -> emptyList()
            path == null || path.size < 2 -> {
                val message = text.warning("junction-branch-dead-end", target, "id" to point.junction.id)
                findings.warnings += message
                emptyList()
            }
            JunctionGeometry.turnsBack(path.map { it.toVec3() }, from.heading) -> {
                val coords = blockCoords(block)
                findings.warnings +=
                    text.warning("junction-side-split", target, "id" to point.junction.id, "coords" to coords)
                emptyList()
            }
            else ->
                arrival(run, point.worldTargets, path, direction)
                    ?.let { listOf(TrackChain.BranchHop(it.node, path, it.heading)) }
                    .orEmpty()
        }
    }

    private fun crossTurns(
        run: Run,
        targetsByWorld: Map<String, WorldTargets>,
        point: BranchPoint,
        oriented: Map<String, Vec3>,
    ): List<TrackChain.BranchHop> {
        if (!point.junction.turns) return emptyList()
        val hops = ArrayList<TrackChain.BranchHop>()
        for ((otherAxis, otherState) in point.junction.states) {
            val otherHeading = if (otherAxis != point.axis) oriented[otherState] else null
            val straight = otherHeading?.let { step(run, targetsByWorld, otherState, it).singleOrNull() }
            if (straight != null) {
                hops += TrackChain.BranchHop(straight.to, straight.path, null, TrackChain.LinkKind.TURN)
            }
        }
        return hops
    }

    private fun sideNeighbour(
        world: RailWorld,
        block: BlockPos,
        axis: String,
    ): BlockPos? =
        RailPathfinder.connections(world, block).firstOrNull { neighbour ->
            if (axis == "x") neighbour.x != block.x else neighbour.z != block.z
        }

    private fun warnPointsOnJunctions(
        text: WarningText,
        targets: Map<String, WorldTargets>,
        warnings: MutableSet<RebuildWarning>,
    ) {
        for (worldTargets in targets.values) {
            val junctions = worldTargets.junctionByBlock.mapValues { it.value.id }
            GraphChecks.pointsOnJunctions(text, worldTargets.pointByBlock, junctions, warnings)
        }
    }

    private fun warnUnmarkedJunctions(
        run: Run,
        links: List<TrackChain.Link>,
        targets: Map<String, WorldTargets>,
    ) {
        for (link in links) {
            val world = link.path.firstOrNull()?.let { run.input.rails.world(it.world) }
            if (world == null || link.path.size <= 2) continue
            val worldTargets = targets[world.name]
            for (index in 1 until link.path.size - 1) {
                val point = link.path[index]
                val block = point.block()
                val marked = worldTargets?.isTarget(block) == true
                val switch = RailPathfinder.connections(world, block).size >= RailPathfinder.SWITCH_MIN_NEIGHBOURS
                if (!marked && switch) {
                    val target = run.input.blockTarget(point)
                    val coords = blockCoords(block)
                    run.findings.warnings += run.input.text.warning("junction-unmarked", target, "coords" to coords)
                }
            }
        }
    }

    /** Warns about junction axes with no direction. Junctions in [unused] are skipped: they have their own warning. */
    private fun warnUnorientedJunctions(
        text: WarningText,
        points: Set<String>,
        headings: Map<String, Vec3>,
        unused: Collection<String>,
        warnings: MutableSet<RebuildWarning>,
    ) {
        val axesById = LinkedHashMap<String, MutableList<String>>()
        for (node in points) {
            val ref = Nodes.parseJunction(node)
            if (ref != null && node !in headings && ref.id !in unused) {
                axesById.getOrPut(ref.id) { ArrayList() } += ref.axis
            }
        }
        for ((id, axes) in axesById) {
            val target = IssueTarget.Junction(id)
            val axisList = axes.joinToString(", ")
            warnings += text.warning("junction-unoriented", target, "id" to id, "axes" to axisList)
        }
    }

    private fun buildPockets(
        run: Run,
        station: Station,
        platform: Platform,
    ) {
        val points = run.points
        val explicit = run.explicit
        val derived = run.derived
        val id = platform.id
        val outNode = Nodes.of(station.id, id, "out")
        val inNode = Nodes.of(station.id, id, "in")

        platform.outbound?.let { points[outNode] = it }
        platform.inbound?.let { points[inNode] = it }

        val beginNode = Nodes.of(station.id, id, "begin")
        val ejectNode = Nodes.of(station.id, id, "eject")
        val beginPath = addPocketEdge(run, beginNode, outNode, platform.begin, platform.outbound)
        val ejectPath = addPocketEdge(run, inNode, ejectNode, platform.inbound, platform.eject)

        val face = platform.heading
        if (face != null) {
            val vector = face.direction
            if (platform.outbound != null) {
                explicit[outNode] = vector
            } else if (platform.inbound != null) {
                explicit[inNode] = vector
            }
        }

        val rails = run.input.rails
        platform.outbound?.let { out ->
            if (outNode !in explicit) PocketHeadings.fromBegin(rails, out, beginPath)?.let { derived[outNode] = it }
        }
        platform.inbound?.let { inbound ->
            if (inNode !in explicit) PocketHeadings.fromEject(rails, inbound, ejectPath)?.let { derived[inNode] = it }
        }
    }

    private fun addPocketEdge(
        run: Run,
        fromNode: String,
        toNode: String,
        from: Point?,
        to: Point?,
    ): List<Point>? {
        val world = from?.let { run.input.rails.world(it.world) }
        val path =
            if (from != null && to != null && world != null) run.findings.path(fromNode, world, from, to) else null
        if (path != null) {
            val speed = LinkSpeed.Result(run.input.speeds.station)
            createEdge(run, fromNode, toNode, path, speed)
        }
        return path
    }

    private fun createEdge(
        run: Run,
        fromNode: String,
        toNode: String,
        path: List<Point>,
        speed: LinkSpeed.Result,
    ) {
        if (path.size < 2 || (fromNode to toNode) in run.manual) return
        val id = "${safeId(fromNode)}->${safeId(toNode)}"
        // Speed, warnings and pocket directions read the full path, never the edge, so the edge can be stored
        // compressed.
        val stored = SwitchMarks.stored(run.input.rails, path)
        val points = stored.points.toMutableList()
        run.findings.edges +=
            Edge(id, fromNode, toNode, speed.cruise, points, true, stored.marks, speed.startSwitch, speed.endSwitch)
    }

    private fun logChain(
        debug: DebugLog,
        subject: UUID?,
        result: TrackChain.Result,
    ) {
        debug.log(DebugLevel.DECISION, subject) {
            "rebuild chain: ${result.links.size} link(s), ${result.headings.size} oriented node(s), " +
                "${result.conflicts.size} conflict(s)"
        }
        for (node in result.conflicts) {
            debug.log(DebugLevel.DECISION, subject) { "heading conflict at $node: the first direction stays" }
        }
    }

    private fun safeId(node: String): String = node.replace(':', '_').replace('.', '_')
}
