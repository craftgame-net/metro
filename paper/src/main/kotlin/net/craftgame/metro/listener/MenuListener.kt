package net.craftgame.metro.listener

import net.craftgame.metro.MetroPlugin
import net.craftgame.metro.data.Nodes
import net.craftgame.metro.gui.BoardingMenu
import net.craftgame.metro.gui.BoardingMenuHolder
import net.craftgame.metro.util.Text
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent

/** Lets a player pick in the boarding menu, turn its pages or leave it. Items can never be moved in or out of it. */
class MenuListener(
    private val plugin: MetroPlugin,
) : Listener {
    /** A drag into the menu would drop the carried items into it, where they are lost. */
    @EventHandler
    fun onDrag(event: InventoryDragEvent) {
        if (event.inventory.holder !is BoardingMenuHolder) return
        val top = event.view.topInventory.size
        if (event.rawSlots.any { it < top }) event.isCancelled = true
    }

    /** Cancelled in either inventory, so no shift-click moves items into the menu. */
    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val holder = event.inventory.holder as? BoardingMenuHolder
        if (holder != null) {
            event.isCancelled = true
            val stop = if (event.clickedInventory == event.view.topInventory) holder.slots[event.rawSlot] else null
            val player = event.whoClicked as? Player
            if (stop != null && player != null) pick(player, holder, stop, event.rawSlot)
        }
    }

    private fun pick(
        player: Player,
        holder: BoardingMenuHolder,
        stop: String,
        rawSlot: Int,
    ) {
        when (stop) {
            // The arrows turn the page in place, so the boarding stays open.
            BoardingMenu.PREVIOUS_ACTION, BoardingMenu.NEXT_ACTION ->
                BoardingMenu.turn(plugin, holder, if (stop == BoardingMenu.NEXT_ACTION) 1 else -1)
            // Closing the menu drops the boarding.
            BoardingMenu.EXIT_ACTION -> player.closeInventory()
            BoardingMenu.HINT_ACTION -> {
                player.closeInventory()
                holder.hints[rawSlot]?.let { hint ->
                    val text =
                        plugin.metroConfig.prefixed(
                            "boarding.hint-click",
                            "station" to hint.station,
                            "platform" to hint.platform,
                        )
                    player.sendMessage(Text.of(text))
                }
            }
            else -> confirmStop(player, holder, stop)
        }
    }

    private fun confirmStop(
        player: Player,
        holder: BoardingMenuHolder,
        stop: String,
    ) {
        val (stationId, platform) = Nodes.parseStop(stop) ?: return
        val station = plugin.dataStore.stations[stationId] ?: return

        player.closeInventory()
        plugin.boarding.confirm(player, holder, station, platform)
    }
}
