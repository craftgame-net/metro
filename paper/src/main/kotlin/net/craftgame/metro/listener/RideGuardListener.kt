package net.craftgame.metro.listener

import net.craftgame.metro.MetroPlugin
import net.craftgame.metro.ride.RideAbort
import net.craftgame.metro.ride.RideState
import org.bukkit.entity.Minecart
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDismountEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.event.vehicle.VehicleEnterEvent

/** Keeps a rider in the cart until the ride ends, and puts back riders who quit or die during a ride. */
class RideGuardListener(
    private val plugin: MetroPlugin,
) : Listener {
    @EventHandler
    fun onDismount(event: EntityDismountEvent) {
        val player = event.entity as? Player ?: return
        if (isMoving(player)) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onSneak(event: PlayerToggleSneakEvent) {
        if (!event.isSneaking) return
        val player = event.player
        if (isMoving(player)) {
            event.isCancelled = true
        }
    }

    /** Getting out while the cart waits at eject is a normal arrival, so only earlier phases count. */
    private fun isMoving(player: Player): Boolean {
        val ride = plugin.controller.rideOf(player) ?: return false
        return ride.state != RideState.ARRIVED
    }

    @EventHandler
    fun onVehicleEnter(event: VehicleEnterEvent) {
        val cart = event.vehicle as? Minecart ?: return
        val ride = plugin.controller.rideOf(cart) ?: return
        val entered = event.entered
        if (entered.uniqueId != ride.playerId) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        droppedOut(event.player)
    }

    @EventHandler
    fun onDeath(event: PlayerDeathEvent) {
        droppedOut(event.entity)
    }

    /** One already waiting at eject has arrived, so its ride completes normally. */
    private fun droppedOut(player: Player) {
        val ride = plugin.controller.rideOf(player) ?: return
        if (ride.state == RideState.ARRIVED) {
            plugin.controller.rides.finish(ride)
        } else {
            plugin.controller.rides.abort(ride, RideAbort.RIDER_LEFT, rollback = false)
            plugin.playerState.markPendingRescue(player.uniqueId, ride.origin)
        }
    }

    @EventHandler
    fun onRespawn(event: PlayerRespawnEvent) {
        val player = event.player
        if (!plugin.playerState.isPendingRescue(player.uniqueId)) return
        event.respawnLocation = plugin.controller.rescueSpot(player, plugin.playerState.lastStation(player.uniqueId))
        plugin.playerState.clearPendingRescue(player.uniqueId)
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        if (!plugin.playerState.isPendingRescue(player.uniqueId)) return
        plugin.controller.teleportToLastStation(player)
        plugin.playerState.clearPendingRescue(player.uniqueId)
    }
}
