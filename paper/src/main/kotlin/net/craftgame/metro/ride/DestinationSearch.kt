package net.craftgame.metro.ride

import net.craftgame.metro.MetroPlugin
import net.craftgame.metro.data.Edge
import net.craftgame.metro.data.Nodes
import net.craftgame.metro.data.Station
import net.craftgame.metro.geo.Point
import net.craftgame.metro.route.RoutePlanner

/** Works out which destinations the boarding menu offers, and the texts around them. */
object DestinationSearch {
    /**
     * Returns the menu options of platform [originPlatform]. [perPlatform] is the same for every platform of the
     * station, so callers may keep it and pass it in. Walking time to another platform, from the `board` anchors, is
     * added to the cost of rides that start there.
     */
    fun find(
        plugin: MetroPlugin,
        origin: Station,
        originPlatform: String,
        perPlatform: Map<String, Map<String, Destination>> = perPlatform(plugin, origin),
    ): Options {
        val boardable = origin.platforms.filter { it.boards }
        val here = origin.platform(originPlatform)?.let { it.board ?: it.begin }
        val walk =
            boardable.associate { platform ->
                val there = platform.board
                platform.id to (if (here != null && there != null) RideTime.walkTicks(distance(here, there)) else 0)
            }
        return Destinations.assign(perPlatform, originPlatform, walk)
    }

    /**
     * Returns, for each boarding platform id of [origin], its fastest destination per target station id. Runs a
     * route search per platform.
     */
    fun perPlatform(
        plugin: MetroPlugin,
        origin: Station,
    ): Map<String, Map<String, Destination>> =
        origin.platforms.filter { it.boards }.associate { it.id to fromPlatform(plugin, origin, it.id) }

    /** Returns the display name of a platform, or a numbered fallback name when it has none. */
    fun platformName(
        plugin: MetroPlugin,
        station: Station,
        platformId: String,
    ): String {
        val fallback =
            plugin.metroConfig.message(
                "item.platform-fallback",
                "n" to (station.platform(platformId)?.fallbackNumber() ?: platformId),
            )
        return station.platform(platformId)?.name(fallback) ?: fallback
    }

    /** Returns the message that tells why a player cannot board at [station] with these [options]. */
    fun refusal(
        plugin: MetroPlugin,
        station: Station,
        options: Options,
    ): String {
        val platforms =
            options.unavailable
                .map { it.platform }
                .distinct()
                .joinToString { platformName(plugin, station, it) }
        return if (options.unavailable.isEmpty()) {
            plugin.metroConfig.prefixed("boarding.no-destinations", "station" to station.display)
        } else {
            plugin.metroConfig.prefixed("boarding.other-platforms", "platforms" to platforms)
        }
    }

    /**
     * Ejects are taken in reachability order, so ties between platforms of a station resolve the same way each
     * time.
     */
    private fun fromPlatform(
        plugin: MetroPlugin,
        origin: Station,
        originPlatform: String,
    ): Map<String, Destination> {
        val originNode = plugin.boarding.originNode(origin, originPlatform)
        val originLine = plugin.dataStore.lineIdOf(origin.id)
        return plugin.router
            .routesToEjects(originNode, skipStation = origin.id)
            .mapNotNull { (node, route) -> destination(plugin, originNode, originLine, node, route) }
            .groupBy { it.station.id }
            .mapValues { (_, group) -> group.minBy { it.ticks } }
    }

    private fun destination(
        plugin: MetroPlugin,
        originNode: String,
        originLine: String?,
        node: String,
        route: List<Edge>,
    ): Destination? {
        val ref = Nodes.parse(node)
        val station = ref?.let { plugin.dataStore.stations[it.stationId] }
        val ticks = station?.let { estimate(plugin, originNode, node, route) }
        return if (ref == null || station == null || ticks == null) {
            null
        } else {
            val line = plugin.dataStore.lineIdOf(station.id)
            Destination(station, ref.platform, ticks, line, line == null || line == originLine)
        }
    }

    private fun estimate(
        plugin: MetroPlugin,
        from: String,
        to: String,
        route: List<Edge>,
    ): Int? {
        val start = plugin.router.anchorPoint(from)
        val end = plugin.router.anchorPoint(to)
        return if (start == null || end == null) {
            null
        } else {
            val config = plugin.metroConfig
            val plan = RoutePlanner.plan(route, start, end, config.speed)
            config.departureTicks + RideTime.ticks(plan.points, plan.segmentSpeeds, config.physics)
        }
    }

    /** Returns the distance in blocks between [a] and [b], or [FAR] when they are in different worlds. */
    private fun distance(
        a: Point,
        b: Point,
    ): Double = if (a.world == b.world) a.toVec3().distance(b.toVec3()) else FAR

    /** Blocks. Large enough that a walk never covers it. */
    private const val FAR = 1_000_000.0
}
