package net.craftgame.metro.route

import net.craftgame.metro.data.Junction
import net.craftgame.metro.data.Network
import net.craftgame.metro.data.Nodes
import net.craftgame.metro.debug.DebugLevel
import net.craftgame.metro.debug.DebugLog
import net.craftgame.metro.geo.BlockPos
import net.craftgame.metro.geo.Vec3
import net.craftgame.metro.util.blockCoords
import java.util.UUID

/** Checks on the finished track chain. Each one adds its findings to a warning set. */
internal object GraphChecks {
    /** A search reaching a junction's block stops there, so a point on that block is never linked. */
    fun pointsOnJunctions(
        text: WarningText,
        points: Map<BlockPos, String>,
        junctions: Map<BlockPos, String>,
        warnings: MutableSet<RebuildWarning>,
    ) {
        for ((key, node) in points) {
            val id = junctions[key] ?: continue
            val coords = blockCoords(key)
            val target = IssueTarget.Node(node)
            val point = "point" to Nodes.label(node)
            warnings += text.warning("point-on-junction", target, point, "id" to id, "coords" to coords)
        }
    }

    /**
     * Returns [result]'s links without the ramps that merge into a `join` against its travel (a turn of about 135
     * degrees for the cart). Each dropped ramp is reported in [warnings].
     */
    fun dropBackwardRamps(
        text: WarningText,
        store: Network,
        result: TrackChain.Result,
        warnings: MutableSet<RebuildWarning>,
        debugLog: DebugLog = DebugLog.OFF,
        subject: UUID? = null,
    ): List<TrackChain.Link> =
        result.links.filter { link ->
            val ref = Nodes.parseJunction(link.to)
            val join = ref?.let { store.junctions[it.id] }?.takeIf { it.kind == Junction.Kind.JOIN }
            val heading = result.headings[link.to]
            val backward =
                ref != null &&
                    join != null &&
                    heading != null &&
                    LinkSpeed.sideways(link.path, ref.axis) &&
                    rampTurnsBack(link, heading)
            if (backward) {
                val coords = blockCoords(link.path.last())
                val target = IssueTarget.Junction(join.id)
                val id = "id" to join.id
                warnings += text.warning("junction-join-backward", target, id, "coords" to coords)
                debugLog.log(DebugLevel.DECISION, subject) {
                    "ramp ${link.from} -> ${link.to} dropped: it enters join ${join.id} against its travel"
                }
            }
            !backward
        }

    private fun rampTurnsBack(
        link: TrackChain.Link,
        heading: Vec3,
    ): Boolean {
        val outward = link.path.asReversed().map { it.toVec3() }
        return JunctionGeometry.turnsBack(outward, heading * -1.0)
    }
}
