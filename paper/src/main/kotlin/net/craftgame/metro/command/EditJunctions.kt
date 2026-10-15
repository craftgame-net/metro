package net.craftgame.metro.command

import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import net.craftgame.metro.MetroPlugin
import net.craftgame.metro.config.Permissions
import org.bukkit.Location
import org.bukkit.entity.Player

/** Builds the `junction` subcommand. */
class EditJunctions(
    private val plugin: MetroPlugin,
) {
    private val suggestions = Suggestions(plugin)

    /** Returns the `junction` command tree. */
    fun build(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands
            .literal("junction")
            .requires(permission(Permissions.BUILD))
            .then(create())
            .then(kind())
            .then(turns())
            .then(speed())
            .then(delete())
            .then(list())
            .then(teleport())

    /**
     * Coordinates are accepted only after an explicit id (`create split j1 10 64 20`). An id is a plain word that
     * can be digits, so `create split 10 64 20` would be ambiguous: is 10 the id or the x?
     */
    private fun create(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("create").then(
            kindArg()
                .playerAction(plugin) { player, context ->
                    val kind = context.getArgument("kind", String::class.java)
                    plugin.junctionEditor.createJunction(player, kind, null, targetBlock(player))
                }.then(
                    Commands
                        .argument("junction", StringArgumentType.word())
                        .playerAction(plugin) { player, context -> create(player, context, targetBlock(player)) }
                        .then(
                            Commands.argument("x", DoubleArgumentType.doubleArg()).then(
                                Commands.argument("y", DoubleArgumentType.doubleArg()).then(
                                    Commands
                                        .argument("z", DoubleArgumentType.doubleArg())
                                        .playerAction(plugin) { player, context ->
                                            create(player, context, explicitLocation(player, context))
                                        },
                                ),
                            ),
                        ),
                ),
        )

    private fun create(
        player: Player,
        context: CommandContext<CommandSourceStack>,
        location: Location?,
    ) {
        plugin.junctionEditor.createJunction(
            player,
            context.getArgument("kind", String::class.java),
            context.getArgument("junction", String::class.java),
            location,
        )
    }

    private fun list(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands
            .literal("list")
            .executes { context ->
                plugin.networkBrowser.listJunctions(context.source.sender, 1)
                1
            }.then(
                ChatPages.pageArg().executes { context ->
                    plugin.networkBrowser.listJunctions(context.source.sender, ChatPages.page(context))
                    1
                },
            )

    private fun teleport(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("teleport").then(
            idArg().playerAction(plugin) { player, context ->
                plugin.networkBrowser.teleportToJunction(player, context.getArgument("junction", String::class.java))
            },
        )

    private fun kind(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("kind").then(
            idArg().then(
                kindArg().playerAction(plugin) { player, context ->
                    plugin.junctionEditor.setJunctionKind(
                        player,
                        context.getArgument("junction", String::class.java),
                        context.getArgument("kind", String::class.java),
                    )
                },
            ),
        )

    private fun turns(): LiteralArgumentBuilder<CommandSourceStack> {
        fun apply(
            player: Player,
            context: CommandContext<CommandSourceStack>,
            value: Boolean?,
        ) {
            plugin.junctionEditor.setJunctionTurns(player, context.getArgument("junction", String::class.java), value)
        }

        return Commands.literal("turns").then(
            idArg()
                .playerAction(plugin) { player, context -> apply(player, context, null) }
                .then(
                    Commands
                        .literal("on")
                        .playerAction(plugin) { player, context -> apply(player, context, true) },
                ).then(
                    Commands
                        .literal("off")
                        .playerAction(plugin) { player, context -> apply(player, context, false) },
                ),
        )
    }

    private fun speed(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("speed").then(
            idArg().then(
                Commands
                    .argument("speed", StringArgumentType.word())
                    .suggests(suggestions.junctionSpeeds)
                    .playerAction(plugin) { player, context ->
                        plugin.junctionEditor.setJunctionSpeed(
                            player,
                            context.getArgument("junction", String::class.java),
                            context.getArgument("speed", String::class.java),
                        )
                    },
            ),
        )

    private fun delete(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("delete").then(
            idArg().playerAction(plugin) { player, context ->
                plugin.junctionEditor.confirmDeleteJunction(player, context.getArgument("junction", String::class.java))
            },
        )

    private fun idArg(): RequiredArgumentBuilder<CommandSourceStack, String> =
        Commands.argument("junction", StringArgumentType.word()).suggests(suggestions.junctions)

    private fun kindArg(): RequiredArgumentBuilder<CommandSourceStack, String> =
        Commands.argument("kind", StringArgumentType.word()).suggests(suggestions.junctionKinds)
}
