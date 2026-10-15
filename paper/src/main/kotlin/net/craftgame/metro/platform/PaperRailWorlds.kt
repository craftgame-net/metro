package net.craftgame.metro.platform

import net.craftgame.metro.geo.BlockPos
import net.craftgame.metro.rail.RailShape
import net.craftgame.metro.rail.RailWorld
import net.craftgame.metro.rail.RailWorlds
import net.craftgame.metro.util.toBukkit
import net.craftgame.metro.util.toRailShape
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.block.data.Rail

/** [RailWorlds] over the worlds the server has loaded. */
class PaperRailWorlds : RailWorlds {
    override fun world(name: String): RailWorld? = Bukkit.getWorld(name)?.let(::PaperRailWorld)
}

private class PaperRailWorld(
    private val world: World,
) : RailWorld {
    override val name: String get() = world.name

    override fun railShape(pos: BlockPos): RailShape? {
        val rail = world.getBlockAt(pos.x, pos.y, pos.z).blockData as? Rail
        return rail?.shape?.toRailShape()
    }

    override fun setRailShape(
        pos: BlockPos,
        shape: RailShape,
    ) {
        val block = world.getBlockAt(pos.x, pos.y, pos.z)
        val rail = block.blockData as? Rail ?: return
        rail.shape = shape.toBukkit()
        block.blockData = rail
    }
}
