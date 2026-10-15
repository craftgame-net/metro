package net.craftgame.metro.edit

import net.craftgame.metro.MetroPlugin
import net.craftgame.metro.data.Nodes
import net.craftgame.metro.data.Platform
import net.craftgame.metro.geo.Point
import net.craftgame.metro.route.EdgeBuilder
import net.craftgame.metro.util.Text
import net.craftgame.metro.util.blockCoords
import net.craftgame.metro.util.toPoint
import org.bukkit.Location
import org.bukkit.entity.Player

/** Sets and clears platform anchors. */
class AnchorEditor(
    private val plugin: MetroPlugin,
) {
    /**
     * Sets the anchor [role] of a platform at [location], adjusted by role (see [transformAnchor]). Refuses with a
     * message to [player] for an unknown station, platform or role, another world, or a rail anchor on a junction.
     * On success it saves and offers a rebuild.
     */
    fun setAnchor(
        player: Player,
        stationId: String,
        platformId: String,
        role: String,
        location: Location,
    ) {
        val station = plugin.dataStore.stations[stationId]
        val platform = station?.platform(platformId)
        val applied = transformAnchor(role, location.toPoint())
        when {
            station == null -> player.sendMessage(Text.of(message("editor.station-not-found")))
            platform == null -> player.sendMessage(Text.of(message("editor.platform-not-found")))
            // Anchors are stored without a world and read back in the station's, so one set in another world would
            // end up in the wrong place.
            location.world?.name != station.world -> {
                val text = message("editor.anchor-other-world", "station" to stationId, "world" to station.world)
                player.sendMessage(Text.of(text))
            }
            applied == null -> player.sendMessage(Text.of(message("editor.unknown-role", "role" to role)))
            onJunction(player, role, applied) -> Unit
            else -> {
                platform.setAnchor(role, applied)
                changed(player, "editor.anchor-saved", Nodes.stop(station.id, platform.id), role, blockCoords(applied))
            }
        }
    }

    /** Removes the anchor [role] of a platform, then saves and offers a rebuild. Tells [player] when nothing is set. */
    fun clearAnchor(
        player: Player,
        stationId: String,
        platformId: String,
        role: String,
    ) {
        val station = plugin.dataStore.stations[stationId]
        val platform = station?.platform(platformId)
        when {
            station == null -> player.sendMessage(Text.of(message("editor.station-not-found")))
            platform == null -> player.sendMessage(Text.of(message("editor.platform-not-found")))
            role !in Platform.ROLES -> player.sendMessage(Text.of(message("editor.unknown-role", "role" to role)))
            platform.anchor(role) == null -> {
                val ref = Nodes.stop(station.id, platform.id)
                val text = message("editor.anchor-missing", "platform" to ref, "role" to role)
                player.sendMessage(Text.of(text))
            }
            else -> {
                platform.setAnchor(role, null)
                changed(player, "editor.anchor-removed", Nodes.stop(station.id, platform.id), role, "")
            }
        }
    }

    private fun changed(
        player: Player,
        key: String,
        platform: String,
        role: String,
        value: String,
    ) {
        if (!plugin.editManager.save(player)) return
        val text = message(key, "platform" to platform, "role" to role, "value" to value)
        player.sendMessage(Text.of(text))
        plugin.editManager.promptRebuild(player)
    }

    /**
     * True when the anchor was refused: a rail anchor on a junction's rail never links, because searches stop at
     * the junction.
     */
    private fun onJunction(
        player: Player,
        role: String,
        applied: Point,
    ): Boolean {
        val junction = if (role in Platform.RAIL_ROLES) PointLookup.junctionAt(plugin.dataStore, applied) else null
        junction?.let { player.sendMessage(Text.of(message("editor.anchor-on-junction", "id" to it.id))) }
        return junction != null
    }

    /**
     * Returns the point to store for [role]: `board` as clicked, rail roles snapped to the nearest rail, `land` one
     * block above the clicked block. Null for an unknown role.
     */
    private fun transformAnchor(
        role: String,
        point: Point,
    ): Point? =
        when (role) {
            "board" -> point
            "begin", "out", "in", "eject" -> EdgeBuilder.snapToRail(plugin.railWorlds, point)
            "land" -> point.copy(y = point.y + 1.0)
            else -> null
        }

    private fun message(
        key: String,
        vararg placeholders: Pair<String, String>,
    ): String = plugin.metroConfig.message(key, *placeholders)
}
