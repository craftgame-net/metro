package net.craftgame.metro.platform

import net.craftgame.metro.geo.Point
import net.craftgame.metro.geo.Vec3
import net.craftgame.metro.ride.RideCart
import net.craftgame.metro.util.toPoint
import net.craftgame.metro.util.toVector
import org.bukkit.Bukkit
import org.bukkit.entity.Minecart
import java.util.UUID

/** [RideCart] over a Bukkit [Minecart]. */
class PaperCart(
    private val minecart: Minecart,
) : RideCart {
    override val id: UUID get() = minecart.uniqueId

    override val isValid: Boolean get() = minecart.isValid

    override val position: Point get() = minecart.location.toPoint()

    override fun carries(rider: UUID): Boolean = Bukkit.getPlayer(rider)?.vehicle?.uniqueId == minecart.uniqueId

    override fun setVelocity(velocity: Vec3) {
        minecart.velocity = velocity.toVector()
    }

    override fun setMaxSpeed(speed: Double) {
        minecart.maxSpeed = speed
    }

    override fun face(yaw: Float) {
        minecart.setRotation(yaw, 0f)
    }

    override fun remove() {
        minecart.eject()
        minecart.remove()
    }
}
