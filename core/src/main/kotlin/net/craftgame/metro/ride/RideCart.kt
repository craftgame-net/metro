package net.craftgame.metro.ride

import net.craftgame.metro.geo.Point
import net.craftgame.metro.geo.Vec3
import java.util.UUID

/**
 * The cart entity of one ride. Implementations wrap the platform's cart and must be called on the server thread
 * only. Speeds and velocities are in blocks per tick.
 */
interface RideCart {
    /** The entity id of the cart. */
    val id: UUID

    /** False once the cart is gone from the world (removed, destroyed or unloaded). */
    val isValid: Boolean

    /** Where the cart is now, in the cart's world. */
    val position: Point

    /** True when the player [rider] is online and sits in this cart. */
    fun carries(rider: UUID): Boolean

    fun setVelocity(velocity: Vec3)

    /** Sets the highest speed the platform lets the cart move at by itself. */
    fun setMaxSpeed(speed: Double)

    /** Turns the cart to [yaw] degrees (0 faces south, 90 faces west) and levels it. */
    fun face(yaw: Float)

    /** Sets every passenger down where the cart is and removes the cart. */
    fun remove()
}
