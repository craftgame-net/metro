package net.craftgame.metro.listener

import net.craftgame.metro.MetroPlugin
import net.craftgame.metro.data.Station
import net.craftgame.metro.util.toPoint
import org.bukkit.Tag
import org.bukkit.block.Block
import org.bukkit.block.data.Powerable
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot

/** Starts boarding when a player presses a platform's `board` button or plate. */
class InteractListener(
    private val plugin: MetroPlugin,
) : Listener {
    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        val block = event.clickedBlock ?: return
        val pressed = event.useInteractedBlock() != Event.Result.DENY && isPress(event, block)
        val target = if (pressed) boardAt(block) else null
        val player = event.player
        // A plate can release and press again under a player who never left it, for example when they jump or stand
        // on its edge. That fires PHYSICAL again and would reopen the menu after they close it.
        if (target != null && (event.action != Action.PHYSICAL || plugin.boarding.stepsOntoPlate(player, block))) {
            plugin.boarding.openMenu(player, target.first, target.second, player.location.clone())
        }
    }

    private fun isPress(
        event: PlayerInteractEvent,
        block: Block,
    ): Boolean =
        when {
            isButton(block) ->
                event.action == Action.RIGHT_CLICK_BLOCK &&
                    event.hand == EquipmentSlot.HAND &&
                    (block.blockData as? Powerable)?.isPowered != true
            isPlate(block) -> event.action == Action.PHYSICAL
            else -> false
        }

    private fun boardAt(block: Block): Pair<Station, String>? {
        val point = block.location.toPoint()
        for (station in plugin.dataStore.stations.values) {
            for (platform in station.platforms) {
                if (platform.board?.sameBlock(point) == true) return station to platform.id
            }
        }
        return null
    }

    private fun isButton(block: Block): Boolean = Tag.BUTTONS.isTagged(block.type)

    private fun isPlate(block: Block): Boolean = Tag.PRESSURE_PLATES.isTagged(block.type)
}
