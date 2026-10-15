package net.craftgame.metro.util

import net.craftgame.metro.MetroPlugin
import org.bukkit.entity.Minecart

/** True for a cart that carries the plugin's ride key, i.e. one Metro spawned. */
fun Minecart.isMetroCart(plugin: MetroPlugin): Boolean = persistentDataContainer.has(plugin.rideKey)
