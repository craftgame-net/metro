package net.craftgame.metro.command

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import net.craftgame.metro.MetroPlugin
import net.craftgame.metro.config.Permissions
import net.craftgame.metro.edit.EDIT_REACH
import net.craftgame.metro.edit.LineSpeedEditor
import net.craftgame.metro.util.Text
import org.bukkit.Location
import org.bukkit.entity.Player

/** Builds the `line`, `station` and `platform` subcommands. */
class EditNodes(
    private val plugin: MetroPlugin,
) {
    private val suggestions = Suggestions(plugin)

    fun line(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands
            .literal("line")
            .requires(permission(Permissions.BUILD))
            .then(
                Commands.literal("create").then(
                    Commands.argument("id", StringArgumentType.word()).playerAction(plugin) { player, context ->
                        plugin.editManager.createLine(player, context.getArgument("id", String::class.java))
                    },
                ),
            ).then(
                Commands.literal("name").then(
                    lineArg(suggestions).then(
                        nameArg().playerAction(plugin) { player, context ->
                            plugin.editManager.setLineName(
                                player,
                                context.getArgument("line", String::class.java),
                                context.getArgument("name", String::class.java),
                            )
                        },
                    ),
                ),
            ).then(
                Commands.literal("icon").then(
                    lineArg(suggestions).then(
                        itemArg().playerAction(plugin) { player, context ->
                            plugin.editManager.setLineIcon(
                                player,
                                context.getArgument("line", String::class.java),
                                context.getArgument("item", String::class.java),
                            )
                        },
                    ),
                ),
            ).then(
                Commands.literal("speed").then(
                    lineArg(suggestions).then(
                        Commands
                            .argument("speed", StringArgumentType.word())
                            .suggests(suggestions.lineSpeeds)
                            .playerAction(plugin) { player, context ->
                                LineSpeedEditor.set(
                                    plugin,
                                    player,
                                    context.getArgument("line", String::class.java),
                                    context.getArgument("speed", String::class.java),
                                )
                            },
                    ),
                ),
            ).then(
                Commands.literal("delete").then(
                    lineArg(suggestions).playerAction(plugin) { player, context ->
                        plugin.editManager.confirmDeleteLine(player, context.getArgument("line", String::class.java))
                    },
                ),
            )

    fun station(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands
            .literal("station")
            .requires(permission(Permissions.BUILD))
            .then(
                Commands.literal("create").then(
                    lineArg(suggestions).then(
                        Commands.argument("id", StringArgumentType.word()).playerAction(plugin) { player, context ->
                            plugin.editManager.createStation(
                                player,
                                context.getArgument("line", String::class.java),
                                context.getArgument("id", String::class.java),
                            )
                        },
                    ),
                ),
            ).then(
                Commands.literal("name").then(
                    stationArg(suggestions).then(
                        nameArg().playerAction(plugin) { player, context ->
                            plugin.editManager.setStationName(
                                player,
                                context.getArgument("station", String::class.java),
                                context.getArgument("name", String::class.java),
                            )
                        },
                    ),
                ),
            ).then(
                Commands.literal("icon").then(
                    stationArg(suggestions).then(
                        itemArg().playerAction(plugin) { player, context ->
                            plugin.editManager.setStationIcon(
                                player,
                                context.getArgument("station", String::class.java),
                                context.getArgument("item", String::class.java),
                            )
                        },
                    ),
                ),
            ).then(
                Commands.literal("delete").then(
                    stationArg(suggestions).playerAction(plugin) { player, context ->
                        plugin.editManager.confirmDelete(player, context.getArgument("station", String::class.java))
                    },
                ),
            ).then(terminal())
            .then(moveToLine())
            .then(list())
            .then(teleport())

    private fun moveToLine(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("line").then(
            stationArg(suggestions).then(
                lineArg(suggestions).playerAction(plugin) { player, context ->
                    plugin.editManager.setStationLine(
                        player,
                        context.getArgument("station", String::class.java),
                        context.getArgument("line", String::class.java),
                    )
                },
            ),
        )

    /**
     * `list 2` shows page 2, not a line named `2`: the page argument is registered first and Brigadier keeps the
     * first of equally good parses. A line with a number as id needs `list <line> <page>`.
     */
    private fun list(): LiteralArgumentBuilder<CommandSourceStack> {
        fun show(
            context: CommandContext<CommandSourceStack>,
            lineId: String?,
            page: Int,
        ): Int {
            plugin.networkBrowser.listStations(context.source.sender, lineId, page)
            return 1
        }

        fun line(context: CommandContext<CommandSourceStack>): String = context.getArgument("line", String::class.java)

        return Commands
            .literal("list")
            .executes { context -> show(context, null, 1) }
            .then(ChatPages.pageArg().executes { context -> show(context, null, ChatPages.page(context)) })
            .then(
                lineArg(suggestions)
                    .executes { context -> show(context, line(context), 1) }
                    .then(
                        ChatPages.pageArg().executes { context ->
                            show(context, line(context), ChatPages.page(context))
                        },
                    ),
            )
    }

    private fun teleport(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("teleport").then(
            stationArg(suggestions)
                .playerAction(plugin) { player, context ->
                    val station = context.getArgument("station", String::class.java)
                    plugin.networkBrowser.teleportToStation(player, station, null)
                }.then(
                    platformArg(suggestions).playerAction(plugin) { player, context ->
                        plugin.networkBrowser.teleportToStation(
                            player,
                            context.getArgument("station", String::class.java),
                            context.getArgument("platform", String::class.java),
                        )
                    },
                ),
        )

    private fun terminal(): LiteralArgumentBuilder<CommandSourceStack> {
        fun setTerminal(
            player: Player,
            context: CommandContext<CommandSourceStack>,
            value: Boolean?,
        ) {
            plugin.editManager.setStationTerminal(player, context.getArgument("station", String::class.java), value)
        }

        return Commands.literal("terminal").then(
            stationArg(suggestions)
                .playerAction(plugin) { player, context -> setTerminal(player, context, null) }
                .then(
                    Commands.literal("on").playerAction(plugin) { player, context ->
                        setTerminal(player, context, true)
                    },
                ).then(
                    Commands.literal("off").playerAction(plugin) { player, context ->
                        setTerminal(player, context, false)
                    },
                ),
        )
    }

    fun platform(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands
            .literal("platform")
            .requires(permission(Permissions.BUILD))
            .then(
                Commands.literal("add").then(
                    stationArg(suggestions).playerAction(plugin) { player, context ->
                        plugin.editManager.addPlatform(player, context.getArgument("station", String::class.java))
                    },
                ),
            ).then(
                Commands.literal("name").then(
                    stationArg(suggestions).then(
                        platformArg(suggestions).then(
                            nameArg().playerAction(plugin) { player, context ->
                                plugin.editManager.setPlatformName(
                                    player,
                                    context.getArgument("station", String::class.java),
                                    context.getArgument("platform", String::class.java),
                                    context.getArgument("name", String::class.java),
                                )
                            },
                        ),
                    ),
                ),
            ).then(
                Commands.literal("icon").then(
                    stationArg(suggestions).then(
                        platformArg(suggestions).then(
                            itemArg().playerAction(plugin) { player, context ->
                                plugin.editManager.setPlatformIcon(
                                    player,
                                    context.getArgument("station", String::class.java),
                                    context.getArgument("platform", String::class.java),
                                    context.getArgument("item", String::class.java),
                                )
                            },
                        ),
                    ),
                ),
            ).then(
                Commands.literal("delete").then(
                    stationArg(suggestions).then(
                        platformArg(suggestions).playerAction(plugin) { player, context ->
                            plugin.editManager.confirmDeletePlatform(
                                player,
                                context.getArgument("station", String::class.java),
                                context.getArgument("platform", String::class.java),
                            )
                        },
                    ),
                ),
            ).then(heading())

    private fun heading(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("heading").then(
            stationArg(suggestions).then(
                platformArg(suggestions)
                    .playerAction(plugin) { player, context ->
                        plugin.editManager.setPlatformHeading(
                            player,
                            context.getArgument("station", String::class.java),
                            context.getArgument("platform", String::class.java),
                            player.facing.name,
                        )
                    }.then(
                        Commands
                            .argument("direction", StringArgumentType.word())
                            .suggests(suggestions.headings)
                            .playerAction(plugin) { player, context ->
                                plugin.editManager.setPlatformHeading(
                                    player,
                                    context.getArgument("station", String::class.java),
                                    context.getArgument("platform", String::class.java),
                                    context.getArgument("direction", String::class.java),
                                )
                            },
                    ),
            ),
        )

    private fun nameArg(): RequiredArgumentBuilder<CommandSourceStack, String> =
        Commands.argument("name", StringArgumentType.greedyString())

    private fun itemArg(): RequiredArgumentBuilder<CommandSourceStack, String> =
        Commands.argument("item", StringArgumentType.string()).suggests(suggestions.materials)
}

/** Runs [block] for a player sender. A console sender gets the players-only message and the command fails. */
internal fun <T : ArgumentBuilder<CommandSourceStack, T>> T.playerAction(
    plugin: MetroPlugin,
    block: (Player, CommandContext<CommandSourceStack>) -> Unit,
): T =
    executes { context ->
        val player = playerOrNotify(plugin, context)
        if (player == null) {
            0
        } else {
            block(player, context)
            1
        }
    }

/** Returns the sender as a player. For a console sender it sends the players-only message and returns null. */
internal fun playerOrNotify(
    plugin: MetroPlugin,
    context: CommandContext<CommandSourceStack>,
): Player? {
    val sender = context.source.sender
    if (sender is Player) return sender
    sender.sendMessage(Text.of(plugin.metroConfig.message("command.players-only")))
    return null
}

/** Sets the anchor named in [context] at [location]. A null [location] only tells the player there is no target. */
internal fun setAnchor(
    plugin: MetroPlugin,
    player: Player,
    context: CommandContext<CommandSourceStack>,
    location: Location?,
) {
    if (location == null) {
        player.sendMessage(Text.of(plugin.metroConfig.message("editor.no-target")))
        return
    }
    plugin.anchorEditor.setAnchor(
        player,
        context.getArgument("station", String::class.java),
        context.getArgument("platform", String::class.java),
        context.getArgument("role", String::class.java),
        location,
    )
}

/** Brigadier argument `line`, with suggestions of the existing line ids. */
internal fun lineArg(suggestions: Suggestions): RequiredArgumentBuilder<CommandSourceStack, String> =
    Commands.argument("line", StringArgumentType.word()).suggests(suggestions.lines)

/** Brigadier argument `station`, with suggestions of the existing station ids. */
internal fun stationArg(suggestions: Suggestions): RequiredArgumentBuilder<CommandSourceStack, String> =
    Commands.argument("station", StringArgumentType.word()).suggests(suggestions.stations)

/** Brigadier argument `platform`, with suggestions of the platform ids of the `station` argument. */
internal fun platformArg(suggestions: Suggestions): RequiredArgumentBuilder<CommandSourceStack, String> =
    Commands.argument("platform", StringArgumentType.word()).suggests(suggestions.platforms)

/** Returns the location of the block the player looks at, or null when none is within reach. */
internal fun targetBlock(player: Player): Location? = player.getTargetBlockExact(EDIT_REACH)?.location

/** Returns the location given by the `x`, `y` and `z` arguments, in the player's world. */
internal fun explicitLocation(
    player: Player,
    context: CommandContext<CommandSourceStack>,
): Location =
    Location(
        player.world,
        context.getArgument("x", Double::class.java),
        context.getArgument("y", Double::class.java),
        context.getArgument("z", Double::class.java),
    )
