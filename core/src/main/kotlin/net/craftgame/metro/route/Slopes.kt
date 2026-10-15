package net.craftgame.metro.route

import net.craftgame.metro.config.Speeds
import net.craftgame.metro.geo.Point
import kotlin.math.abs

/** Finds rail slope steps on a dense path and works out the slower speed a cart runs at on them. */
object Slopes {
    /** The slope speed that leaves every speed as it is. */
    const val OFF = Double.POSITIVE_INFINITY

    /**
     * Returns true when a dense step of [dx], [dy] and [dz] blocks goes one block along an axis and one block up or
     * down, as a cart does on a slope rail. A step that changes height in any other way is not a slope.
     */
    fun isSlope(
        dx: Double,
        dy: Double,
        dz: Double,
    ): Boolean {
        if (!unit(dy)) return false
        return unit(dx) && zero(dz) || zero(dx) && unit(dz)
    }

    /** Returns true when the dense step from [from] to [to] is a slope step. */
    fun isSlope(
        from: Point,
        to: Point,
    ): Boolean = isSlope(to.x - from.x, to.y - from.y, to.z - from.z)

    /**
     * Returns [speed] limited to [slopeSpeed], in blocks per tick. The result is at least [Speeds.MIN] but never
     * above [speed].
     */
    fun cap(
        speed: Double,
        slopeSpeed: Double,
    ): Double = minOf(speed, slopeSpeed.coerceAtLeast(Speeds.MIN))

    private fun unit(value: Double): Boolean = abs(abs(value) - 1.0) < EPSILON

    private fun zero(value: Double): Boolean = abs(value) < EPSILON

    /** Dense points are interpolated, so their steps carry float noise. */
    private const val EPSILON = 1e-6
}
