package net.craftgame.metro.debug

import net.craftgame.metro.MetroPlugin
import net.craftgame.metro.util.Text
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import java.util.UUID
import java.util.logging.Logger

/** Writes debug lines to the server log at INFO, because Paper hides the finer levels. */
class ConsoleDebugSink(
    private val logger: Logger,
) : DebugSink {
    override fun accept(
        level: Int,
        subject: UUID?,
        text: String,
    ) {
        val who = subjectName(subject)?.let { " [$it]" }.orEmpty()
        logger.info("[DL$level]$who $text")
    }

    override fun suppressed(count: Int) = Unit
}

/** Sends debug lines to one player's chat. The player may be offline; then the lines are dropped. */
class ChatDebugSink(
    private val plugin: MetroPlugin,
    private val viewer: UUID,
) : DebugSink {
    override fun accept(
        level: Int,
        subject: UUID?,
        text: String,
    ) {
        val config = plugin.metroConfig
        val who = subjectName(subject)?.let { " " + config.message("debug.who", "name" to it) }.orEmpty()
        val head = config.message("debug.prefix-$level") + who + "&f "
        Bukkit.getPlayer(viewer)?.sendMessage(Text.of(head).append(Component.text(text)))
    }

    override fun suppressed(count: Int) {
        val text = plugin.metroConfig.message("debug.chat-suppressed", "count" to "$count")
        Bukkit.getPlayer(viewer)?.sendMessage(Text.of(text))
    }
}

/** Returns the player's name for a player subject, or null for a global line. */
private fun subjectName(subject: UUID?): String? {
    if (subject == null) return null
    return Bukkit.getOfflinePlayer(subject).name ?: subject.toString()
}
