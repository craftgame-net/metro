package net.craftgame.metro.util

import net.craftgame.metro.geo.Point
import net.craftgame.metro.geo.Vec3
import net.craftgame.metro.rail.RailShape
import net.craftgame.metro.route.IssueTarget
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.data.Rail
import org.bukkit.util.Vector

// The only place where Metro's geometry types and Bukkit's meet.

/** Needs a location with a world. */
fun Location.toPoint(): Point = Point(world.name, x, y, z)

fun Location.toVec3(): Vec3 = Vec3(x, y, z)

fun Vec3.toVector(): Vector = Vector(x, y, z)

/** Returns the world this point names, or null when it is not loaded. */
fun Point.loadedWorld(): World? = Bukkit.getWorld(world)

/** Returns this point as a location, or null when its world is not loaded. */
fun Point.toLocation(): Location? = loadedWorld()?.let { Location(it, x, y, z) }

/** Returns the block that contains this point, or null when its world is not loaded. */
fun Point.toBlock(): Block? = loadedWorld()?.getBlockAt(blockX, blockY, blockZ)

fun Rail.Shape.toRailShape(): RailShape = RailShape.valueOf(name)

fun RailShape.toBukkit(): Rail.Shape = Rail.Shape.valueOf(name)

/** Returns [block] as a teleport target, named by the world key as a command's world argument reads it. */
fun blockTarget(block: Block): IssueTarget.Block {
    val world = block.world.key.asString()
    return IssueTarget.Block(world, block.x, block.y, block.z)
}

/** Returns the item named [name] (see `Line.icon`), or [fallback] when there is none. */
fun iconMaterial(
    name: String,
    fallback: Material,
): Material = Material.matchMaterial(name) ?: fallback
