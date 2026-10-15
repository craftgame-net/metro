package net.craftgame.metro.route

import net.craftgame.metro.data.Nodes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IssueTargetTest {
    private val noAnchor: (Nodes.Ref) -> IssueTarget.Block? = { null }

    @Test
    fun buildsTeleportCommands() {
        assertEquals("/metro junction teleport j1", IssueTarget.command(IssueTarget.Junction("j1"), noAnchor))
        val block = IssueTarget.Block("minecraft:overworld", 1, -2, 3)
        assertEquals("/metro teleport minecraft:overworld 1 -2 3", IssueTarget.command(block, noAnchor))
        assertEquals("/metro station teleport a p2", IssueTarget.command(IssueTarget.Platform("a", "p2"), noAnchor))
        assertEquals("/metro station teleport a", IssueTarget.command(IssueTarget.Station("a"), noAnchor))
    }

    @Test
    fun anchorIsOptional() {
        assertEquals("/metro station teleport a", IssueTarget.command(IssueTarget.Station("a")))
        assertEquals("/metro station teleport a p1", IssueTarget.command(IssueTarget.Node("a:p1:out")))
    }

    @Test
    fun junctionNodeGoesToItsJunction() {
        assertEquals("/metro junction teleport j1", IssueTarget.command(IssueTarget.Node("junction.j1.ns"), noAnchor))
    }

    @Test
    fun stationNodeGoesToItsAnchorBlock() {
        val anchors = { ref: Nodes.Ref -> if (ref.role == "out") IssueTarget.Block("minecraft:w", 4, 5, 6) else null }
        assertEquals("/metro teleport minecraft:w 4 5 6", IssueTarget.command(IssueTarget.Node("a:p1:out"), anchors))
    }

    @Test
    fun stationNodeWithoutAnchorGoesToItsPlatform() {
        assertEquals("/metro station teleport a p1", IssueTarget.command(IssueTarget.Node("a:p1:in"), noAnchor))
    }

    @Test
    fun unknownNodeHasNoCommand() {
        assertNull(IssueTarget.command(IssueTarget.Node("nonsense"), noAnchor))
    }
}
