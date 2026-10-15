package net.craftgame.metro.geo

import kotlin.math.floor
import kotlin.math.sqrt

/** An immutable vector or position without a world, in blocks. */
data class Vec3(
    val x: Double,
    val y: Double,
    val z: Double,
) {
    operator fun plus(other: Vec3): Vec3 = Vec3(x + other.x, y + other.y, z + other.z)

    operator fun minus(other: Vec3): Vec3 = Vec3(x - other.x, y - other.y, z - other.z)

    operator fun times(factor: Double): Vec3 = Vec3(x * factor, y * factor, z * factor)

    fun dot(other: Vec3): Double = x * other.x + y * other.y + z * other.z

    fun lengthSquared(): Double = dot(this)

    fun length(): Double = sqrt(lengthSquared())

    fun distanceSquared(other: Vec3): Double = (this - other).lengthSquared()

    fun distance(other: Vec3): Double = sqrt(distanceSquared(other))

    /** Returns this vector scaled to length 1. A zero vector gives NaN components. */
    fun normalized(): Vec3 {
        val length = length()
        return Vec3(x / length, y / length, z / length)
    }

    /** Returns the block that contains this position. */
    fun block(): BlockPos = BlockPos(floor(x).toInt(), floor(y).toInt(), floor(z).toInt())

    companion object {
        val ZERO = Vec3(0.0, 0.0, 0.0)
    }
}
