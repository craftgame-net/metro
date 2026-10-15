package net.craftgame.metro.route

import net.craftgame.metro.data.Network
import net.craftgame.metro.data.Nodes

/**
 * Finds station pairs where one way is much longer than the way back. The long way turns around far off,
 * so a crossing near these stations would shorten it.
 */
object Detours {
    /** The trip from station [from] to station [to] is [ratio] times longer than the trip back. */
    data class Detour(
        val from: String,
        val to: String,
        /** Blocks from [from] to [to]. */
        val there: Double,
        /** Blocks from [to] to [from]. */
        val back: Double,
    ) {
        /** [there] divided by [back]. */
        val ratio: Double get() = there / back
    }

    /** Runs a route search from every boarding platform, so it can take a while on a big network. */
    fun find(
        network: Network,
        router: Router,
    ): List<Detour> = pick(shortestTrips(network, router))

    /**
     * Returns at most [limit] detours with [Detour.ratio] of at least [minRatio], the largest ratio first.
     * [trips] maps (from station id, to station id) to the shortest route length in blocks. When [minRatio] is above 1,
     * only the long way of a pair can reach it, so each pair appears once.
     */
    fun pick(
        trips: Map<Pair<String, String>, Double>,
        minRatio: Double = MIN_RATIO,
        limit: Int = LIMIT,
    ): List<Detour> =
        trips
            .mapNotNull { (pair, there) ->
                val back = trips[pair.second to pair.first]
                if (back == null || back <= 0.0) null else Detour(pair.first, pair.second, there, back)
            }.filter { it.ratio >= minRatio }
            .sortedByDescending { it.ratio }
            .take(limit)

    /** Length in blocks of the quickest route between each pair of stations. */
    private fun shortestTrips(
        network: Network,
        router: Router,
    ): Map<Pair<String, String>, Double> {
        val trips = HashMap<Pair<String, String>, Double>()
        for (station in network.stations.values) {
            for (platform in station.platforms.filter { it.boards }) {
                val origin = Nodes.of(station.id, platform.id, "begin")
                for ((eject, route) in router.routesToEjects(origin, skipStation = station.id)) {
                    val target = Nodes.parse(eject)?.stationId ?: continue
                    val length = router.routeLength(route)
                    val key = station.id to target
                    if (length < (trips[key] ?: Double.MAX_VALUE)) trips[key] = length
                }
            }
        }
        return trips
    }

    /** Smallest ratio between the two ways of a pair that counts as a detour. */
    private const val MIN_RATIO = 2.0

    /** Most detours reported. */
    private const val LIMIT = 10
}
