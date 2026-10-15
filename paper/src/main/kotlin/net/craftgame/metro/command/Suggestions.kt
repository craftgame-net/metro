package net.craftgame.metro.command

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.craftgame.metro.MetroPlugin
import net.craftgame.metro.data.Platform
import org.bukkit.Bukkit
import org.bukkit.Material

/** Returns the ids to suggest for a command being typed. */
private typealias IdSource = (CommandContext<CommandSourceStack>) -> Collection<String>

/** Tab-completion sources for the command arguments. Each one offers the values that start with the typed text. */
class Suggestions(
    plugin: MetroPlugin,
) {
    val lines: SuggestionProvider<CommandSourceStack> = provider { plugin.dataStore.lines.keys }

    val stations: SuggestionProvider<CommandSourceStack> = provider { plugin.dataStore.stations.keys }

    val junctions: SuggestionProvider<CommandSourceStack> = provider { plugin.dataStore.junctions.keys }

    val platforms: SuggestionProvider<CommandSourceStack> =
        SuggestionProvider { context, builder ->
            val station = plugin.dataStore.stations[context.getArgument("station", String::class.java)]
            val prefix = builder.remaining.lowercase()
            for (platform in station?.platforms.orEmpty()) {
                if (platform.id.startsWith(prefix, true)) builder.suggest(platform.id)
            }
            builder.buildFuture()
        }

    val materials: SuggestionProvider<CommandSourceStack> =
        SuggestionProvider { _, builder ->
            val prefix = builder.remaining.lowercase()
            val options = if (prefix.contains(':')) itemKeys else itemNames
            for (id in options) {
                if (id.startsWith(prefix)) builder.suggest(id)
            }
            builder.buildFuture()
        }

    val onlinePlayers: SuggestionProvider<CommandSourceStack> =
        provider { _ -> Bukkit.getOnlinePlayers().map { player -> player.name } }

    val roles: SuggestionProvider<CommandSourceStack> = provider { Platform.ROLES }

    val headings: SuggestionProvider<CommandSourceStack> = provider { HEADINGS }

    val junctionKinds: SuggestionProvider<CommandSourceStack> = provider { JUNCTION_KINDS }

    val junctionSpeeds: SuggestionProvider<CommandSourceStack> = provider { JUNCTION_SPEEDS }

    val lineSpeeds: SuggestionProvider<CommandSourceStack> =
        provider {
            listOf(
                "auto",
                plugin.metroConfig.speed.maxCruise
                    .toString(),
            )
        }

    private fun provider(ids: IdSource): SuggestionProvider<CommandSourceStack> =
        SuggestionProvider { context, builder ->
            val prefix = builder.remaining.lowercase()
            for (id in ids(context)) {
                if (id.startsWith(prefix, true)) builder.suggest(id)
            }
            builder.buildFuture()
        }

    /** Sorted item ids without namespace, e.g. `compass`. */
    private val itemNames: List<String> by lazy { itemIds { it.key.value() } }

    /** Sorted item ids with namespace, e.g. `minecraft:compass`. */
    private val itemKeys: List<String> by lazy { itemIds { it.key.toString() } }

    private fun itemIds(extract: (Material) -> String): List<String> {
        val ids = ArrayList<String>()
        for (material in Material.values()) {
            if (material.isItem) ids.add(extract(material))
        }
        ids.sort()
        return ids
    }

    private companion object {
        val HEADINGS = listOf("north", "east", "south", "west", "auto")
        val JUNCTION_KINDS = listOf("split", "join", "cross")
        val JUNCTION_SPEEDS = listOf("auto")
    }
}
