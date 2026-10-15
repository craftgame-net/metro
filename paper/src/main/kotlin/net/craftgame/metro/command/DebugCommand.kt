package net.craftgame.metro.command

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import net.craftgame.metro.MetroPlugin
import net.craftgame.metro.config.Permissions
import net.craftgame.metro.debug.ChatDebugSink
import net.craftgame.metro.debug.ConsoleDebugSink
import net.craftgame.metro.debug.DebugHub
import net.craftgame.metro.debug.DebugLevel
import net.craftgame.metro.debug.DebugScope
import net.craftgame.metro.util.Text
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/** `/metro debug [off|1|2|3] [own|all|<player>]`: the sender watches debug lines of the given level and scope. */
internal class DebugCommand(
    private val plugin: MetroPlugin,
) {
    private val suggestions = Suggestions(plugin)

    fun build(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands
            .literal("debug")
            .requires(permission(Permissions.DEBUG))
            .executes { context -> toggle(context.source.sender) }
            .then(Commands.literal("off").executes { context -> stop(context.source.sender) })
            .then(levelNode("on", DebugLevel.EVENT))
            .then(levelNode("1", DebugLevel.EVENT))
            .then(levelNode("2", DebugLevel.DECISION))
            .then(levelNode("3", DebugLevel.DETAIL))

    private fun levelNode(
        name: String,
        level: Int,
    ): ArgumentBuilder<CommandSourceStack, *> =
        Commands
            .literal(name)
            .executes { context -> start(context.source.sender, level, null) }
            .then(Commands.literal("own").executes { context -> start(context.source.sender, level, OWN) })
            .then(Commands.literal("all").executes { context -> start(context.source.sender, level, ALL) })
            .then(
                Commands
                    .argument("player", StringArgumentType.word())
                    .suggests(suggestions.onlinePlayers)
                    .executes { context ->
                        val target = context.getArgument("player", String::class.java)
                        start(context.source.sender, level, target)
                    },
            )

    private fun toggle(sender: CommandSender): Int =
        if (plugin.debugLog.watchOf(viewerOf(sender)) == null) start(sender, DebugLevel.EVENT, null) else stop(sender)

    private fun stop(sender: CommandSender): Int {
        plugin.debugLog.stop(viewerOf(sender))
        tell(sender, "command.debug-off")
        return 1
    }

    /** [scopeText] is `own`, `all`, a player name, or null for the default of the sender. */
    private fun start(
        sender: CommandSender,
        level: Int,
        scopeText: String?,
    ): Int {
        resolveScope(sender, scopeText)?.let { begin(sender, level, it) }
        return 1
    }

    private fun begin(
        sender: CommandSender,
        level: Int,
        scope: DebugScope,
    ) {
        val player = sender as? Player
        val refusal = if (player == null) null else refusalForPlayer(sender, level, scope)
        if (refusal != null) {
            tell(sender, refusal)
            return
        }
        val sink = if (player == null) ConsoleDebugSink(plugin.logger) else ChatDebugSink(plugin, player.uniqueId)
        plugin.debugLog.watch(player?.uniqueId, DebugHub.Watch(level, scope), sink)
        tell(sender, "command.debug-on", "level" to "$level", "scope" to scopeName(scope))
    }

    /** Returns the lang key that says why a player may not watch this, or null when they may. */
    private fun refusalForPlayer(
        sender: CommandSender,
        level: Int,
        scope: DebugScope,
    ): String? =
        when {
            level > DebugHub.CHAT_MAX_LEVEL -> "command.debug-chat-level"
            scope != DebugScope.Own && !sender.hasPermission(Permissions.ADMIN) -> "command.debug-need-admin"
            else -> null
        }

    /** Returns the scope for [text], or null after telling the sender why there is none. */
    private fun resolveScope(
        sender: CommandSender,
        text: String?,
    ): DebugScope? {
        val own = sender is Player
        return when (text) {
            null -> if (own) DebugScope.Own else DebugScope.All
            ALL -> DebugScope.All
            OWN ->
                if (own) {
                    DebugScope.Own
                } else {
                    tell(sender, "command.debug-console-own")
                    null
                }
            else -> {
                val target = Bukkit.getPlayerExact(text)
                if (target == null) tell(sender, "command.unknown-player", "name" to text)
                target?.let { DebugScope.Player(it.uniqueId) }
            }
        }
    }

    private fun scopeName(scope: DebugScope): String =
        when (scope) {
            DebugScope.All -> ALL
            DebugScope.Own -> OWN
            is DebugScope.Player -> Bukkit.getOfflinePlayer(scope.id).name ?: scope.id.toString()
        }

    private fun viewerOf(sender: CommandSender) = (sender as? Player)?.uniqueId

    private fun tell(
        sender: CommandSender,
        key: String,
        vararg placeholders: Pair<String, String>,
    ) = sender.sendMessage(Text.of(plugin.metroConfig.message(key, *placeholders)))

    private companion object {
        const val OWN = "own"
        const val ALL = "all"
    }
}
