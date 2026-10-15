package net.craftgame.metro.platform

import net.craftgame.metro.MetroPlugin
import net.craftgame.metro.geo.Point
import net.craftgame.metro.ride.RideHost
import net.craftgame.metro.ride.StationSpot
import net.craftgame.metro.util.Text
import net.craftgame.metro.util.toLocation
import org.bukkit.Bukkit
import java.util.UUID

/** [RideHost] over the online players, the player state store, the lang texts and the plugin's chunk tickets. */
class PaperRideHost(
    private val plugin: MetroPlugin,
) : RideHost {
    override fun isOnline(rider: UUID): Boolean = Bukkit.getPlayer(rider) != null

    override fun name(rider: UUID): String? = Bukkit.getPlayer(rider)?.name

    override fun setDown(
        rider: UUID,
        target: Point,
    ) {
        val player = Bukkit.getPlayer(rider) ?: return
        val location = target.toLocation()
        if (!player.isDead && location != null) player.teleport(location)
    }

    override fun rememberStation(
        rider: UUID,
        spot: StationSpot,
    ) = plugin.playerState.setLastStation(rider, spot)

    override fun clearPendingRescue(rider: UUID) = plugin.playerState.clearPendingRescue(rider)

    override fun rescue(rider: UUID) {
        Bukkit.getPlayer(rider)?.let(plugin.controller::teleportToLastStation)
    }

    override fun message(
        rider: UUID,
        key: String,
        vararg placeholders: Pair<String, String>,
    ) {
        Bukkit.getPlayer(rider)?.sendMessage(Text.of(plugin.metroConfig.prefixed(key, *placeholders)))
    }

    override fun holdChunk(
        world: String,
        chunkX: Int,
        chunkZ: Int,
    ) = plugin.chunkTickets.acquire(world, chunkX, chunkZ)

    override fun releaseChunk(
        world: String,
        chunkX: Int,
        chunkZ: Int,
    ) = plugin.chunkTickets.release(world, chunkX, chunkZ)
}
