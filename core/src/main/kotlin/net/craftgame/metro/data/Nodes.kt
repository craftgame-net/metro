package net.craftgame.metro.data

/**
 * Builds and parses graph node ids: `stationId:platformId:role` and `junction.<id>.<axis>`.
 * A junction id has no colon, so [parse] returns null for it.
 */
object Nodes {
    const val JUNCTION_PREFIX = "junction."

    /** `station:platform:role`. */
    private const val PARTS_WITH_PLATFORM = 3

    data class Ref(
        val stationId: String,
        val platform: String,
        val role: String,
    )

    data class JunctionRef(
        val id: String,
        val axis: String,
    )

    /** Returns the node id of a station point. [role] is `begin`, `out`, `in` or `eject`. */
    fun of(
        stationId: String,
        platform: String,
        role: String,
    ): String = "$stationId:$platform:$role"

    /** Splits a station node id. Returns null for a junction node or any other text. */
    fun parse(node: String): Ref? {
        val parts = node.split(':')
        return when (parts.size) {
            // The form without a platform id means platform "0".
            2 -> Ref(parts[0], "0", parts[1])
            PARTS_WITH_PLATFORM -> Ref(parts[0], parts[1], parts[2])
            else -> null
        }
    }

    /** Returns text for people: `role station:platform` for a station node, else [node] unchanged. */
    fun label(node: String): String = parse(node)?.let { "${it.role} ${stop(it.stationId, it.platform)}" } ?: node

    /** Returns the id of a platform of a station: `stationId:platformId`. */
    fun stop(
        stationId: String,
        platform: String,
    ): String = "$stationId:$platform"

    /** Splits `stationId:platformId`. Returns null when [stop] does not have exactly two parts. */
    fun parseStop(stop: String): Pair<String, String>? {
        val parts = stop.split(':')
        if (parts.size != 2) return null
        return parts[0] to parts[1]
    }

    /** Returns the node id of a junction state on one [axis]. */
    fun junction(
        id: String,
        axis: String,
    ): String = "$JUNCTION_PREFIX$id.$axis"

    /** Splits a junction node id. Returns null for a station node or any other text. */
    fun parseJunction(node: String): JunctionRef? {
        if (!node.startsWith(JUNCTION_PREFIX)) return null
        val rest = node.removePrefix(JUNCTION_PREFIX)
        val dot = rest.lastIndexOf('.')
        return if (dot <= 0 || dot == rest.length - 1) {
            null
        } else {
            JunctionRef(rest.substring(0, dot), rest.substring(dot + 1))
        }
    }
}
