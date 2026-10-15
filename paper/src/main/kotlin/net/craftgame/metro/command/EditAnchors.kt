package net.craftgame.metro.command

import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import net.craftgame.metro.MetroPlugin
import net.craftgame.metro.config.Permissions

/** Builds the `anchor` subcommand. */
class EditAnchors(
    private val plugin: MetroPlugin,
) {
    private val suggestions = Suggestions(plugin)

    /** `set` is optional. A station named `set` or `clear` needs it, because Brigadier tries the literals first. */
    fun build(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands
            .literal("anchor")
            .requires(permission(Permissions.BUILD))
            .then(Commands.literal("set").then(anchorSet()))
            .then(Commands.literal("clear").then(anchorClear()))
            .then(anchorSet())

    private fun anchorSet(): RequiredArgumentBuilder<CommandSourceStack, String> =
        stationArg(suggestions).then(
            platformArg(suggestions).then(
                roleArg()
                    .playerAction(plugin) { player, context ->
                        setAnchor(plugin, player, context, targetBlock(player))
                    }.then(
                        Commands.argument("x", DoubleArgumentType.doubleArg()).then(
                            Commands.argument("y", DoubleArgumentType.doubleArg()).then(
                                Commands
                                    .argument("z", DoubleArgumentType.doubleArg())
                                    .playerAction(plugin) { player, context ->
                                        setAnchor(plugin, player, context, explicitLocation(player, context))
                                    },
                            ),
                        ),
                    ),
            ),
        )

    private fun anchorClear(): RequiredArgumentBuilder<CommandSourceStack, String> =
        stationArg(suggestions).then(
            platformArg(suggestions).then(
                roleArg().playerAction(plugin) { player, context ->
                    plugin.anchorEditor.clearAnchor(
                        player,
                        context.getArgument("station", String::class.java),
                        context.getArgument("platform", String::class.java),
                        context.getArgument("role", String::class.java),
                    )
                },
            ),
        )

    private fun roleArg(): RequiredArgumentBuilder<CommandSourceStack, String> =
        Commands.argument("role", StringArgumentType.word()).suggests(suggestions.roles)
}
