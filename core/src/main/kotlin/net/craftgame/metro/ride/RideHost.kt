package net.craftgame.metro.ride

import net.craftgame.metro.geo.Point
import java.util.UUID

/**
 * The platform side of the rides: the riders, what is saved about them, their chat and chunk loading.
 * [Rides] calls it on the server thread only. A call for a rider who is offline does nothing, unless it says
 * otherwise.
 */
interface RideHost {
    fun isOnline(rider: UUID): Boolean

    /** Returns the name of [rider] for log lines, or null when they are offline. */
    fun name(rider: UUID): String?

    /** Teleports [rider] to [target]. Does nothing when they are dead or the world of [target] is not loaded. */
    fun setDown(
        rider: UUID,
        target: Point,
    )

    /** Saves [spot] as the last station of [rider]. Works for an offline rider too. */
    fun rememberStation(
        rider: UUID,
        spot: StationSpot,
    )

    /** Forgets that [rider] must be rescued when they come back. Works for an offline rider too. */
    fun clearPendingRescue(rider: UUID)

    /** Teleports [rider] back to their last station, as rollback of a ride that ended early. */
    fun rescue(rider: UUID)

    /** Sends [rider] the prefixed lang text [key] with [placeholders] filled in. */
    fun message(
        rider: UUID,
        key: String,
        vararg placeholders: Pair<String, String>,
    )

    /**
     * Keeps chunk [chunkX], [chunkZ] of world [world] loaded until a matching [releaseChunk]. Called once per chunk
     * and ride.
     */
    fun holdChunk(
        world: String,
        chunkX: Int,
        chunkZ: Int,
    )

    /** Ends one hold taken with [holdChunk]. */
    fun releaseChunk(
        world: String,
        chunkX: Int,
        chunkZ: Int,
    )
}
