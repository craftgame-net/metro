package net.craftgame.metro.edit

import net.craftgame.metro.MetroPlugin
import net.craftgame.metro.config.Speeds
import net.craftgame.metro.util.Text
import org.bukkit.entity.Player

/** Sets the speed of a line from text typed in a command. */
object LineSpeedEditor {
    /**
     * Sets the speed of line [lineId] from [raw]: `auto` (unset) or blocks per tick, clamped to `speed.max-cruise`.
     * Edge speeds are baked at rebuild, so the new speed has no effect until the player rebuilds.
     */
    fun set(
        plugin: MetroPlugin,
        player: Player,
        lineId: String,
        raw: String,
    ) {
        val config = plugin.metroConfig
        val line = plugin.dataStore.lines[lineId]
        val input = parseSpeed(raw)
        val speed = input?.override
        val cap = config.speed.maxCruise
        when {
            line == null -> player.sendMessage(Text.of(config.message("editor.line-not-found")))
            input == null -> player.sendMessage(Text.of(invalidSpeed(plugin, raw)))
            else -> {
                if (speed != null && speed > cap) {
                    val clamped = config.message("editor.line-speed-clamped", "speed" to raw, "max" to cap.toString())
                    player.sendMessage(Text.of(clamped))
                }
                line.speed = speed?.coerceAtMost(cap)
                if (plugin.editManager.save(player)) {
                    val shown = line.speed?.toString() ?: config.message("item.auto")
                    val text = config.message("editor.line-speed-set", "id" to line.id, "speed" to shown)
                    player.sendMessage(Text.of(text))
                    plugin.editManager.promptRebuild(player)
                }
            }
        }
    }
}

/** Returns the message that says [raw] is not a valid speed and what the minimum is. */
fun invalidSpeed(
    plugin: MetroPlugin,
    raw: String,
): String = plugin.metroConfig.message("editor.invalid-speed", "speed" to raw, "min" to Speeds.MIN.toString())
