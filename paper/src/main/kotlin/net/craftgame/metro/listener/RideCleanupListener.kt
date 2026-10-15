package net.craftgame.metro.listener

import net.craftgame.metro.MetroPlugin
import net.craftgame.metro.util.isMetroCart
import org.bukkit.Bukkit
import org.bukkit.entity.Minecart
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.world.EntitiesLoadEvent

/** Removes Metro carts with no active ride, such as carts orphaned by a crash. */
class RideCleanupListener(
    private val plugin: MetroPlugin,
) : Listener {
    @EventHandler
    fun onEntitiesLoad(event: EntitiesLoadEvent) {
        purge(event.entities)
    }

    /** Removes the orphaned Metro carts of all loaded worlds. Call once on enable. */
    fun cleanupLoaded() {
        for (world in Bukkit.getWorlds()) {
            purge(world.entities)
        }
    }

    private fun purge(entities: Collection<org.bukkit.entity.Entity>) {
        for (entity in entities) {
            val cart = entity as? Minecart ?: continue
            if (cart.isMetroCart(plugin) && plugin.controller.rideOf(cart) == null) {
                cart.remove()
            }
        }
    }
}
