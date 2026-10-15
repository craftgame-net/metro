package net.craftgame.metro.edit

import net.craftgame.metro.MetroPlugin
import net.craftgame.metro.data.Junction
import net.craftgame.metro.route.EdgeBuilder
import net.craftgame.metro.util.Text
import net.craftgame.metro.util.toPoint
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import java.util.UUID

/** The crowbar's right-click: settings of the anchor or junction under the crosshair. */
class PointEditor(
    private val plugin: MetroPlugin,
) {
    /** Server tick of the last menu open per player. */
    private val lastOpen = HashMap<UUID, Int>()

    /** Drops the stored state of [player], for example when they leave. */
    fun forget(player: Player) {
        lastOpen.remove(player.uniqueId)
    }

    /** Opens the menu of the anchor or junction under the player's crosshair, or tells them there is none. */
    fun openAt(player: Player) {
        // One right-click on a block can fire both a block and an air interaction, which would open the menu twice.
        val tick = Bukkit.getCurrentTick()
        val previous = lastOpen.put(player.uniqueId, tick)
        if (previous != null && tick - previous < DEBOUNCE_TICKS) return
        val found = PointLookup.raycast(player, PointLookup.markers(plugin.dataStore))
        if (found == null) {
            player.sendActionBar(Text.of(plugin.metroConfig.message("editor.point-none")))
            return
        }
        val block = found.first.location
        when (val point = found.second) {
            is EditPoint.Anchor -> PointMenu.openAnchor(plugin, player, block, point)
            is EditPoint.JunctionPoint -> PointMenu.openJunction(plugin, player, block, point.junction)
        }
    }

    /** Refused when a junction or a rail anchor already sits on that rail. */
    fun openCreateJunction(
        player: Player,
        block: Location,
    ) {
        val snapped = EdgeBuilder.snapToRail(plugin.railWorlds, block.toPoint())
        if (plugin.junctionEditor.occupied(player, snapped) || plugin.junctionEditor.anchored(player, snapped)) return
        PointMenu.openCreateJunction(plugin, player, block)
    }

    /** Runs the choice of a click in a point menu; `holder.kind` says which menu it was. */
    fun handle(
        player: Player,
        holder: MetroMenuHolder,
        choice: String,
    ) {
        val block = holder.block ?: return
        when (holder.kind) {
            PointMenu.ANCHOR -> handleAnchor(player, holder, choice, block)
            PointMenu.JUNCTION -> handleJunction(player, holder.junctionId, choice, block)
            PointMenu.CREATE_JUNCTION ->
                if (choice == "__back__") {
                    plugin.editManager.open(player, block)
                } else {
                    plugin.junctionEditor.createJunction(player, choice, null, block)
                }
            PointMenu.DELETE_JUNCTION ->
                if (choice == "yes") plugin.junctionEditor.confirmDeleteJunction(player, holder.junctionId)
        }
    }

    private fun handleAnchor(
        player: Player,
        holder: MetroMenuHolder,
        choice: String,
        block: Location,
    ) {
        val station = holder.stationId?.let { plugin.dataStore.stations[it] }
        val platform = station?.platform(holder.platform)
        if (station == null || platform == null) {
            player.sendMessage(Text.of(plugin.metroConfig.message("editor.platform-not-found")))
            return
        }
        if (choice == "clear") {
            plugin.anchorEditor.clearAnchor(player, station.id, platform.id, holder.role.orEmpty())
            return
        }
        plugin.editManager.select(player, station, platform.id)
        when (choice) {
            "select" -> EditMenu.openActions(plugin, player, block, station, platform.id)
            "platform-settings" -> EditMenu.openPlatformSettings(plugin, player, block, station, platform)
            "station-settings" -> EditMenu.openStationSettings(plugin, player, block, station)
        }
    }

    private fun handleJunction(
        player: Player,
        id: String?,
        choice: String,
        block: Location,
    ) {
        val junction = id?.let { plugin.dataStore.junctions[it] }
        if (junction == null) {
            player.sendMessage(Text.of(plugin.metroConfig.message("editor.junction-not-found")))
            return
        }
        val reopen =
            when (choice) {
                "turns" -> {
                    plugin.junctionEditor.setJunctionTurns(player, junction.id, null)
                    true
                }
                "speed" -> {
                    val command = "/metro junction speed ${junction.id} "
                    plugin.editManager.prompt(player, "editor.command-junction-speed", command)
                    false
                }
                "delete" -> {
                    PointMenu.openDeleteJunction(plugin, player, block, junction)
                    false
                }
                else -> setKind(player, junction, choice)
            }
        if (reopen) PointMenu.openJunction(plugin, player, block, junction)
    }

    /** True when [choice] is a junction kind. Picking the current kind changes nothing. */
    private fun setKind(
        player: Player,
        junction: Junction,
        choice: String,
    ): Boolean {
        val kind = Junction.kindOf(choice) ?: return false
        if (kind != junction.kind) plugin.junctionEditor.setJunctionKind(player, junction.id, choice)
        return true
    }

    private companion object {
        /** Ticks within which a second open for the same player is ignored. */
        const val DEBOUNCE_TICKS = 5
    }
}
