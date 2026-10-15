package net.craftgame.metro.edit

import net.craftgame.metro.MetroPlugin
import net.craftgame.metro.data.Ids
import net.craftgame.metro.data.Junction
import net.craftgame.metro.geo.Point
import net.craftgame.metro.route.EdgeBuilder
import net.craftgame.metro.util.Text
import net.craftgame.metro.util.toPoint
import org.bukkit.Location
import org.bukkit.entity.Player

/** Creates and changes junctions. */
class JunctionEditor(
    private val plugin: MetroPlugin,
) {
    /**
     * Creates a junction at [location]. A null [rawId] picks the next free `j<n>`; a null [location] means no target.
     */
    fun createJunction(
        player: Player,
        rawKind: String,
        rawId: String?,
        location: Location?,
    ) {
        val id = if (rawId == null) nextId() else Ids.validated(rawId)
        val kind = Junction.kindOf(rawKind)
        when {
            id == null ->
                player.sendMessage(Text.of(plugin.metroConfig.message("editor.invalid-id", "id" to rawId.orEmpty())))
            kind == null ->
                player.sendMessage(Text.of(plugin.metroConfig.message("editor.unknown-kind", "kind" to rawKind)))
            plugin.dataStore.junctions.containsKey(id) ->
                player.sendMessage(Text.of(plugin.metroConfig.message("editor.junction-exists", "id" to id)))
            location == null -> player.sendMessage(Text.of(plugin.metroConfig.message("editor.no-target")))
            else -> place(player, id, kind, location)
        }
    }

    private fun place(
        player: Player,
        id: String,
        kind: Junction.Kind,
        location: Location,
    ) {
        val world = location.world
        if (world == null) {
            player.sendMessage(Text.of(plugin.metroConfig.message("editor.no-target")))
            return
        }
        val snapped = EdgeBuilder.snapToRail(plugin.railWorlds, location.toPoint())
        if (occupied(player, snapped) || anchored(player, snapped)) return
        plugin.dataStore.putJunction(Junction(id, world.name, snapped, kind))
        val shown = kind.name.lowercase()
        saved(player, plugin.metroConfig.message("editor.junction-created", "id" to id, "kind" to shown))
    }

    /** True, with a message to [player], when a junction already sits on the rail at [snapped]. */
    fun occupied(
        player: Player,
        snapped: Point,
    ): Boolean {
        val occupant = PointLookup.junctionAt(plugin.dataStore, snapped) ?: return false
        player.sendMessage(Text.of(plugin.metroConfig.message("editor.junction-here", "id" to occupant.id)))
        return true
    }

    /** True, with a message to [player], when a rail anchor of a platform already sits on the rail at [snapped]. */
    fun anchored(
        player: Player,
        snapped: Point,
    ): Boolean {
        val anchor = PointLookup.railAnchorAt(plugin.dataStore, snapped) ?: return false
        val name = "${anchor.role} ${anchor.ref}"
        player.sendMessage(Text.of(plugin.metroConfig.message("editor.junction-on-anchor", "anchor" to name)))
        return true
    }

    fun setJunctionKind(
        player: Player,
        id: String,
        rawKind: String,
    ) {
        val junction = junction(player, id) ?: return
        val kind = Junction.kindOf(rawKind)
        if (kind == null) {
            player.sendMessage(Text.of(plugin.metroConfig.message("editor.unknown-kind", "kind" to rawKind)))
            return
        }
        junction.kind = kind
        val shown = kind.name.lowercase()
        saved(player, plugin.metroConfig.message("editor.junction-kind-set", "id" to id, "kind" to shown))
    }

    fun setJunctionTurns(
        player: Player,
        id: String,
        value: Boolean?,
    ) {
        val junction = junction(player, id) ?: return
        junction.turns = value ?: !junction.turns
        val state = if (junction.turns) "on" else "off"
        saved(player, plugin.metroConfig.message("editor.junction-turns-set", "id" to id, "state" to state))
    }

    /** A speed above `speed.max` is clamped to it, since no cart can go faster. */
    fun setJunctionSpeed(
        player: Player,
        id: String,
        raw: String,
    ) {
        val junction = junction(player, id) ?: return
        val input = parseSpeed(raw)
        if (input == null) {
            player.sendMessage(Text.of(invalidSpeed(plugin, raw)))
            return
        }
        val cap = plugin.metroConfig.speed.max
        val speed = input.override
        if (speed != null && speed > cap) {
            val clamped =
                plugin.metroConfig.message("editor.junction-speed-clamped", "speed" to raw, "max" to cap.toString())
            player.sendMessage(Text.of(clamped))
        }
        junction.speed = speed?.coerceAtMost(cap)
        val shown = junction.speed?.toString() ?: plugin.metroConfig.message("item.auto")
        saved(player, plugin.metroConfig.message("editor.junction-speed-set", "id" to id, "speed" to shown))
    }

    fun confirmDeleteJunction(
        player: Player,
        id: String?,
    ) {
        val key = id ?: return
        junction(player, key) ?: return
        plugin.dataStore.removeJunction(key)
        saved(player, plugin.metroConfig.message("editor.junction-deleted", "id" to key))
    }

    private fun nextId(): String {
        var n = 1
        while (plugin.dataStore.junctions.containsKey("$AUTO_PREFIX$n")) n++
        return "$AUTO_PREFIX$n"
    }

    private fun junction(
        player: Player,
        id: String,
    ): Junction? {
        val junction = plugin.dataStore.junctions[id]
        if (junction == null) player.sendMessage(Text.of(plugin.metroConfig.message("editor.junction-not-found")))
        return junction
    }

    private fun saved(
        player: Player,
        text: String,
    ) {
        if (!plugin.editManager.save(player)) return
        player.sendMessage(Text.of(text))
        plugin.editManager.promptRebuild(player)
    }

    private companion object {
        const val AUTO_PREFIX = "j"
    }
}
