package net.craftgame.metro.edit

import net.craftgame.metro.MetroPlugin
import net.craftgame.metro.command.ChatPages
import net.craftgame.metro.data.Nodes
import net.craftgame.metro.data.Station
import net.craftgame.metro.geo.Point
import net.craftgame.metro.route.IssueTarget
import net.craftgame.metro.util.Text
import net.craftgame.metro.util.loadedWorld
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/** Lists stations and junctions with teleport links. Changes no data. */
class NetworkBrowser(
    private val plugin: MetroPlugin,
) {
    private val pages = ChatPages(plugin)

    /** A null [lineId] lists every station. Players get [page], the console everything. */
    fun listStations(
        sender: CommandSender,
        lineId: String?,
        page: Int,
    ) {
        val store = plugin.dataStore
        if (lineId != null && store.lines[lineId] == null) {
            sender.sendMessage(Text.of(message("editor.line-not-found")))
            return
        }
        val stations = if (lineId == null) store.stations.values.toList() else store.stationsOf(lineId)
        sender.sendMessage(Text.of(message("editor.station-list-header", "count" to stations.size.toString())))
        if (stations.isEmpty()) sender.sendMessage(Text.of(message("editor.list-empty")))
        val lines =
            stations.map { station ->
                val spot = station.firstVisitSpot()?.second
                val line =
                    message(
                        "editor.station-list-entry",
                        "id" to station.id,
                        "name" to station.display,
                        "line" to (store.lineIdOf(station.id) ?: "-"),
                        "platforms" to station.platforms.size.toString(),
                        "coords" to (spot?.let { coords(it) } ?: message("editor.list-no-coords")),
                    )
                clickable(line, spot != null, IssueTarget.command(IssueTarget.Station(station.id)))
            }
        val command = if (lineId == null) "/metro station list" else "/metro station list $lineId"
        pages.send(sender, lines, page) { "$command $it" }
    }

    /** Lists the junctions with teleport links. Players get [page], the console everything. */
    fun listJunctions(
        sender: CommandSender,
        page: Int,
    ) {
        val junctions = plugin.dataStore.junctions.values
        sender.sendMessage(Text.of(message("editor.junction-list-header", "count" to junctions.size.toString())))
        if (junctions.isEmpty()) sender.sendMessage(Text.of(message("editor.list-empty")))
        val lines =
            junctions.map { junction ->
                val line =
                    message(
                        "editor.junction-list-entry",
                        "id" to junction.id,
                        "kind" to junction.kind.name.lowercase(),
                        "world" to junction.world,
                        "coords" to coords(junction.location),
                    )
                clickable(line, true, IssueTarget.command(IssueTarget.Junction(junction.id)))
            }
        pages.send(sender, lines, page) { "/metro junction list $it" }
    }

    /** A null [platformId] picks the first platform that has a spot to visit. */
    fun teleportToStation(
        player: Player,
        stationId: String,
        platformId: String?,
    ) {
        val station = plugin.dataStore.stations[stationId]
        val platform = platformId?.let { station?.platform(it) }
        val visit = if (platformId == null) station?.firstVisitSpot() else platform?.visitSpot()?.let { platform to it }
        when {
            station == null -> player.sendMessage(Text.of(message("editor.station-not-found")))
            platformId != null && platform == null -> player.sendMessage(Text.of(message("editor.platform-not-found")))
            visit == null -> {
                val target = platform?.let { label(station, it.id) } ?: station.id
                player.sendMessage(Text.of(message("editor.no-visit-spot", "target" to target)))
            }
            else -> teleport(player, centred(player, visit.second, 0.0), label(station, visit.first.id))
        }
    }

    /** Teleports [player] to the junction [id], or tells them it does not exist. */
    fun teleportToJunction(
        player: Player,
        id: String,
    ) {
        val junction = plugin.dataStore.junctions[id]
        if (junction == null) {
            player.sendMessage(Text.of(message("editor.junction-not-found")))
            return
        }
        teleport(player, centred(player, junction.location, ABOVE_RAIL), junction.id)
    }

    /** Teleports [player] to a block of [world]. */
    fun teleportToBlock(
        player: Player,
        world: World,
        x: Int,
        y: Int,
        z: Int,
    ) {
        val label = "${world.name} $x $y $z"
        val anchor = Point(world.name, x.toDouble(), y.toDouble(), z.toDouble())
        teleport(player, centred(player, anchor, ABOVE_RAIL), label)
    }

    private fun teleport(
        player: Player,
        target: Location?,
        label: String,
    ) {
        if (target == null) {
            player.sendMessage(Text.of(message("editor.teleport-failed", "target" to label)))
            return
        }
        // Async: a sync teleport into an unloaded chunk would load it on the main thread and stall the tick. The
        // result message is sent when the teleport future completes.
        player.teleportAsync(target).thenAccept { ok ->
            val key = if (ok) "editor.teleported" else "editor.teleport-failed"
            player.sendMessage(Text.of(message(key, "target" to label)))
        }
    }

    /**
     * Returns [anchor] moved to the middle of its block and up by [lift] blocks, with the player's yaw and pitch.
     * Null if the world is not loaded.
     */
    private fun centred(
        player: Player,
        anchor: Point,
        lift: Double,
    ): Location? {
        val world = anchor.loadedWorld() ?: return null
        val location = player.location
        return Location(
            world,
            anchor.blockX + HALF,
            anchor.blockY + lift,
            anchor.blockZ + HALF,
            location.yaw,
            location.pitch,
        )
    }

    private fun label(
        station: Station,
        platformId: String,
    ): String = Nodes.stop(station.id, platformId)

    private fun clickable(
        line: String,
        active: Boolean,
        command: String?,
    ) = if (active && command != null) Text.run(line, command, message("editor.click-to-teleport")) else Text.of(line)

    private fun coords(point: Point): String = "${point.blockX} ${point.blockY} ${point.blockZ}"

    private fun message(
        key: String,
        vararg placeholders: Pair<String, String>,
    ): String = plugin.metroConfig.message(key, *placeholders)

    private companion object {
        /** Offset to the middle of a block, in blocks. */
        const val HALF = 0.5

        /** Blocks above the block's floor where a player is put, so they do not stand in the rail. */
        const val ABOVE_RAIL = 0.1
    }
}
