package net.craftgame.metro.route

import net.craftgame.metro.MetroPlugin
import net.craftgame.metro.debug.DebugLevel
import net.craftgame.metro.util.blockTarget
import net.craftgame.metro.util.toBlock
import java.util.UUID

/** Returns the texts of rebuild findings, read from lang.yml as `command.<key>`. */
internal fun warningText(plugin: MetroPlugin): WarningText =
    WarningText { key, placeholders -> plugin.metroConfig.message("command.$key", placeholders.toMap()) }

/**
 * Runs [EdgeBuilder.rebuild] on the plugin's data store with the current config. [subject] is the player who started
 * it, or null for the console. Main thread only.
 */
fun EdgeBuilder.rebuild(
    plugin: MetroPlugin,
    subject: UUID?,
): RebuildReport {
    val text = warningText(plugin)
    val started = System.nanoTime()
    val report =
        rebuild(
            EdgeBuilder.Input(
                plugin.dataStore,
                plugin.router,
                plugin.railWorlds,
                plugin.metroConfig.speed,
                plugin.metroConfig.maxSearch,
                text,
                blockTarget = { point -> point.toBlock()?.let(::blockTarget) },
                debugLog = plugin.debugLog,
            ),
            subject,
        )
    plugin.debugLog.log(DebugLevel.EVENT, subject) {
        val issues = Issues.of(report, text)
        val errors = issues.count { it.error }
        val millis = (System.nanoTime() - started) / NANOS_PER_MILLI
        "rebuild done: ${plugin.dataStore.edges.size} edge(s), $errors error(s), ${issues.size - errors} warning(s), " +
            "$millis ms"
    }
    return report
}

private const val NANOS_PER_MILLI = 1_000_000L
