package net.craftgame.metro.command

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import net.craftgame.metro.MetroPlugin
import net.craftgame.metro.util.Paging
import net.craftgame.metro.util.Text
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/** Sends a chat list a page at a time. The console gets the whole list. */
class ChatPages(
    private val plugin: MetroPlugin,
) {
    /**
     * Sends the [page] (counted from 1, clamped) of [lines]. A player also gets a footer whose links run
     * `command(n)` for page n. The console gets all lines and no footer.
     */
    fun send(
        sender: CommandSender,
        lines: List<Component>,
        page: Int,
        command: (Int) -> String,
    ) {
        if (sender !is Player) {
            lines.forEach(sender::sendMessage)
            return
        }
        val size = Paging.CHAT_LINES
        val pages = Paging.pages(lines.size, size)
        val current = Paging.clamp(page, lines.size, size)
        Paging.slice(lines, current, size).forEach(sender::sendMessage)
        if (pages > 1) sender.sendMessage(footer(current, pages, command))
    }

    private fun footer(
        page: Int,
        pages: Int,
        command: (Int) -> String,
    ): Component {
        val parts = ArrayList<Component>()
        if (page > 1) parts += arrow("command.page-previous", page - 1, command)
        parts += Text.of(message("command.page-status", "page" to "$page", "pages" to "$pages"))
        if (page < pages) parts += arrow("command.page-next", page + 1, command)
        return Component.join(JoinConfiguration.separator(Component.text(GAP)), parts)
    }

    private fun arrow(
        key: String,
        target: Int,
        command: (Int) -> String,
    ): Component = Text.run(message(key), command(target), message("command.page-hover", "page" to "$target"))

    private fun message(
        key: String,
        vararg placeholders: Pair<String, String>,
    ): String = plugin.metroConfig.message(key, *placeholders)

    companion object {
        /** Space between the footer parts. */
        private const val GAP = "   "

        /** Brigadier argument for a page number, at least 1. */
        fun pageArg(): RequiredArgumentBuilder<CommandSourceStack, Int> {
            val type = IntegerArgumentType.integer(1)
            return Commands.argument("page", type)
        }

        /** Reads the page number given by [pageArg]. */
        fun page(context: CommandContext<CommandSourceStack>): Int = IntegerArgumentType.getInteger(context, "page")
    }
}
