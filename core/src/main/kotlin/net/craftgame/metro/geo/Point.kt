package net.craftgame.metro.geo

import kotlin.math.floor

/** A position in a world, in blocks. [world] is the world name, as data.yml stores it. */
data class Point(
    val world: String,
    val x: Double,
    val y: Double,
    val z: Double,
) {
    val blockX: Int get() = floor(x).toInt()
    val blockY: Int get() = floor(y).toInt()
    val blockZ: Int get() = floor(z).toInt()

    fun block(): BlockPos = BlockPos(blockX, blockY, blockZ)

    /** Returns the coordinates without the world. */
    fun toVec3(): Vec3 = Vec3(x, y, z)

    /** True when both points are in the same block of the same world. */
    fun sameBlock(other: Point): Boolean = world == other.world && block() == other.block()

    companion object {
        /** Returns [position] in [world]. */
        fun of(
            world: String,
            position: Vec3,
        ): Point = Point(world, position.x, position.y, position.z)
    }
}
