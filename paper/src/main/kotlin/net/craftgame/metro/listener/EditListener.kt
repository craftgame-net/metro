package net.craftgame.metro.listener

import net.craftgame.metro.MetroPlugin
import net.craftgame.metro.edit.EDIT_REACH
import net.craftgame.metro.edit.MetroMenuHolder
import net.craftgame.metro.edit.PointMenu
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.EquipmentSlot

/** Opens the editor on crowbar clicks, sends editor menu clicks to the editors and clears per-player state on quit. */
class EditListener(
    private val plugin: MetroPlugin,
) : Listener {
    @EventHandler(ignoreCancelled = true)
    fun onInteract(event: PlayerInteractEvent) {
        val player = event.player
        val editing =
            event.action == Action.LEFT_CLICK_BLOCK &&
                plugin.editManager.isEditing(player) &&
                plugin.editManager.isCrowbar(player)
        // The event's clickedBlock often misses thin blocks like rails.
        val block = if (editing) player.getTargetBlockExact(EDIT_REACH) ?: event.clickedBlock else null
        if (block == null) return
        event.isCancelled = true
        plugin.editManager.open(player, block.location)
    }

    /**
     * Not `ignoreCancelled`: clicks into the air arrive already cancelled. The item use is always denied, so a
     * trident crowbar never charges a throw. The event fires once per hand.
     */
    @EventHandler(priority = EventPriority.LOW)
    fun onUse(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return
        val player = event.player
        if (!plugin.editManager.isEditing(player) || !plugin.editManager.isCrowbar(player)) return
        event.setUseItemInHand(Event.Result.DENY)
        event.setUseInteractedBlock(Event.Result.DENY)
        if (event.hand == EquipmentSlot.HAND) plugin.pointEditor.openAt(player)
    }

    /** Editor state and a debug subscription do not outlive the session. */
    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val player = event.player
        plugin.editManager.forget(player)
        plugin.pointEditor.forget(player)
        plugin.hoverLabel.forget(player)
        plugin.debugLog.stop(player.uniqueId)
    }

    /** A drag into the menu would drop the carried items into it, where they are lost. */
    @EventHandler
    fun onDrag(event: InventoryDragEvent) {
        if (event.inventory.holder !is MetroMenuHolder) return
        val top = event.view.topInventory.size
        if (event.rawSlots.any { it < top }) event.isCancelled = true
    }

    /** Cancelled in either inventory, so no shift-click moves items into the menu. */
    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val holder = event.inventory.holder as? MetroMenuHolder
        if (holder != null) {
            event.isCancelled = true
            val choice = if (event.clickedInventory == event.view.topInventory) holder.slots[event.rawSlot] else null
            val player = event.whoClicked as? Player
            if (choice != null && player != null) {
                player.closeInventory()
                dispatch(player, holder, choice)
            }
        }
    }

    private fun dispatch(
        player: Player,
        holder: MetroMenuHolder,
        choice: String,
    ) {
        val editManager = plugin.editManager
        when (holder.kind) {
            "actions" -> holder.block?.let { pickAction(player, it, choice) }
            "platform" -> editManager.handlePlatformPick(player, choice, holder.block)
            "platform-settings" -> editManager.handlePlatformSettings(player, choice, holder.block)
            "station-settings" -> editManager.handleStationSettings(player, choice, holder.block)
            "lines" -> editManager.handleLinePick(player, choice, holder.block)
            "stations" -> editManager.handleStationPick(player, choice, holder.block)
            "line-settings" -> editManager.handleLineSettings(player, choice, holder.block)
            "confirm-delete", "confirm-delete-platform", "confirm-delete-line" ->
                if (choice == "yes") confirm(player, holder)
            in PointMenu.KINDS -> plugin.pointEditor.handle(player, holder, choice)
        }
    }

    private fun pickAction(
        player: Player,
        block: Location,
        choice: String,
    ) {
        if (choice == "create-junction") {
            plugin.pointEditor.openCreateJunction(player, block)
        } else {
            plugin.editManager.handleAction(player, choice, block)
        }
    }

    private fun confirm(
        player: Player,
        holder: MetroMenuHolder,
    ) {
        when (holder.kind) {
            "confirm-delete" -> plugin.editManager.confirmDelete(player, holder.stationId)
            "confirm-delete-platform" ->
                plugin.editManager.confirmDeletePlatform(player, holder.stationId, holder.platform)
            "confirm-delete-line" -> plugin.editManager.confirmDeleteLine(player, holder.lineId)
        }
    }
}
