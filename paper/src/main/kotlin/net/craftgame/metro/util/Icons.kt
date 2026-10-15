package net.craftgame.metro.util

import org.bukkit.Material

object Icons {
    /** True when [material] can be shown as an item icon. Reads the server's registries, so tests replace it. */
    fun usable(material: Material): Boolean = material.isItem && !material.isAir

    /** Returns the item name of the material [name] stands for, or null when it is unknown or not [usable]. */
    fun usableName(name: String): String? = Material.matchMaterial(name)?.takeIf(::usable)?.name
}
