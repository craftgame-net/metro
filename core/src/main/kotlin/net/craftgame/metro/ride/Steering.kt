package net.craftgame.metro.ride

import net.craftgame.metro.geo.Vec3

/** Works out the velocity that moves a ridden cart toward a point on its route. It uses no server state. */
object Steering {
    data class Result(
        /** Velocity to set on the cart, in blocks per tick. */
        val velocity: Vec3,
        /** Value for the cart's max speed, in blocks per tick. */
        val maxSpeed: Double,
    )

    /** Highest max speed on a descent, as a multiple of the cruise speed. */
    const val MAX_DESCENT_SCALE = 2.0

    /** Blocks. Close enough to follow a turn, far enough to stay steady. */
    const val MIN_LOOKAHEAD = 0.75

    /** Squared blocks. A target closer than this counts as being on the cart. */
    private const val MIN_DIRECTION_SQ = 1e-8

    /** Squared blocks. A descent flatter than this has no horizontal pace to hold, so it is left as it is. */
    private const val MIN_HORIZONTAL_SQ = 1e-6

    /**
     * Returns how far ahead to aim, in blocks: at least one tick of travel at [speed] (blocks per tick), since aiming
     * at a point the cart passes this tick is unstable.
     */
    fun lookahead(speed: Double): Double = maxOf(MIN_LOOKAHEAD, speed)

    /**
     * Vanilla `OldMinecartBehavior.moveAlongTrack` moves a cart with a passenger by only this fraction of its
     * horizontal delta each tick, so the horizontal velocity is divided by it. Vanilla also caps the delta at 2.0
     * before scaling.
     */
    const val PASSENGER_SCALE = 0.75

    /**
     * Returns the velocity that moves a cart at [from] towards [target] at [speed] blocks per tick; [cruise] is the
     * ride's top speed. Null when the target is on the cart. On a descent the cart is not pushed downward (that
     * fights gravity): the rail carries the drop and the cap is raised so the horizontal pace holds.
     * [Result.maxSpeed] is at least [speed], which vanilla's per-axis clamp of the scaled movement needs.
     */
    fun steer(
        from: Vec3,
        target: Vec3,
        speed: Double,
        cruise: Double,
    ): Result? {
        val offset = target - from
        if (offset.lengthSquared() < MIN_DIRECTION_SQ) return null
        var direction = offset.normalized()
        var maxSpeed = cruise
        val horizontalSq = direction.x * direction.x + direction.z * direction.z
        if (direction.y < 0.0 && horizontalSq > MIN_HORIZONTAL_SQ) {
            direction = Vec3(direction.x, 0.0, direction.z).normalized()
            maxSpeed = (speed / Math.sqrt(horizontalSq)).coerceAtMost(cruise * MAX_DESCENT_SCALE)
        }
        val velocity =
            Vec3(direction.x * speed / PASSENGER_SCALE, direction.y * speed, direction.z * speed / PASSENGER_SCALE)
        return Result(velocity, maxSpeed.coerceAtLeast(speed))
    }
}
