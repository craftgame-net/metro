package net.craftgame.metro.data

import net.craftgame.metro.geo.Cardinal
import net.craftgame.metro.geo.Point

/**
 * One platform (side) of a station. [id] is stable within the station, so node ids survive reordering and deletion.
 * Anchors: `board` (button or plate that starts boarding), `begin` (where the cart spawns), `out` (where it joins the
 * main line), `in` (where it leaves the main line), `eject` (where it stops) and `land` (where the rider is set down).
 * [icon] is an item name such as `OAK_SIGN`.
 */
data class Platform(
    var id: String = "",
    var display: String? = null,
    var icon: String = "OAK_SIGN",
    var board: Point? = null,
    var begin: Point? = null,
    var outbound: Point? = null,
    var inbound: Point? = null,
    var eject: Point? = null,
    var land: Point? = null,
    /** Direction of travel past the platform: north, east, south or west. Null: unset, so it is read from the rails. */
    var heading: Cardinal? = null,
) {
    /** Returns the display name, or [fallback] when the display name is null or blank. */
    fun name(fallback: String): String = display?.takeIf { it.isNotBlank() } ?: fallback

    /** Number shown in the fallback name: a numeric id plus one, else the id. */
    fun fallbackNumber(): String = id.toIntOrNull()?.takeIf { it >= 0 }?.let { (it + 1).toString() } ?: id

    /** Returns the anchor for [role], or null when the anchor is not set or the role is unknown. */
    fun anchor(role: String): Point? =
        when (role) {
            "board" -> board
            "begin" -> begin
            "out" -> outbound
            "in" -> inbound
            "eject" -> eject
            "land" -> land
            else -> null
        }

    /** A null [point] clears it. Unknown roles are ignored. */
    fun setAnchor(
        role: String,
        point: Point?,
    ) {
        when (role) {
            "board" -> board = point
            "begin" -> begin = point
            "out" -> outbound = point
            "in" -> inbound = point
            "eject" -> eject = point
            "land" -> land = point
        }
    }

    /**
     * Riders can board here. The main line never links `begin` itself, so without `out` no route leaves the platform.
     * This is the single test for a boarding platform.
     */
    val boards: Boolean get() = board != null && begin != null && outbound != null

    /** Riders can get off here: `in` and `eject` are set. Counterpart of [boards]. */
    val alights: Boolean get() = inbound != null && eject != null

    /** Returns where a rider is set down: `land`, or `eject` when `land` is not set. */
    fun teleportTarget(): Point? = land ?: eject

    /** Returns a spot to send an editor to: the first set one of `land`, `board`, `begin`, `eject`. */
    fun visitSpot(): Point? = land ?: board ?: begin ?: eject

    companion object {
        val ROLES = listOf("board", "begin", "out", "in", "eject", "land")

        /** Roles that snap onto a rail block. `board` is a button or plate, `land` the floor. */
        val RAIL_ROLES = setOf("begin", "out", "in", "eject")
    }
}

/** A stop of a line, in one world, with one or more platforms. [icon] is an item name such as `COMPASS`. */
data class Station(
    val id: String,
    var display: String,
    var world: String,
    var icon: String = "COMPASS",
    /** A ride may start or end here, but never pass through. */
    var terminal: Boolean = false,
    val platforms: MutableList<Platform> = ArrayList(),
) {
    fun platform(id: String): Platform? = platforms.firstOrNull { it.id == id }

    /** Returns the first platform that has a visit spot, with that spot. Null when no platform has one. */
    fun firstVisitSpot(): Pair<Platform, Point>? {
        for (platform in platforms) {
            val spot = platform.visitSpot() ?: continue
            return platform to spot
        }
        return null
    }

    /** Returns the smallest non-negative number, as text, that no platform of this station uses as id. */
    fun nextPlatformId(): String {
        var n = 0
        while (platforms.any { it.id == n.toString() }) n++
        return n.toString()
    }

    /** Adds [platform], giving it a new id when its id is blank. Returns it. */
    fun addPlatform(platform: Platform = Platform()): Platform {
        if (platform.id.isBlank()) platform.id = nextPlatformId()
        platforms.add(platform)
        return platform
    }

    /** Returns the removed platform, or null for an unknown id. */
    fun removePlatform(id: String): Platform? {
        val index = platforms.indexOfFirst { it.id == id }
        if (index < 0) return null
        return platforms.removeAt(index)
    }

    /**
     * Gives a new id to each platform whose id is blank or repeats an earlier one.
     * Returns the replaced repeated ids as `old to new`.
     */
    fun ensurePlatformIds(): List<Pair<String, String>> {
        val used = HashSet<String>()
        val needsId = ArrayList<Int>()
        platforms.forEachIndexed { index, platform ->
            if (platform.id.isBlank() || !used.add(platform.id)) needsId.add(index)
        }
        val renamed = ArrayList<Pair<String, String>>()
        for (index in needsId) {
            val platform = platforms[index]
            var n = index
            while (n.toString() in used) n++
            val old = platform.id
            platform.id = n.toString()
            used.add(platform.id)
            if (old.isNotBlank()) renamed.add(old to platform.id)
        }
        return renamed
    }
}
