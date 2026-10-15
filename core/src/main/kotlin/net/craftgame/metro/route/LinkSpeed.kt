package net.craftgame.metro.route

import net.craftgame.metro.config.Speeds
import net.craftgame.metro.data.Junction
import net.craftgame.metro.data.Network
import net.craftgame.metro.data.Nodes
import net.craftgame.metro.geo.Point

/** The speeds baked into an auto edge at rebuild. Every result is capped by [Speeds.max]. */
object LinkSpeed {
    /** The station end of a link. */
    data class End(
        val stationId: String,
        val lineId: String?,
        /** Blocks per tick. Null: the line has no speed of its own. */
        val lineSpeed: Double?,
    )

    /**
     * Speeds in blocks per tick. [startSwitch] is for leaving a `split` by its side rail, [endSwitch] for merging
     * into a `join` from the side; each is null when the edge has no switch zone at that end.
     */
    data class Result(
        val cruise: Double,
        val startSwitch: Double? = null,
        val endSwitch: Double? = null,
    )

    /**
     * Returns the speeds for a link, with the cruise speed from [track]. A branch or ramp has a junction end, so it
     * runs at `speed.cruise`; the junction speed applies only in its switch zone.
     */
    fun of(
        store: Network,
        link: TrackChain.Link,
        speeds: Speeds,
    ): Result {
        if (link.kind == TrackChain.LinkKind.TURN) return Result(turn(speeds))
        val from = end(store, link.from)
        val to = end(store, link.to)
        val stops =
            from != null &&
                from.stationId == to?.stationId &&
                stopsOnTrack(store, from.stationId, link.path)
        val startJunction = Nodes.parseJunction(link.from)?.let { store.junctions[it.id] }
        val start = startJunction?.takeIf { link.kind == TrackChain.LinkKind.BRANCH }
        val endRef = Nodes.parseJunction(link.to)
        val join = endRef?.let { store.junctions[it.id] }?.takeIf { it.kind == Junction.Kind.JOIN }
        val ramp = endRef != null && join != null && sideways(link.path, endRef.axis)
        return Result(
            cruise = track(from, to, stops, speeds),
            startSwitch = switchZone(start != null, start?.speed, speeds),
            endSwitch = switchZone(ramp, join?.speed, speeds),
        )
    }

    /** Returns the junction speed (see [branch]) when [atSwitch] is true, else null. */
    fun switchZone(
        atSwitch: Boolean,
        junctionSpeed: Double?,
        speeds: Speeds,
    ): Double? = if (atSwitch) branch(junctionSpeed, speeds) else null

    /** True when [path] arrives at a junction along the other axis than [axis]. */
    fun sideways(
        path: List<Point>,
        axis: String,
    ): Boolean {
        val arrival =
            if (path.size < 2) null else TrackChain.direction(path[path.size - 2].toVec3(), path.last().toVec3())
        return arrival != null && JunctionGeometry.axisOf(arrival) != axis
    }

    /**
     * A null end is a junction. Between two stations of one line with its own speed: that speed, up to
     * [Speeds.maxCruise]; between other points: [Speeds.cruise]. Inside one station: [Speeds.station] when a platform
     * sits on the link ([stops]), else [Speeds.through] or the line speed if slower.
     */
    fun track(
        from: End?,
        to: End?,
        stops: Boolean,
        speeds: Speeds,
    ): Double {
        val speed =
            when {
                from == null || to == null -> speeds.cruise
                from.stationId != to.stationId -> between(from, to, speeds)
                stops -> speeds.station
                else -> minOf(speeds.through, from.lineSpeed ?: speeds.through)
            }
        return speed.coerceAtMost(speeds.max)
    }

    /** Returns [junctionSpeed], or `speed.branch` when it is null, capped by `speed.max`. */
    fun branch(
        junctionSpeed: Double?,
        speeds: Speeds,
    ): Double = (junctionSpeed ?: speeds.branch).coerceAtMost(speeds.max)

    /** Returns the speed of a turn at a crossing: the cruise speed. The sharp-turn cap slows it further on the ride. */
    fun turn(speeds: Speeds): Double = speeds.cruise.coerceAtMost(speeds.max)

    private fun between(
        from: End,
        to: End,
        speeds: Speeds,
    ): Double {
        val lineSpeed = from.lineSpeed
        return if (from.lineId != null && from.lineId == to.lineId && lineSpeed != null) {
            minOf(lineSpeed, speeds.maxCruise)
        } else {
            speeds.cruise
        }
    }

    private fun end(
        store: Network,
        node: String,
    ): End? {
        val stationId = Nodes.parse(node)?.stationId ?: return null
        val lineId = store.lineIdOf(stationId)
        return End(stationId, lineId, lineId?.let { store.lines[it]?.speed })
    }

    private fun stopsOnTrack(
        store: Network,
        stationId: String,
        path: List<Point>,
    ): Boolean {
        val blocks = path.mapTo(HashSet()) { it.block() }
        val stops =
            store.stations[stationId]
                ?.platforms
                .orEmpty()
                .flatMap { listOfNotNull(it.begin, it.eject) }
        return stops.any { it.block() in blocks }
    }
}
