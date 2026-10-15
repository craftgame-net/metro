package net.craftgame.metro.ride

import net.craftgame.metro.MetroPlugin
import net.craftgame.metro.data.Nodes
import net.craftgame.metro.data.Station
import net.craftgame.metro.debug.DebugLevel
import net.craftgame.metro.geo.Point
import net.craftgame.metro.gui.BoardingMenuHolder
import net.craftgame.metro.route.RoutePlan
import net.craftgame.metro.route.RoutePlanner
import net.craftgame.metro.util.Text
import org.bukkit.Location
import org.bukkit.entity.Player

/** What a ride needs once the pick is valid: where it boards, where the cart spawns and the path. */
internal class Departure(
    val origin: StationSpot,
    val start: Point,
    val plan: RoutePlan,
)

/** Checks a pick in the boarding menu and plans its ride. */
internal class DeparturePlanner(
    private val plugin: MetroPlugin,
) {
    /** Checks the pick and plans the ride. Tells the player why not, unless the pick was stale. */
    fun plan(
        player: Player,
        state: BoardingMenuHolder,
        destination: Station,
        destPlatform: String,
    ): Departure? {
        val originStation = plugin.dataStore.stations[state.stationId]
        val origin = originStation?.platform(state.platformId)
        val target = destination.platform(destPlatform)
        return when {
            originStation == null || origin == null || target == null -> {
                plugin.debugLog.log(DebugLevel.DECISION, player.uniqueId) {
                    "pick ignored: the station or platform is gone"
                }
                null
            }
            !nearOrigin(player, state.originSpot) ->
                refuse(player, plugin.metroConfig.prefixed("boarding.moved-away"), "moved away from the plate")
            destination.id == originStation.id && destPlatform == state.platformId -> noRoute(player, destination)
            else -> {
                val route =
                    plugin.router.findRoute(
                        plugin.boarding.originNode(originStation, state.platformId),
                        plugin.boarding.destinationNode(destination, destPlatform),
                    )
                val start = origin.begin
                val end = target.eject ?: target.begin
                if (route.isNullOrEmpty() || start == null || end == null) {
                    noRoute(player, destination)
                } else {
                    val plan = RoutePlanner.plan(route, start, end, plugin.metroConfig.speed)
                    plugin.debugLog.log(DebugLevel.DECISION, player.uniqueId) {
                        val speeds = plan.segmentSpeeds.dropLast(1)
                        "route ${Nodes.stop(originStation.id, state.platformId)} -> " +
                            "${Nodes.stop(destination.id, destPlatform)}: ${route.size} edge(s), " +
                            "speed ${speeds.minOrNull()}..${speeds.maxOrNull()}, " +
                            "${plan.switchCandidates.size} switch candidate(s)"
                    }
                    Departure(StationSpot(originStation.id, state.platformId), start, plan)
                }
            }
        }
    }

    private fun noRoute(
        player: Player,
        destination: Station,
    ): Departure? =
        refuse(
            player,
            plugin.metroConfig.prefixed("boarding.no-route", "station" to destination.display),
            "no route to ${destination.id}",
        )

    private fun refuse(
        player: Player,
        text: String,
        reason: String,
    ): Departure? {
        plugin.debugLog.log(DebugLevel.DECISION, player.uniqueId) { "boarding refused: $reason" }
        player.sendMessage(Text.of(text))
        return null
    }

    /** An open menu blocks client movement, so this only catches a teleport or knockback while choosing. */
    private fun nearOrigin(
        player: Player,
        origin: Location,
    ): Boolean {
        val here = player.location
        return here.world == origin.world && here.distanceSquared(origin) <= MAX_DRIFT * MAX_DRIFT
    }

    private companion object {
        /** Blocks a player may be away from where they pressed and still board. */
        const val MAX_DRIFT = 3.0
    }
}
