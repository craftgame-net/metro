package net.craftgame.metro.util

import net.craftgame.metro.data.SnapshotWriter
import net.craftgame.metro.data.YamlSection
import net.craftgame.metro.ride.StationSpot
import org.yaml.snakeyaml.error.YAMLException
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Keeps the last station each player used, and who dropped out mid-ride so they can be put back on their platform.
 * Only rescues are written to disk (in the background). Call [close] on shutdown.
 */
class PlayerStateStore(
    /** players.yml in the plugin folder. */
    private val file: File,
    private val logger: Logger,
) {
    private val lastStations = HashMap<UUID, StationSpot>()
    private val pendingRescue = HashMap<UUID, StationSpot>()
    private val writer = SnapshotWriter(file, logger)

    /** Replaces the in-memory state with the pending rescues in players.yml. Waits for queued writes first. */
    fun load() {
        writer.flush()
        lastStations.clear()
        pendingRescue.clear()
        if (!file.exists()) return
        val section = read()?.section("pending-rescue") ?: return
        for (raw in section.keys) {
            val id = runCatching { UUID.fromString(raw) }.getOrNull() ?: continue
            readSpot(section.section(raw))?.let { pendingRescue[id] = it }
        }
    }

    private fun readSpot(entry: YamlSection?): StationSpot? {
        val station = entry?.string("station")
        val platform = entry?.string("platform")
        return if (station != null && platform != null) StationSpot(station, platform) else null
    }

    /** Null, with an error logged, when the file cannot be read; the rescues in it are then lost. */
    private fun read(): YamlSection? {
        val error =
            try {
                return YamlSection.read(file)
            } catch (e: IOException) {
                e
            } catch (e: YAMLException) {
                e
            }
        logger.log(Level.SEVERE, "Metro: could not read ${file.name}", error)
        return null
    }

    /** Queues the pending rescues for writing on the background writer thread. */
    fun save() {
        val rescues = LinkedHashMap<String, Any?>()
        pendingRescue.forEach { (id, spot) ->
            val entry = LinkedHashMap<String, Any?>()
            entry["station"] = spot.stationId
            entry["platform"] = spot.platformId
            rescues[id.toString()] = entry
        }
        val root = LinkedHashMap<String, Any?>()
        if (rescues.isNotEmpty()) root["pending-rescue"] = rescues
        writer.submit(YamlSection.write(root))
    }

    /** Waits for queued writes and stops the writer thread. */
    fun close() {
        writer.close()
    }

    /** Returns the spot to put the player back on: an outstanding rescue first, else the last one used, else null. */
    fun lastStation(id: UUID): StationSpot? = pendingRescue[id] ?: lastStations[id]

    /** Remembers [spot] in memory only. */
    fun setLastStation(
        id: UUID,
        spot: StationSpot,
    ) {
        lastStations[id] = spot
    }

    /** Records that the player left a ride. A null [spot] means their last station; without one nothing is recorded. */
    fun markPendingRescue(
        id: UUID,
        spot: StationSpot? = null,
    ) {
        val target = spot ?: lastStations[id] ?: return
        pendingRescue[id] = target
        save()
    }

    /** True when the player left a ride and has not been put back yet. */
    fun isPendingRescue(id: UUID): Boolean = id in pendingRescue

    fun clearPendingRescue(id: UUID) {
        if (pendingRescue.remove(id) != null) save()
    }
}
