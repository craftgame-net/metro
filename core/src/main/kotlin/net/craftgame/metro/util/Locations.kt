package net.craftgame.metro.util

import net.craftgame.metro.geo.BlockPos
import net.craftgame.metro.geo.Point

/** Returns `x,y,z` of the block, or `-` for a null point. */
fun blockCoords(point: Point?): String = point?.let { blockCoords(it.block()) } ?: "-"

/** Returns `x,y,z`. */
fun blockCoords(block: BlockPos): String = "${block.x},${block.y},${block.z}"
