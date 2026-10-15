package net.craftgame.metro.route

import net.craftgame.metro.data.Nodes

/** The place a finding points at, so the player can be sent there with a teleport command. */
sealed interface IssueTarget {
    data class Junction(
        val id: String,
    ) : IssueTarget

    /** A block position. [world] is the world key (`minecraft:overworld`), as a command's world argument reads it. */
    data class Block(
        val world: String,
        val x: Int,
        val y: Int,
        val z: Int,
    ) : IssueTarget

    data class Platform(
        val stationId: String,
        val platformId: String,
    ) : IssueTarget

    data class Station(
        val stationId: String,
    ) : IssueTarget

    data class Node(
        val nodeId: String,
    ) : IssueTarget

    companion object {
        /**
         * Returns the teleport command for [target]. A station point goes to its anchor block when [anchor] returns
         * one, else to its platform. Null for a node that is neither a station point nor a junction.
         */
        fun command(
            target: IssueTarget,
            anchor: (Nodes.Ref) -> Block? = { null },
        ): String? =
            when (target) {
                is Junction -> "/metro junction teleport ${target.id}"
                is Block -> "/metro teleport ${target.world} ${target.x} ${target.y} ${target.z}"
                is Platform -> "/metro station teleport ${target.stationId} ${target.platformId}"
                is Station -> "/metro station teleport ${target.stationId}"
                is Node -> nodeCommand(target.nodeId, anchor)
            }

        private fun nodeCommand(
            node: String,
            anchor: (Nodes.Ref) -> Block?,
        ): String? {
            val junction = Nodes.parseJunction(node)
            val ref = Nodes.parse(node)
            return when {
                junction != null -> command(Junction(junction.id), anchor)
                ref != null -> command(anchor(ref) ?: Platform(ref.stationId, ref.platform), anchor)
                else -> null
            }
        }
    }
}
