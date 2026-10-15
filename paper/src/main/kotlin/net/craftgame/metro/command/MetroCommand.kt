package net.craftgame.metro.command

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import net.craftgame.metro.MetroPlugin
import net.craftgame.metro.config.Permissions
import net.craftgame.metro.route.EdgeBuilder
import net.craftgame.metro.route.rebuild
import net.craftgame.metro.util.Text
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.logging.Level

/** Registers `/metro` and its service subcommands. */
class MetroCommand(
    private val plugin: MetroPlugin,
) {
    private val edit = EditNodes(plugin)
    private val junctions = EditJunctions(plugin)
    private val anchors = EditAnchors(plugin)
    private val warnings = RebuildWarnings(plugin)

    /** Registers `/metro`. Called from Paper's `COMMANDS` lifecycle event. */
    fun register(registrar: Commands) {
        registrar.register(root(), "Metro management")
    }

    private fun root(): LiteralCommandNode<CommandSourceStack> =
        Commands
            .literal("metro")
            .executes { context -> help(context.source.sender) }
            .then(Commands.literal("help").executes { context -> help(context.source.sender) })
            .then(Commands.literal("version").executes { context -> version(context.source.sender) })
            .then(
                Commands
                    .literal("editor")
                    .requires(permission(Permissions.BUILD))
                    .executes { context -> editor(context.source.sender) },
            ).then(
                Commands
                    .literal("crowbar")
                    .requires(permission(Permissions.BUILD))
                    .executes { context -> crowbar(context.source.sender) },
            ).then(
                Commands
                    .literal("issues")
                    .requires(permission(Permissions.BUILD))
                    .executes { context -> warnings.sendIssues(context.source.sender) }
                    .then(
                        ChatPages.pageArg().executes { context ->
                            warnings.sendIssues(context.source.sender, ChatPages.page(context))
                        },
                    ),
            ).then(teleport())
            .then(
                Commands
                    .literal("reload")
                    .requires(permission(Permissions.RELOAD))
                    .executes { context -> reload(context.source.sender) },
            ).then(
                Commands
                    .literal("rebuild")
                    .requires(permission(Permissions.BUILD))
                    .executes { context -> rebuild(context.source.sender) },
            ).then(DebugCommand(plugin).build())
            .then(edit.line())
            .then(edit.station())
            .then(edit.platform())
            .then(anchors.build())
            .then(junctions.build())
            .build()

    private fun teleport(): LiteralArgumentBuilder<CommandSourceStack> {
        fun coordinate(name: String) = Commands.argument(name, IntegerArgumentType.integer())

        return Commands
            .literal("teleport")
            .requires(permission(Permissions.BUILD))
            .then(
                Commands.argument("world", ArgumentTypes.world()).then(
                    coordinate("x").then(
                        coordinate("y").then(
                            coordinate("z").playerAction(plugin) { player, context ->
                                plugin.networkBrowser.teleportToBlock(
                                    player,
                                    context.getArgument("world", World::class.java),
                                    context.getArgument("x", Int::class.java),
                                    context.getArgument("y", Int::class.java),
                                    context.getArgument("z", Int::class.java),
                                )
                            },
                        ),
                    ),
                ),
            )
    }

    private fun help(sender: CommandSender): Int {
        sender.sendMessage(Text.of(plugin.metroConfig.message("command.help-header")))
        for ((permission, keys) in HELP) {
            if (!sender.hasPermission(permission)) continue
            keys.forEach { sender.sendMessage(Text.of(plugin.metroConfig.message(it))) }
        }
        return 1
    }

    private fun version(sender: CommandSender): Int {
        val version = "version" to plugin.pluginMeta.version
        val text =
            if (sender.hasPermission(Permissions.DEBUG)) {
                plugin.metroConfig.message("command.version-server", version, "server" to Bukkit.getVersion())
            } else {
                plugin.metroConfig.message("command.version", version)
            }
        sender.sendMessage(Text.of(text))
        return 1
    }

    private fun editor(sender: CommandSender): Int {
        val player = sender as? Player
        if (player == null) {
            sender.sendMessage(Text.of(plugin.metroConfig.message("command.players-only")))
        } else {
            plugin.editManager.toggle(player)
        }
        return 1
    }

    private fun crowbar(sender: CommandSender): Int {
        val player = sender as? Player
        if (player == null) {
            sender.sendMessage(Text.of(plugin.metroConfig.message("command.players-only")))
            return 1
        }
        val item = ItemStack(plugin.metroConfig.crowbarMaterial)
        item.itemMeta?.let { meta ->
            meta.displayName(Text.of(plugin.metroConfig.message("item.crowbar")))
            item.itemMeta = meta
        }
        player.inventory.addItem(item)
        player.sendMessage(Text.of(plugin.metroConfig.message("command.crowbar-given")))
        return 1
    }

    private fun reload(sender: CommandSender): Int {
        // Refused while a rebuild is loading chunks: the rebuild would then save a graph built from the data being
        // replaced.
        if (plugin.chunkPreloader.running) {
            sender.sendMessage(Text.of(plugin.metroConfig.message("command.reload-busy")))
            return 1
        }
        plugin.metroConfig.reload()
        plugin.dataStore.load()
        EdgeBuilder.clearReport()
        plugin.networkPreview.restart()
        sender.sendMessage(
            Text.of(
                plugin.metroConfig.message(
                    "command.reloaded",
                    "lines" to
                        plugin.dataStore.lines.size
                            .toString(),
                    "stations" to
                        plugin.dataStore.stations.size
                            .toString(),
                    "segments" to
                        plugin.dataStore.edges.size
                            .toString(),
                ),
            ),
        )
        return 1
    }

    private fun rebuild(sender: CommandSender): Int {
        val playerId = (sender as? Player)?.uniqueId

        // The player may go offline while chunks load; later messages then go to the console.
        fun target(): CommandSender = playerId?.let { Bukkit.getPlayer(it) ?: Bukkit.getConsoleSender() } ?: sender

        fun tell(
            key: String,
            vararg placeholders: Pair<String, String>,
        ) = target().sendMessage(Text.of(plugin.metroConfig.message(key, *placeholders)))
        // The preload callbacks run on the main thread.
        val started =
            plugin.chunkPreloader.preload(
                onStart = { total -> tell("command.rebuild-loading", "total" to "$total") },
                onProgress = { done, total ->
                    tell("command.rebuild-progress", "done" to "$done", "total" to "$total")
                },
            ) {
                val result = runCatching { EdgeBuilder.rebuild(plugin, playerId) }
                val error = result.exceptionOrNull()
                // Any bug in the rebuild may throw, so no narrower type fits. An Error goes on up to the scheduler.
                if (error != null && error !is RuntimeException) throw error
                if (error != null) {
                    // Nothing is saved, so data.yml keeps the last good graph.
                    plugin.logger.log(Level.SEVERE, "Metro: rebuild failed", error)
                    tell("command.rebuild-failed")
                }
                val report = result.getOrNull()
                if (report != null) {
                    if (!plugin.dataStore.save()) tell("editor.not-saved")
                    warnings.sendSummary(target(), report)
                }
            }
        if (!started) sender.sendMessage(Text.of(plugin.metroConfig.message("command.rebuild-in-progress")))
        return 1
    }

    private companion object {
        val HELP =
            listOf(
                Permissions.RIDE to listOf("command.help-version"),
                Permissions.BUILD to
                    listOf(
                        "command.help-editor",
                        "command.help-crowbar",
                        "command.help-edit",
                        "editor.usage-line",
                        "editor.usage-station",
                        "editor.usage-platform",
                        "editor.usage-anchor",
                        "editor.usage-junction",
                        "command.help-rebuild",
                        "command.help-issues",
                    ),
                Permissions.RELOAD to listOf("command.help-reload"),
                Permissions.DEBUG to listOf("command.help-debug"),
            )
    }
}
