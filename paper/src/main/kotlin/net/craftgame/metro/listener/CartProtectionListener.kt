package net.craftgame.metro.listener

import net.craftgame.metro.MetroPlugin
import net.craftgame.metro.util.isMetroCart
import org.bukkit.entity.Minecart
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.vehicle.VehicleDamageEvent
import org.bukkit.event.vehicle.VehicleDestroyEvent
import org.bukkit.event.vehicle.VehicleEntityCollisionEvent

/** Nobody may push, hit or break a Metro cart. */
class CartProtectionListener(
    private val plugin: MetroPlugin,
) : Listener {
    @EventHandler
    fun onCollision(event: VehicleEntityCollisionEvent) {
        val cart = event.vehicle as? Minecart ?: return
        if (cart.isMetroCart(plugin)) event.isCancelled = true
    }

    @EventHandler
    fun onVehicleDamage(event: VehicleDamageEvent) {
        val cart = event.vehicle as? Minecart ?: return
        if (cart.isMetroCart(plugin)) event.isCancelled = true
    }

    @EventHandler
    fun onVehicleDestroy(event: VehicleDestroyEvent) {
        val cart = event.vehicle as? Minecart ?: return
        if (cart.isMetroCart(plugin)) event.isCancelled = true
    }

    @EventHandler
    fun onEntityDamage(event: EntityDamageEvent) {
        val cart = event.entity as? Minecart ?: return
        if (cart.isMetroCart(plugin)) event.isCancelled = true
    }
}
