package net.craftgame.metro.preview

import net.craftgame.metro.MetroPlugin
import net.craftgame.metro.data.Junction
import net.craftgame.metro.edit.EditPoint
import net.craftgame.metro.edit.PointLookup
import net.craftgame.metro.util.Palette
import net.craftgame.metro.util.Text
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.block.Block
import org.bukkit.entity.Display
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import java.util.UUID

/** Shows a floating text above the anchor or junction that an editor looks at, one label per editor. */
class HoverLabel(
    private val plugin: MetroPlugin,
) {
    /** The label entity of each editor. */
    private val labels = HashMap<UUID, TextDisplay>()

    /** For each editor: the block, data version and config generation the label text was written from. */
    private val shown = HashMap<UUID, Any>()

    /** Cached [PointLookup.markers], built for the data version in [markersVersion]. */
    private var markers: Map<String, EditPoint> = emptyMap()
    private var markersVersion: Long? = null

    /** Starts the repeating task. The scheduler stops it when the plugin is disabled. */
    fun start() {
        plugin.scheduler.repeat(INITIAL_DELAY_TICKS, INTERVAL_TICKS) { tick() }
    }

    /** Removes every label. */
    fun stop() {
        labels.values.forEach { it.remove() }
        labels.clear()
        shown.clear()
    }

    /** Removes the label of [player], for example when they leave. */
    fun forget(player: Player) {
        labels.remove(player.uniqueId)?.remove()
        shown.remove(player.uniqueId)
    }

    private fun tick() {
        val editing = Bukkit.getOnlinePlayers().filter { plugin.editManager.isEditing(it) }
        val keep = editing.map { it.uniqueId }.toSet()
        val stale = labels.entries.iterator()
        while (stale.hasNext()) {
            val entry = stale.next()
            if (entry.key !in keep) {
                entry.value.remove()
                stale.remove()
                shown.remove(entry.key)
            }
        }
        if (editing.isEmpty()) return
        val markers = markers()
        for (player in editing) {
            val found = PointLookup.raycast(player, markers)
            if (found == null) hide(player) else show(player, found.first, found.second)
        }
    }

    private fun markers(): Map<String, EditPoint> {
        val version = plugin.dataStore.version
        if (markersVersion != version) {
            markers = PointLookup.markers(plugin.dataStore)
            markersVersion = version
        }
        return markers
    }

    private fun hide(player: Player) {
        shown.remove(player.uniqueId)
        labels[player.uniqueId]?.let { player.hideEntity(plugin, it) }
    }

    private fun show(
        player: Player,
        block: Block,
        point: EditPoint,
    ) {
        val existing = labels[player.uniqueId]
        // The same block under the same data and config: the label already shows this.
        val what = listOf(block.location, plugin.dataStore.version, plugin.metroConfig.generation)
        val current = existing != null && existing.isValid && existing.world == block.world
        if (current && shown[player.uniqueId] == what) return
        val location = block.location.clone().add(BLOCK_CENTER, LABEL_HEIGHT, BLOCK_CENTER)
        val display =
            // A label from another world is replaced, not teleported across worlds.
            if (existing != null && existing.isValid && existing.world == block.world) {
                existing
            } else {
                existing?.remove()
                spawn(block, location).also { labels[player.uniqueId] = it }
            }
        display.teleport(location)
        display.text(text(point))
        player.showEntity(plugin, display)
        shown[player.uniqueId] = what
    }

    private fun spawn(
        block: Block,
        location: Location,
    ): TextDisplay =
        block.world.spawn(location, TextDisplay::class.java) { display ->
            display.isPersistent = false
            display.isVisibleByDefault = false
            display.billboard = Display.Billboard.CENTER
            display.isSeeThrough = true
            display.isShadowed = false
            display.isDefaultBackground = false
            display.backgroundColor = Color.fromARGB(BACKGROUND_ALPHA, 0, 0, 0)
            display.isInvulnerable = true
            display.setGravity(false)
            display.viewRange = VIEW_RANGE
            val transformation = display.transformation
            transformation.scale.set(LABEL_SCALE, LABEL_SCALE, LABEL_SCALE)
            display.transformation = transformation
        }

    private fun text(point: EditPoint): Component =
        when (point) {
            is EditPoint.Anchor -> anchorText(point.ref, point.role)
            is EditPoint.JunctionPoint -> junctionText(point.junction)
        }

    private fun anchorText(
        platform: String,
        role: String,
    ): Component {
        val color = Palette.anchorCode(role)
        return Text.of(
            plugin.metroConfig.message(
                "hover.anchor-label",
                "color" to color,
                "role" to role,
                "platform" to platform,
            ),
        )
    }

    private fun junctionText(junction: Junction): Component {
        val turnsOff = junction.kind == Junction.Kind.CROSS && !junction.turns
        val turns = if (turnsOff) plugin.metroConfig.message("hover.turns-off") else ""
        val speed =
            junction.speed
                ?.let { plugin.metroConfig.message("hover.speed", "speed" to it.toString()) }
                .orEmpty()
        return Text.of(
            plugin.metroConfig.message(
                "hover.junction-label",
                "color" to Palette.code(Palette.junction(junction.kind, junction.turns)),
                "id" to junction.id,
                "kind" to junction.kind.name.lowercase(),
                "turns" to turns,
                "speed" to speed,
            ),
        )
    }

    private companion object {
        /** Ticks between two label updates. */
        const val INTERVAL_TICKS = 4L

        /** Ticks to wait before the first update. */
        const val INITIAL_DELAY_TICKS = 10L

        /** Offset to the middle of a block, in blocks. */
        const val BLOCK_CENTER = 0.5

        /** Blocks above the block's lower corner where the label floats. */
        const val LABEL_HEIGHT = 1.15

        /** Opacity of the label background, 0 to 255. */
        const val BACKGROUND_ALPHA = 140

        /** Multiplier of the default entity view distance. */
        const val VIEW_RANGE = 0.5f

        /** Size factor of the text. */
        const val LABEL_SCALE = 0.55f
    }
}
