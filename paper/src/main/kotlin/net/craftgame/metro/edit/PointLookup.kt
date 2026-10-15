package net.craftgame.metro.edit

import net.craftgame.metro.data.DataStore
import net.craftgame.metro.data.Junction
import net.craftgame.metro.data.Nodes
import net.craftgame.metro.data.Platform
import net.craftgame.metro.data.Station
import net.craftgame.metro.geo.Point
import org.bukkit.block.Block
import org.bukkit.entity.Player

/** A marked point that the editor can open: a platform anchor or a junction. */
sealed interface EditPoint {
    data class Anchor(
        val station: Station,
        val platform: Platform,
        val role: String,
    ) : EditPoint {
        val ref: String get() = Nodes.stop(station.id, platform.id)
    }

    data class JunctionPoint(
        val junction: Junction,
    ) : EditPoint
}

/** Finds anchors and junctions by position. */
object PointLookup {
    /** Blocks between two samples of the ray. */
    private const val RAY_STEP = 0.25

    /** Returns every junction and anchor by a key built from its world and block coordinates. */
    fun markers(store: DataStore): Map<String, EditPoint> {
        val map = HashMap<String, EditPoint>()
        for (junction in store.junctions.values) {
            map[key(junction.location)] = EditPoint.JunctionPoint(junction)
        }
        for (station in store.stations.values) {
            station.platforms.forEach { platform ->
                for (role in Platform.ROLES) {
                    val anchor = platform.anchor(role) ?: continue
                    map[key(anchor)] = EditPoint.Anchor(station, platform, role)
                }
            }
        }
        return map
    }

    /**
     * Returns the first marker along the player's line of sight, within [EDIT_REACH] blocks, with its block.
     * The ray ignores blocks in the way, so a marker behind a wall is still found.
     */
    fun raycast(
        player: Player,
        markers: Map<String, EditPoint>,
    ): Pair<Block, EditPoint>? {
        val eye = player.eyeLocation
        val direction = eye.direction
        var travelled = 0.0
        while (travelled <= EDIT_REACH) {
            val block = eye.clone().add(direction.clone().multiply(travelled)).block
            markers[key(block.world.name, block.x, block.y, block.z)]?.let { return block to it }
            travelled += RAY_STEP
        }
        return null
    }

    /** Returns the junction in the block of [point], or null. */
    fun junctionAt(
        store: DataStore,
        point: Point,
    ): Junction? = store.junctions.values.firstOrNull { it.location.sameBlock(point) }

    /** Returns a rail anchor (`begin`, `out`, `in`, `eject`) in the block of [point], or null. */
    fun railAnchorAt(
        store: DataStore,
        point: Point,
    ): EditPoint.Anchor? =
        store.stations.values
            .asSequence()
            .filter { it.world == point.world }
            .flatMap { station ->
                station.platforms.flatMap { platform ->
                    Platform.RAIL_ROLES
                        .filter { role -> platform.anchor(role)?.let { sameBlock(it, point) } == true }
                        .map { role -> EditPoint.Anchor(station, platform, role) }
                }
            }.firstOrNull()

    /** Anchors are stored in their station's world, so only the block counts. */
    private fun sameBlock(
        a: Point,
        b: Point,
    ): Boolean = a.block() == b.block()

    private fun key(point: Point): String = key(point.world, point.blockX, point.blockY, point.blockZ)

    private fun key(
        world: String,
        x: Int,
        y: Int,
        z: Int,
    ): String = "$world:$x:$y:$z"
}
