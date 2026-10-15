package net.craftgame.metro.ride

import net.craftgame.metro.MetroPlugin
import net.craftgame.metro.config.Permissions
import net.craftgame.metro.data.Nodes
import net.craftgame.metro.data.Platform
import net.craftgame.metro.data.Station
import net.craftgame.metro.debug.DebugLevel
import net.craftgame.metro.geo.Point
import net.craftgame.metro.gui.BoardingMenu
import net.craftgame.metro.gui.BoardingMenuHolder
import net.craftgame.metro.util.StampedCache
import net.craftgame.metro.util.Text
import net.craftgame.metro.util.blockCoords
import net.craftgame.metro.util.toPoint
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.block.Block
import org.bukkit.entity.Minecart
import org.bukkit.entity.Player
import org.bukkit.util.BoundingBox

/**
 * Opens the destination menu at a platform and turns a pick into a ride. The boarding under way lives in the menu's
 * [BoardingMenuHolder], so closing the menu leaves nothing behind.
 */
class BoardingService(
    private val plugin: MetroPlugin,
) {
    private val returned = ReturnGuard()
    private val planner = DeparturePlanner(plugin)

    /** Menu options by (station id, platform id). */
    private val menus = StampedCache<Pair<String, String>, Options>()

    /** Destinations by station id, then platform id, then target station id. Shared by the platforms of a station. */
    private val routes = StampedCache<String, Map<String, Map<String, Destination>>>()

    init {
        // Forgets players who left the plate, so the guard map does not keep entries of players who went away.
        // The task runs as long as the plugin is enabled; the scheduler stops it on disable.
        plugin.scheduler.repeat(PRUNE_PERIOD_TICKS, PRUNE_PERIOD_TICKS) {
            returned.prune { id, plate -> Bukkit.getPlayer(id)?.let { standsOn(it, plate) } == true }
        }
    }

    private fun choosing(player: Player): Boolean = player.openInventory.topInventory.holder is BoardingMenuHolder

    /**
     * Returns false when the player has not left the plate they last pressed, so a press that only repeats the
     * earlier one is ignored. Otherwise it remembers [plate] and returns true.
     */
    fun stepsOntoPlate(
        player: Player,
        plate: Block,
    ): Boolean {
        val point = plate.location.toPoint()
        if (returned.holds(player.uniqueId, point)) return false
        returned.set(player.uniqueId, point)
        return true
    }

    private fun standsOn(
        player: Player,
        plate: Point,
    ): Boolean {
        val world = Bukkit.getWorld(plate.world) ?: return false
        val block = world.getBlockAt(plate.block().x, plate.block().y, plate.block().z)
        return player.world == world && BoundingBox.of(block).overlaps(player.boundingBox)
    }

    /** Returns the graph node of the platform's `begin`, where rides from it start. */
    fun originNode(
        station: Station,
        platform: String,
    ): String = Nodes.of(station.id, platform, "begin")

    /** Returns the graph node of the platform's `eject`, where rides to it end. */
    fun destinationNode(
        station: Station,
        platform: String,
    ): String = Nodes.of(station.id, platform, "eject")

    /**
     * Opens the destination menu for a player who pressed the `board` anchor of a platform. [originSpot] is where
     * the player stands; the ride only starts if they are still near it when they pick. Returns false, with a
     * message where the player needs one, when they may not board or have no destination.
     */
    fun openMenu(
        player: Player,
        station: Station,
        platformId: String,
        originSpot: Location,
    ): Boolean {
        val options = if (mayBoard(player, station.platform(platformId))) optionsFor(station, platformId) else null
        return when {
            options == null -> false
            !options.boardable ->
                deny(player, DestinationSearch.refusal(plugin, station, options), "no destination from ${station.id}")
            else -> {
                val holder = BoardingMenuHolder(station.id, platformId, originSpot)
                BoardingMenu.open(plugin, player, station, options, holder)
                true
            }
        }
    }

    /** Tells the player why they may not board, unless they are already choosing or the platform is gone. */
    private fun mayBoard(
        player: Player,
        platform: Platform?,
    ): Boolean =
        when {
            !player.hasPermission(Permissions.RIDE) ->
                deny(player, plugin.metroConfig.prefixed("boarding.no-permission"), "no permission")
            plugin.controller.rideOf(player) != null ->
                deny(player, plugin.metroConfig.prefixed("boarding.already-riding"), "already riding")
            choosing(player) || platform == null -> false
            platform.begin == null ->
                deny(player, plugin.metroConfig.message("boarding.no-begin"), "platform has no begin")
            else -> true
        }

    private fun optionsFor(
        station: Station,
        platformId: String,
    ): Options {
        val stamp = plugin.dataStore.version to plugin.metroConfig.generation
        return menus.get(station.id to platformId, stamp) {
            val perPlatform = routes.get(station.id, stamp) { DestinationSearch.perPlatform(plugin, station) }
            DestinationSearch.find(plugin, station, platformId, perPlatform)
        }
    }

    /** Sends [text] to the player and returns false, so a refusal reads as one line. [reason] is for the debug log. */
    private fun deny(
        player: Player,
        text: String,
        reason: String,
    ): Boolean {
        plugin.debugLog.log(DebugLevel.DECISION, player.uniqueId) { "boarding refused: $reason" }
        player.sendMessage(Text.of(text))
        return false
    }

    /** Puts the player into the cart. Returns true when the player ends up in it. */
    private fun seat(
        player: Player,
        cart: Minecart,
    ): Boolean {
        if (player.vehicle?.uniqueId == cart.uniqueId) return true
        if (player.isGliding) player.isGliding = false
        if (player.isFlying) player.isFlying = false
        if (player.isSprinting) player.isSprinting = false
        cart.addPassenger(player)
        return player.vehicle?.uniqueId == cart.uniqueId
    }

    /**
     * Starts a ride to platform [destPlatform] of [destination] after the player picked it in the menu opened with
     * [state]. Spawns a cart at `begin`, seats the player and hands the ride to [CartController], which owns the
     * cart from then on. A second call for the same menu is ignored. Returns false when no ride started; the player
     * is told why unless the pick was stale.
     */
    fun confirm(
        player: Player,
        state: BoardingMenuHolder,
        destination: Station,
        destPlatform: String,
    ): Boolean {
        if (state.selected || !player.isOnline) {
            plugin.debugLog.log(DebugLevel.DECISION, player.uniqueId) { "pick ignored: the menu was already used" }
            return false
        }
        state.selected = true
        val departure = planner.plan(player, state, destination, destPlatform)
        return departure != null && launch(player, departure, destination.id, destPlatform)
    }

    /** Spawns the cart, seats the player and starts the ride. Returns false when the player could not be seated. */
    private fun launch(
        player: Player,
        departure: Departure,
        destId: String,
        destPlatform: String,
    ): Boolean {
        val cart = plugin.controller.spawnCart(departure.start)
        val seated = seat(player, cart)
        plugin.debugLog.log(DebugLevel.DECISION, player.uniqueId) {
            "boarding launch: cart at ${blockCoords(departure.start)}, seated=$seated"
        }
        if (seated) {
            val dwell = plugin.metroConfig.departureTicks
            plugin.controller.startRide(
                player,
                cart,
                destId,
                destPlatform,
                departure.origin,
                departure.plan,
                dwell,
            )
        } else {
            player.sendMessage(Text.of(plugin.metroConfig.prefixed("boarding.seat-failed")))
            cart.remove()
        }
        return seated
    }

    private companion object {
        const val PRUNE_PERIOD_TICKS = 4L
    }
}
