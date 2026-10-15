package net.craftgame.metro.ride

import net.craftgame.metro.MetroPlugin
import net.craftgame.metro.data.Nodes
import net.craftgame.metro.debug.DebugLevel
import net.craftgame.metro.geo.Point
import net.craftgame.metro.platform.PaperCart
import net.craftgame.metro.platform.PaperRideHost
import net.craftgame.metro.platform.Scheduler
import net.craftgame.metro.route.RoutePlan
import net.craftgame.metro.util.toLocation
import org.bukkit.Location
import org.bukkit.entity.Minecart
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType
import java.util.UUID

/** Spawns the carts of [Rides], runs them every tick and finds rides by Bukkit player or cart. Main thread only. */
class CartController(
    private val plugin: MetroPlugin,
) {
    val rides =
        Rides(
            plugin.dataStore,
            plugin.railWorlds,
            PaperRideHost(plugin),
            plugin.logger,
            { plugin.metroConfig.ejectDelay },
            plugin.debugLog,
        )

    /** The per-tick task; null when it is not running. */
    private var task: Scheduler.Task? = null

    /** Starts the per-tick task. */
    fun start() {
        task = plugin.scheduler.repeat(1L, 1L) { rides.tick() }
    }

    /** Cancels the per-tick task and ends every ride, see [Rides.stopAll]. */
    fun stop() {
        task?.cancel()
        task = null
        rides.stopAll()
    }

    /** Returns the player's ride, or null when they are not riding. */
    fun rideOf(player: Player): Ride? = rides.rideOf(player.uniqueId)

    /** Returns the ride of a cart, or null when the cart belongs to no active ride. */
    fun rideOf(cart: Minecart): Ride? = rides.rideOfCart(cart.uniqueId)

    /**
     * Spawns an empty cart at [point], tagged with the ride key so orphans can be found and removed.
     * Throws when the world of the point is not loaded.
     */
    fun spawnCart(point: Point): Minecart {
        val location = point.toLocation() ?: error("Cart world '${point.world}' is not loaded")
        val cart = location.world.spawn(location, Minecart::class.java)
        cart.persistentDataContainer.set(plugin.rideKey, PersistentDataType.STRING, UUID.randomUUID().toString())
        cart.isSlowWhenEmpty = false
        return cart
    }

    /**
     * Registers a ride for a cart that already holds the player and returns it. The cart waits [dwellTicks] ticks
     * before it moves. [origin] is where the player boarded, for rescue and as their last station. [plan] is the path
     * to follow, with the switches that may need reshaping.
     */
    fun startRide(
        player: Player,
        cart: Minecart,
        destId: String,
        destPlatform: String,
        origin: StationSpot,
        plan: RoutePlan,
        dwellTicks: Int,
    ): Ride {
        val ride =
            Ride(
                player.uniqueId,
                PaperCart(cart),
                destId,
                destPlatform,
                origin,
                plan.points,
                plan.segmentSpeeds,
                plugin.metroConfig.physics,
            ).apply { this.dwellTicks = dwellTicks }
        rides.start(ride, plan.switchCandidates)
        return ride
    }

    /** Teleports the player to their last station, see [rescueSpot]. Does nothing when none is remembered. */
    fun teleportToLastStation(player: Player) {
        val spot = plugin.playerState.lastStation(player.uniqueId)
        if (spot == null) {
            plugin.debugLog.log(DebugLevel.DECISION, player.uniqueId) { "rescue skipped: no remembered station" }
            return
        }
        player.teleport(rescueSpot(player, spot))
    }

    /**
     * Returns where to put a player who left a ride: the platform of [spot] (null: no remembered station).
     * Falls back to the world spawn when the station has no platform left or its world is not loaded, so no one is
     * left on the rails.
     */
    fun rescueSpot(
        player: Player,
        spot: StationSpot?,
    ): Location {
        val station = spot?.let { plugin.dataStore.stations[it.stationId] }
        val platform = spot?.let { station?.platform(it.platformId) } ?: station?.platforms?.firstOrNull()
        val location = platform?.let { it.teleportTarget() ?: it.begin }?.toLocation()
        if (location != null) {
            plugin.debugLog.log(DebugLevel.DECISION, player.uniqueId) {
                "rescue spot ${spot?.let { Nodes.stop(it.stationId, it.platformId) }} -> " +
                    "${location.blockX},${location.blockY},${location.blockZ}"
            }
            return location
        }
        val gone = spot?.stationId ?: "?"
        plugin.logger.warning("Metro: rescue station $gone is gone, sending ${player.name} to spawn")
        plugin.debugLog.log(DebugLevel.DECISION, player.uniqueId) {
            "rescue spot: station $gone is gone, using the spawn"
        }
        return player.world.spawnLocation
    }
}
