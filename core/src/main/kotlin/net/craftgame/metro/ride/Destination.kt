package net.craftgame.metro.ride

import net.craftgame.metro.data.Station

/** A station a rider can reach from a platform. */
data class Destination(
    val station: Station,
    /** Id of the platform where the ride ends. */
    val platform: String,
    /** Estimated ride time in ticks, including the departure delay. */
    val ticks: Int,
    /** Id of the station's line, or null when it has none. */
    val line: String?,
    /** True when the station is on the line of the origin. A station without a line counts as on the origin line. */
    val onOriginLine: Boolean,
)

/** A [destination] that is better reached from another platform of the origin station. */
data class Hint(
    val destination: Destination,
    /** Id of that other platform. */
    val platform: String,
)

/**
 * The content of the boarding menu of one platform. [own] are the destinations that this platform reaches fastest.
 * [slower] can be ridden from here too, but [Hint.platform] is faster. [unavailable] can only be reached from
 * [Hint.platform].
 */
data class Options(
    val own: List<Destination>,
    val slower: List<Hint>,
    val unavailable: List<Hint>,
) {
    /** True when a ride can start from this platform. */
    val boardable: Boolean get() = own.isNotEmpty() || slower.isNotEmpty()
}

/** Splits the destinations of a station's platforms into the options of one platform. */
object Destinations {
    private val order = compareBy<Destination> { it.ticks }

    /**
     * Splits the destinations of all platforms into the options of platform [current]. [walk] maps a platform id to
     * the ticks to walk there from [current]. A tie goes to [current]. A station that another platform reaches
     * faster is listed as slower when [current] reaches it too, else as unavailable.
     */
    fun assign(
        perPlatform: Map<String, Map<String, Destination>>,
        current: String,
        walk: Map<String, Int> = emptyMap(),
    ): Options {
        val here = perPlatform[current].orEmpty()

        fun cost(hint: Hint): Int {
            val walking = if (hint.platform == current) 0 else walk[hint.platform] ?: 0
            return hint.destination.ticks + walking
        }
        val best =
            perPlatform
                .flatMap { (platform, stations) -> stations.values.map { Hint(it, platform) } }
                .groupBy { it.destination.station.id }
                .values
                .map { group -> group.minWith(compareBy({ cost(it) }, { it.platform != current })) }
        val (own, other) = best.partition { it.platform == current }
        val (slower, unavailable) = other.partition { it.destination.station.id in here }
        val hintOrder = compareBy(order) { hint: Hint -> hint.destination }
        return Options(
            own.map { it.destination }.sortedWith(order),
            slower.map { Hint(here.getValue(it.destination.station.id), it.platform) }.sortedWith(hintOrder),
            unavailable.sortedWith(hintOrder),
        )
    }
}
