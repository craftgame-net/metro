package net.craftgame.metro.geo

/** The integer position of a block, usable as a map key. */
data class BlockPos(
    val x: Int,
    val y: Int,
    val z: Int,
) {
    /** Returns the block's minimum corner as a vector. */
    fun toVec3(): Vec3 = Vec3(x.toDouble(), y.toDouble(), z.toDouble())
}
