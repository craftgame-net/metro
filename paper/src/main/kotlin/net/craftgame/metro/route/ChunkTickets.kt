package net.craftgame.metro.route

import net.craftgame.metro.debug.DebugLevel
import net.craftgame.metro.debug.DebugLog
import net.craftgame.metro.util.RefCounter
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin

/**
 * Plugin chunk tickets shared by rides and the preloader. Bukkit keeps one ticket per chunk and plugin,
 * so holds are counted here: added with the first hold, removed with the last.
 */
class ChunkTickets(
    private val plugin: Plugin,
    private val debugLog: DebugLog,
) {
    private val counter =
        RefCounter<Pair<String, Long>>(
            onFirst = { (world, key) ->
                Bukkit.getWorld(world)?.addPluginChunkTicket(ChunkSet.chunkX(key), ChunkSet.chunkZ(key), plugin)
                logTicket("added", world, key)
            },
            // An unloaded world has dropped its tickets already.
            onLast = { (world, key) ->
                Bukkit.getWorld(world)?.removePluginChunkTicket(ChunkSet.chunkX(key), ChunkSet.chunkZ(key), plugin)
                logTicket("removed", world, key)
            },
        )

    private fun logTicket(
        action: String,
        world: String,
        key: Long,
    ) {
        debugLog.log(DebugLevel.DETAIL, null) {
            "chunk ticket $action $world ${ChunkSet.chunkX(key)},${ChunkSet.chunkZ(key)} (${counter.size()} held)"
        }
    }

    /**
     * Adds one hold on a chunk of the world named [world]. The first hold adds Bukkit's plugin ticket, which keeps
     * the chunk loaded. Every call needs a matching [release], or the chunk stays loaded until [releaseAll].
     * Main thread only.
     */
    fun acquire(
        world: String,
        chunkX: Int,
        chunkZ: Int,
    ) = counter.acquire(world to ChunkSet.key(chunkX, chunkZ))

    /** Removes one hold. The last hold removes the plugin ticket. A chunk with no hold is ignored. Main thread only. */
    fun release(
        world: String,
        chunkX: Int,
        chunkZ: Int,
    ) = counter.release(world to ChunkSet.key(chunkX, chunkZ))

    /** Removes every hold and every plugin ticket. Call on shutdown. */
    fun releaseAll() {
        val held = counter.size()
        if (held > 0) debugLog.log(DebugLevel.DECISION, null) { "chunk tickets: released all $held" }
        counter.releaseAll()
    }
}
