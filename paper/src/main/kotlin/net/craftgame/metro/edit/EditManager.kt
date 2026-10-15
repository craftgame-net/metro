package net.craftgame.metro.edit

import net.craftgame.metro.MetroPlugin
import net.craftgame.metro.command.RebuildWarnings
import net.craftgame.metro.data.Ids
import net.craftgame.metro.data.Line
import net.craftgame.metro.data.Network
import net.craftgame.metro.data.Nodes
import net.craftgame.metro.data.Platform
import net.craftgame.metro.data.Station
import net.craftgame.metro.geo.Cardinal
import net.craftgame.metro.route.EdgeBuilder
import net.craftgame.metro.util.Icons
import net.craftgame.metro.util.Text
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import java.util.UUID

/** Blocks that the crowbar and the editor commands reach when they look for a target block. */
internal const val EDIT_REACH = 6

/**
 * Keeps each player's editor mode and active selection, and applies their edits. Edits save and offer a rebuild;
 * it never rebuilds. Main thread only.
 * The `handle...` methods take the `choice` of a clicked slot and the menu's `block` (null if opened by command).
 */
class EditManager(
    private val plugin: MetroPlugin,
) {
    /** Players in editor mode. */
    private val editing = HashSet<UUID>()

    /** Selected (station id, platform id) per player. */
    private val active = HashMap<UUID, Pair<String, String>>()

    /** Selected line id per player. */
    private val activeLines = HashMap<UUID, String>()

    fun isEditing(player: Player): Boolean = player.uniqueId in editing

    fun isCrowbar(player: Player): Boolean {
        val item = player.inventory.itemInMainHand
        if (item.type == Material.AIR) return false
        return item.type == plugin.metroConfig.crowbarMaterial
    }

    fun toggle(player: Player): Boolean {
        val on = if (editing.remove(player.uniqueId)) false else editing.add(player.uniqueId)
        player.sendMessage(Text.of(plugin.metroConfig.message(if (on) "editor.enabled" else "editor.disabled")))
        return on
    }

    fun forget(player: Player) {
        editing.remove(player.uniqueId)
        active.remove(player.uniqueId)
        activeLines.remove(player.uniqueId)
    }

    private fun activeLine(player: Player): Line? = activeLines[player.uniqueId]?.let { plugin.dataStore.lines[it] }

    private fun activeStation(player: Player): Station? {
        val id = active[player.uniqueId]?.first
        return id?.let { plugin.dataStore.stations[it] }
    }

    private fun activePlatform(
        player: Player,
        station: Station,
    ): Platform? {
        val id = active[player.uniqueId]?.second
        return (id?.let { station.platform(it) }) ?: station.platforms.firstOrNull()
    }

    private fun setActiveStation(
        player: Player,
        stationId: String,
        platformId: String,
    ) {
        active[player.uniqueId] = stationId to platformId
    }

    /** Makes [station] and its platform [platformId] the player's selection, along with the station's line. */
    fun select(
        player: Player,
        station: Station,
        platformId: String,
    ) {
        plugin.dataStore.lineIdOf(station.id)?.let { setActiveLine(player, it) }
        setActiveStation(player, station.id, platformId)
    }

    private fun setActiveLine(
        player: Player,
        lineId: String,
    ) {
        activeLines[player.uniqueId] = lineId
    }

    /** Opens the menu for the player's selection: lines, stations, platforms or anchor actions, whichever is next. */
    fun open(
        player: Player,
        block: Location,
    ) {
        val line = activeLine(player)
        val station = activeStation(player)
        when {
            line == null -> EditMenu.openLines(plugin, player, block)
            station == null || plugin.dataStore.lineIdOf(station.id) != line.id ->
                EditMenu.openStations(plugin, player, block, line)
            else -> {
                val platform = activePlatform(player, station)
                if (platform == null) {
                    EditMenu.openPlatforms(plugin, player, block, station)
                } else {
                    EditMenu.openActions(plugin, player, block, station, platform.id)
                }
            }
        }
    }

    fun handleLinePick(
        player: Player,
        choice: String,
        block: Location?,
    ) {
        if (choice == "__create__") {
            player.closeInventory()
            prompt(player, "editor.command-create-line", "/metro line create ")
            return
        }
        val line = plugin.dataStore.lines[choice] ?: return
        setActiveLine(player, choice)
        active.remove(player.uniqueId)
        val text = plugin.metroConfig.message("editor.active-line", "display" to line.display, "id" to line.id)
        player.sendMessage(Text.of(text))
        if (block != null) EditMenu.openStations(plugin, player, block, line)
    }

    fun handleLineSettings(
        player: Player,
        choice: String,
        block: Location?,
    ) {
        val line = activeLine(player) ?: return
        when (choice) {
            "line-name" -> {
                player.closeInventory()
                prompt(player, "editor.command-line-name", "/metro line name ${line.id} ")
            }
            "line-icon" -> {
                player.closeInventory()
                prompt(player, "editor.command-line-icon", "/metro line icon ${line.id} ")
            }
            "line-speed" -> {
                player.closeInventory()
                prompt(player, "editor.command-line-speed", "/metro line speed ${line.id} ")
            }
            "line-delete" -> if (block != null) EditMenu.openLineDeleteConfirm(plugin, player, block, line)
            "__back__" -> if (block != null) EditMenu.openStations(plugin, player, block, line)
        }
    }

    fun handleStationPick(
        player: Player,
        choice: String,
        block: Location?,
    ) {
        val line = activeLine(player) ?: return
        when (choice) {
            "__lines__" -> if (block != null) EditMenu.openLines(plugin, player, block)
            "__settings__" -> if (block != null) EditMenu.openLineSettings(plugin, player, block, line)
            "__create__" -> {
                player.closeInventory()
                prompt(player, "editor.command-create-station", "/metro station create ${line.id} ")
            }
            else -> {
                val station = plugin.dataStore.stations[choice] ?: return
                val first = station.platforms.firstOrNull()
                setActiveStation(player, station.id, first?.id.orEmpty())
                if (block != null) {
                    EditMenu.openPlatforms(plugin, player, block, station)
                } else {
                    player.sendMessage(Text.of(plugin.metroConfig.message("editor.active-station", "id" to choice)))
                }
            }
        }
    }

    fun handleAction(
        player: Player,
        action: String,
        block: Location,
    ) {
        val station = activeStation(player) ?: return
        val platform = activePlatform(player, station)

        // Go through AnchorEditor so the menu and the command snap the block the same way. Otherwise the same click
        // could set a different anchor from the menu than from the command.
        when (action) {
            "platforms" -> EditMenu.openPlatforms(plugin, player, block, station)
            "platform-settings" -> platform?.let { EditMenu.openPlatformSettings(plugin, player, block, station, it) }
            "delete-platform" ->
                platform?.let { EditMenu.openDeletePlatformConfirm(plugin, player, block, station, it.id) }
            "lines" -> EditMenu.openLines(plugin, player, block)
            "stations" -> activeLine(player)?.let { EditMenu.openStations(plugin, player, block, it) }
            in Platform.ROLES ->
                platform?.let { plugin.anchorEditor.setAnchor(player, station.id, it.id, action, block) }
            "heading" -> platform?.let { promptHeading(player, station.id, it.id) }
        }
    }

    fun handleStationSettings(
        player: Player,
        choice: String,
        block: Location?,
    ) {
        val station = activeStation(player) ?: return
        when (choice) {
            "name" -> {
                player.closeInventory()
                prompt(player, "editor.command-station-name", "/metro station name ${station.id} ")
            }
            "icon" -> {
                player.closeInventory()
                prompt(player, "editor.command-station-icon", "/metro station icon ${station.id} ")
            }
            "delete" -> if (block != null) EditMenu.openDeleteConfirm(plugin, player, block, station)
            "terminal" -> {
                setStationTerminal(player, station.id, null)
                if (block != null) EditMenu.openStationSettings(plugin, player, block, station)
            }
            "__back__" -> if (block != null) EditMenu.openPlatforms(plugin, player, block, station)
        }
    }

    fun handlePlatformSettings(
        player: Player,
        choice: String,
        block: Location?,
    ) {
        val station = activeStation(player) ?: return
        val platform = activePlatform(player, station) ?: return
        when (choice) {
            "platform-name" -> {
                player.closeInventory()
                prompt(player, "editor.command-platform-name", "/metro platform name ${station.id} ${platform.id} ")
            }
            "platform-icon" -> {
                player.closeInventory()
                prompt(player, "editor.command-platform-icon", "/metro platform icon ${station.id} ${platform.id} ")
            }
            "platform-heading" -> {
                setPlatformHeading(player, station.id, platform.id, player.facing.name)
                if (block != null) EditMenu.openPlatformSettings(plugin, player, block, station, platform)
            }
            "__back__" -> if (block != null) EditMenu.openActions(plugin, player, block, station, platform.id)
        }
    }

    fun handlePlatformPick(
        player: Player,
        choice: String,
        block: Location?,
    ) {
        val station = activeStation(player) ?: return
        when (choice) {
            "__add__" -> {
                val added = station.addPlatform()
                setActiveStation(player, station.id, added.id)
                if (save(player)) {
                    val ref = Nodes.stop(station.id, added.id)
                    val text = plugin.metroConfig.message("editor.platform-created", "platform" to ref)
                    player.sendMessage(Text.of(text))
                    if (block != null) EditMenu.openActions(plugin, player, block, station, added.id)
                }
            }
            "__settings__" -> if (block != null) EditMenu.openStationSettings(plugin, player, block, station)
            "__stations__" ->
                activeLine(player)?.let { if (block != null) EditMenu.openStations(plugin, player, block, it) }
            else -> {
                if (station.platform(choice) == null) return
                setActiveStation(player, station.id, choice)
                if (block != null) EditMenu.openActions(plugin, player, block, station, choice)
            }
        }
    }

    fun confirmDelete(
        player: Player,
        stationId: String?,
    ) {
        val id = stationId ?: return
        plugin.dataStore.removeStation(id)
        active.entries.removeIf { it.value.first == id }
        if (save(player)) {
            player.sendMessage(Text.of(plugin.metroConfig.message("editor.station-deleted", "id" to id)))
            promptRebuild(player)
        }
    }

    fun confirmDeleteLine(
        player: Player,
        lineId: String?,
    ) {
        val id = lineId ?: return
        val removed = plugin.dataStore.deleteLine(id)
        activeLines.entries.removeIf { it.value == id }
        active.entries.removeIf { plugin.dataStore.stations[it.value.first] == null }
        if (save(player)) {
            val text = plugin.metroConfig.message("editor.line-deleted", "id" to id, "count" to removed.toString())
            player.sendMessage(Text.of(text))
            promptRebuild(player)
        }
    }

    fun confirmDeletePlatform(
        player: Player,
        stationId: String?,
        platformId: String?,
    ) {
        val station = stationId?.let { plugin.dataStore.stations[it] }
        val platform = platformId?.let { station?.platform(it) }
        if (stationId == null || platformId == null || platform == null) return
        plugin.dataStore.removePlatform(stationId, platformId)
        active.entries.removeIf { it.value.first == stationId && it.value.second == platformId }
        if (save(player)) {
            player.sendMessage(
                Text.of(
                    plugin.metroConfig.message(
                        "editor.platform-removed",
                        "platform" to Nodes.stop(stationId, platformId),
                    ),
                ),
            )
            promptRebuild(player)
        }
    }

    fun createLine(
        player: Player,
        rawId: String,
    ) {
        val id = Ids.validated(rawId)
        if (id == null) {
            player.sendMessage(Text.of(plugin.metroConfig.message("editor.invalid-id", "id" to rawId)))
            return
        }
        if (plugin.dataStore.lines.containsKey(id)) {
            player.sendMessage(Text.of(plugin.metroConfig.message("editor.line-exists", "id" to id)))
            return
        }
        plugin.dataStore.createLine(id, id, Material.COMPASS.name)
        setActiveLine(player, id)
        if (save(player)) player.sendMessage(Text.of(plugin.metroConfig.message("editor.line-created", "id" to id)))
    }

    fun setLineName(
        player: Player,
        lineId: String,
        name: String,
    ) {
        val line = plugin.dataStore.lines[lineId]
        if (line == null) {
            player.sendMessage(Text.of(plugin.metroConfig.message("editor.line-not-found")))
            return
        }
        if (name.isBlank()) {
            player.sendMessage(Text.of(plugin.metroConfig.message("editor.empty-name")))
            return
        }
        line.display = name.trim()
        if (save(player)) {
            player.sendMessage(Text.of(plugin.metroConfig.message("editor.line-name-set", "name" to line.display)))
        }
    }

    fun setLineIcon(
        player: Player,
        lineId: String,
        rawMaterial: String,
    ) {
        val line = plugin.dataStore.lines[lineId]
        if (line == null) {
            player.sendMessage(Text.of(plugin.metroConfig.message("editor.line-not-found")))
            return
        }
        val material = parseMaterial(rawMaterial)
        if (material == null) {
            player.sendMessage(Text.of(plugin.metroConfig.message("editor.unknown-item")))
            return
        }
        line.icon = material.name
        if (save(player)) {
            player.sendMessage(Text.of(plugin.metroConfig.message("editor.line-icon-set", "icon" to material.name)))
        }
    }

    fun createStation(
        player: Player,
        lineId: String,
        rawId: String,
    ) {
        val id = Ids.validated(rawId)
        when {
            plugin.dataStore.lines[lineId] == null ->
                player.sendMessage(Text.of(plugin.metroConfig.message("editor.line-not-found")))
            id == null ->
                player.sendMessage(Text.of(plugin.metroConfig.message("editor.invalid-id", "id" to rawId)))
            else -> createOrActivateStation(player, lineId, id)
        }
    }

    private fun createOrActivateStation(
        player: Player,
        lineId: String,
        id: String,
    ) {
        val store = plugin.dataStore
        val existing = store.stations[id]
        val owner = store.lineIdOf(id)
        when {
            existing != null && owner != lineId ->
                player.sendMessage(
                    Text.of(plugin.metroConfig.message("editor.station-exists", "id" to id, "line" to owner.orEmpty())),
                )
            existing != null -> {
                val first = existing.platforms.firstOrNull()
                select(player, existing, first?.id.orEmpty())
                player.sendMessage(Text.of(plugin.metroConfig.message("editor.station-active", "id" to id)))
            }
            else -> {
                val station = Station(id = id, display = id, world = player.world.name)
                val platform = station.addPlatform()
                store.addStation(lineId, station)
                select(player, station, platform.id)
                if (save(player)) {
                    player.sendMessage(
                        Text.of(plugin.metroConfig.message("editor.station-created", "id" to id, "line" to lineId)),
                    )
                }
            }
        }
    }

    fun setStationName(
        player: Player,
        stationId: String,
        name: String,
    ) {
        val station = plugin.dataStore.stations[stationId]
        if (station == null) {
            player.sendMessage(Text.of(plugin.metroConfig.message("editor.station-not-found")))
            return
        }
        if (name.isBlank()) {
            player.sendMessage(Text.of(plugin.metroConfig.message("editor.empty-name")))
            return
        }
        station.display = name.trim()
        if (save(player)) {
            player.sendMessage(Text.of(plugin.metroConfig.message("editor.name-set", "name" to station.display)))
        }
    }

    fun setStationIcon(
        player: Player,
        stationId: String,
        rawMaterial: String,
    ) {
        val station = plugin.dataStore.stations[stationId]
        if (station == null) {
            player.sendMessage(Text.of(plugin.metroConfig.message("editor.station-not-found")))
            return
        }
        val material = parseMaterial(rawMaterial)
        if (material == null) {
            player.sendMessage(Text.of(plugin.metroConfig.message("editor.unknown-item")))
            return
        }
        station.icon = material.name
        if (save(player)) {
            player.sendMessage(Text.of(plugin.metroConfig.message("editor.icon-set", "icon" to material.name)))
        }
    }

    /** A line's speed is baked only into edges with both ends on that line, so the move needs a rebuild. */
    fun setStationLine(
        player: Player,
        stationId: String,
        lineId: String,
    ) {
        val result = plugin.dataStore.moveStation(stationId, lineId)
        val key =
            when (result) {
                Network.MoveResult.NO_STATION -> "editor.station-not-found"
                Network.MoveResult.NO_LINE -> "editor.line-not-found"
                Network.MoveResult.ALREADY_ON_LINE -> "editor.station-already-on-line"
                Network.MoveResult.MOVED -> "editor.station-line-set"
            }
        if (result == Network.MoveResult.MOVED && !save(player)) return
        player.sendMessage(Text.of(plugin.metroConfig.message(key, "station" to stationId, "line" to lineId)))
        if (result == Network.MoveResult.MOVED) promptRebuild(player)
    }

    /** A null [value] toggles the flag. */
    fun setStationTerminal(
        player: Player,
        stationId: String,
        value: Boolean?,
    ) {
        val station = plugin.dataStore.stations[stationId]
        if (station == null) {
            player.sendMessage(Text.of(plugin.metroConfig.message("editor.station-not-found")))
            return
        }
        station.terminal = value ?: !station.terminal
        if (save(player)) {
            val report = EdgeBuilder.lastReport
            val state = if (station.terminal) "on" else "off"
            val message =
                plugin.metroConfig.message("editor.station-terminal-set", "station" to station.id, "state" to state)
            player.sendMessage(Text.of(message))
            if (station.terminal && report != null && station.id !in report.turnarounds) {
                val warning = plugin.metroConfig.message("editor.terminal-no-turnaround", "station" to station.id)
                player.sendMessage(RebuildWarnings(plugin).link(warning, "terminal-no-turnaround"))
            }
        }
    }

    fun addPlatform(
        player: Player,
        stationId: String,
    ) {
        val station = plugin.dataStore.stations[stationId]
        if (station == null) {
            player.sendMessage(Text.of(plugin.metroConfig.message("editor.station-not-found")))
            return
        }
        val platform = station.addPlatform()
        select(player, station, platform.id)
        if (save(player)) {
            val ref = Nodes.stop(station.id, platform.id)
            player.sendMessage(Text.of(plugin.metroConfig.message("editor.platform-created", "platform" to ref)))
        }
    }

    fun setPlatformName(
        player: Player,
        stationId: String,
        platformId: String,
        name: String,
    ) {
        val platform = plugin.dataStore.stations[stationId]?.platform(platformId)
        if (platform == null) {
            player.sendMessage(Text.of(plugin.metroConfig.message("editor.platform-not-found")))
            return
        }
        if (name.isBlank()) {
            player.sendMessage(Text.of(plugin.metroConfig.message("editor.empty-name")))
            return
        }
        platform.display = name.trim()
        if (save(player)) {
            val text = plugin.metroConfig.message("editor.platform-name-set", "name" to platform.display.orEmpty())
            player.sendMessage(Text.of(text))
        }
    }

    /**
     * Sets the platform heading. [raw] is `north`, `east`, `south`, `west`, or `auto` to unset it. Not case sensitive.
     */
    fun setPlatformHeading(
        player: Player,
        stationId: String,
        platformId: String,
        raw: String,
    ) {
        val platform = plugin.dataStore.stations[stationId]?.platform(platformId)
        if (platform == null) {
            player.sendMessage(Text.of(plugin.metroConfig.message("editor.platform-not-found")))
            return
        }
        val normalized = raw.lowercase()
        val heading = Cardinal.of(normalized)
        if (normalized != "auto" && heading == null) {
            player.sendMessage(Text.of(plugin.metroConfig.message("editor.unknown-heading", "heading" to raw)))
            return
        }
        platform.heading = heading
        if (save(player)) {
            val text = plugin.metroConfig.message("editor.platform-heading-set", "heading" to normalized)
            player.sendMessage(Text.of(text))
            promptRebuild(player)
        }
    }

    private fun promptHeading(
        player: Player,
        stationId: String,
        platformId: String,
    ) {
        prompt(player, "editor.command-platform-heading", "/metro platform heading $stationId $platformId ")
    }

    fun setPlatformIcon(
        player: Player,
        stationId: String,
        platformId: String,
        rawMaterial: String,
    ) {
        val platform = plugin.dataStore.stations[stationId]?.platform(platformId)
        if (platform == null) {
            player.sendMessage(Text.of(plugin.metroConfig.message("editor.platform-not-found")))
            return
        }
        val material = parseMaterial(rawMaterial)
        if (material == null) {
            player.sendMessage(Text.of(plugin.metroConfig.message("editor.unknown-item")))
            return
        }
        platform.icon = material.name
        if (save(player)) {
            player.sendMessage(Text.of(plugin.metroConfig.message("editor.platform-icon-set", "icon" to material.name)))
        }
    }

    /** Refuses unknown names, air and block-only materials (no item to show). */
    private fun parseMaterial(raw: String): Material? = Material.matchMaterial(raw.trim())?.takeIf(Icons::usable)

    /** Sends the lang text [key] as a chat line; a click puts [command] into the player's chat box. */
    internal fun prompt(
        player: Player,
        key: String,
        command: String,
        vararg placeholders: Pair<String, String>,
    ) {
        player.sendMessage(
            Text.command(
                plugin.metroConfig.message(key, "command" to command.trim(), *placeholders),
                command,
            ),
        )
    }

    /** When saving is refused, tells [player] and returns false, so the caller skips its confirmation. */
    internal fun save(player: Player): Boolean {
        if (plugin.dataStore.save()) return true
        player.sendMessage(Text.of(plugin.metroConfig.message("editor.not-saved")))
        return false
    }

    /** Tells the player that the graph needs `/metro rebuild`, with a click to start typing it. */
    internal fun promptRebuild(player: Player) {
        prompt(player, "editor.rebuild-needed", REBUILD_COMMAND)
    }

    private companion object {
        const val REBUILD_COMMAND = "/metro rebuild"
    }
}
