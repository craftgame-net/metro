package net.craftgame.metro.route

import net.craftgame.metro.geo.Point

/** Tells which path points are sharp turns, where a cart has to slow down. */
object Turns {
    /** True when the path turns sharply at point [i]. The ends and zero-length steps never count. */
    fun isSharp(
        path: List<Point>,
        i: Int,
    ): Boolean {
        if (i <= 0 || i >= path.size - 1) return false
        return isSharp(
            path[i].x - path[i - 1].x,
            path[i].z - path[i - 1].z,
            path[i + 1].x - path[i].x,
            path[i + 1].z - path[i].z,
        )
    }

    /** True when the step (ax, az) followed by the step (bx, bz) turns by [SHARP_DEG] degrees or more. */
    fun isSharp(
        ax: Double,
        az: Double,
        bx: Double,
        bz: Double,
    ): Boolean {
        val la = Math.hypot(ax, az)
        val lb = Math.hypot(bx, bz)
        return la >= MIN_STEP &&
            lb >= MIN_STEP &&
            (ax * bx + az * bz) / (la * lb) <= SHARP_COS
    }

    /**
     * Smallest turn angle in degrees that counts as sharp. Paths run through block centres, so a turn is 0, 90 or 180
     * degrees.
     */
    const val SHARP_DEG = 90.0

    private const val ANGLE_EPSILON = 1e-6

    private val SHARP_COS = Math.cos(Math.toRadians(SHARP_DEG - ANGLE_EPSILON))

    private const val MIN_STEP = 1e-6
}
