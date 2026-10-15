package net.craftgame.metro.command

import net.craftgame.metro.MetroPlugin
import net.craftgame.metro.data.Nodes
import net.craftgame.metro.route.Detours
import net.craftgame.metro.route.EdgeBuilder
import net.craftgame.metro.route.IssueTarget
import net.craftgame.metro.route.Issues
import net.craftgame.metro.route.RebuildReport
import net.craftgame.metro.route.RebuildWarning
import net.craftgame.metro.route.warningText
import net.craftgame.metro.util.StampedCache
import net.craftgame.metro.util.Text
import net.craftgame.metro.util.blockTarget
import net.craftgame.metro.util.toBlock
import net.kyori.adventure.text.Component
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.Locale
import kotlin.math.roundToInt

/** Builds the `/metro issues` output from a rebuild report. */
class RebuildWarnings(
    private val plugin: MetroPlugin,
) {
    private val detours = StampedCache<Unit, List<Detours.Detour>>()
    private val pages = ChatPages(plugin)

    /** Returns the findings of [report], without those whose station, platform or junction was deleted since. */
    fun of(report: RebuildReport): List<RebuildWarning> {
        val store = plugin.dataStore
        return Issues.of(report, warningText(plugin), Issues.present(store.stations, store.junctions.keys))
    }

    /**
     * Sends [page] (from 1) of the issue list of the last rebuild to [sender]. Returns 1, the command's success value.
     */
    fun sendIssues(
        sender: CommandSender,
        page: Int = 1,
    ): Int {
        val report = EdgeBuilder.lastReport
        val body = ArrayList<Component>()
        if (report == null) {
            val text = Text.of(message("command.issues-no-rebuild-yet"))
            val rebuild = run("command.issues-rebuild-link", "/metro rebuild", "command.issues-rebuild-hover")
            sender.sendMessage(Component.textOfChildren(text, Component.space(), rebuild))
        } else {
            val issues = of(report)
            if (issues.isEmpty()) {
                sender.sendMessage(Text.of(message("command.issues-none")))
            } else {
                val errors = issues.count { it.error }
                val header =
                    message(
                        "command.issues-header",
                        "errors" to errors.toString(),
                        "warnings" to (issues.size - errors).toString(),
                    )
                sender.sendMessage(Text.of(header))
                issues.mapTo(body) { line(sender, it) }
            }
        }
        body += hints(sender)
        pages.send(sender, body, page) { "/metro issues $it" }
        return 1
    }

    /** Sends the one-line result of a rebuild. A player gets a link to the list; the console gets the full list. */
    fun sendSummary(
        sender: CommandSender,
        report: RebuildReport,
    ) {
        val issues = of(report)
        val errors = issues.count { it.error }
        val summary =
            Text.of(
                message(
                    "command.rebuild-summary",
                    "segments" to
                        plugin.dataStore.edges.size
                            .toString(),
                    "errors" to errors.toString(),
                    "warnings" to (issues.size - errors).toString(),
                ),
            )
        when {
            issues.isEmpty() -> sender.sendMessage(summary)
            sender is Player -> {
                val show = run("command.issues-show", "/metro issues", "command.issues-show-hover")
                sender.sendMessage(Component.textOfChildren(summary, Component.space(), show))
            }
            else -> {
                sender.sendMessage(summary)
                issues.forEach { sender.sendMessage(Text.of(it.text)) }
            }
        }
    }

    /** Returns [raw] followed by a `[?]` link to the wiki section for the finding [key]. */
    fun link(
        raw: String,
        key: String,
    ): Component =
        Text.withLink(
            raw,
            message("command.docs-link"),
            url(key),
            message("command.docs-hover"),
        )

    private fun line(
        sender: CommandSender,
        warning: RebuildWarning,
    ): Component = withTeleport(sender, link(warning.text, warning.key), warning.target)

    /** Returns the one-way detour lines, cached for the data version and config generation. */
    private fun hints(sender: CommandSender): List<Component> {
        val stamp = plugin.dataStore.version to plugin.metroConfig.generation
        val detours = detours.get(Unit, stamp) { Detours.find(plugin.dataStore, plugin.router) }
        if (detours.isEmpty()) return emptyList()
        val lines = ArrayList<Component>()
        lines += Text.of(message("command.issues-hints"))
        for (detour in detours) {
            val text =
                message(
                    "command.issues-detour",
                    "from" to detour.from,
                    "to" to detour.to,
                    "there" to detour.there.roundToInt().toString(),
                    "back" to detour.back.roundToInt().toString(),
                    "ratio" to String.format(Locale.ROOT, "%.1f", detour.ratio),
                )
            lines += withTeleport(sender, link(text, "detours"), IssueTarget.Station(detour.from))
        }
        return lines
    }

    /** No permission check: `/metro issues` already requires `metro.build`, which the teleport commands need too. */
    private fun withTeleport(
        sender: CommandSender,
        line: Component,
        target: IssueTarget?,
    ): Component {
        val command = target?.takeIf { sender is Player }?.let { IssueTarget.command(it, ::anchorBlock) }
        return if (command == null) {
            line
        } else {
            val teleport = run("command.teleport-link", command, "editor.click-to-teleport")
            Component.textOfChildren(line, Component.space(), teleport)
        }
    }

    private fun anchorBlock(ref: Nodes.Ref): IssueTarget.Block? =
        plugin.dataStore.stations[ref.stationId]
            ?.platform(ref.platform)
            ?.anchor(ref.role)
            ?.toBlock()
            ?.let(::blockTarget)

    private fun run(
        labelKey: String,
        command: String,
        hoverKey: String,
    ): Component = Text.run(message(labelKey), command, message(hoverKey))

    private fun message(
        key: String,
        vararg placeholders: Pair<String, String>,
    ): String = plugin.metroConfig.message(key, *placeholders)

    private fun url(key: String): String = "${plugin.metroConfig.docsUrl}/$PAGE#${ANCHORS[key] ?: SECTION}"

    companion object {
        private const val PAGE = "Maintenance"
        private const val SECTION = "rebuild-warnings"

        /**
         * Wiki heading anchor by finding key. Anchors must match the headings in wiki/Maintenance.md, or the `[?]`
         * links point nowhere.
         */
        val ANCHORS =
            mapOf(
                "unoriented" to "no-direction-of-travel",
                "heading-conflict" to "opposite-directions-meet",
                "terminal-no-turnaround" to "terminal-without-turnaround",
                "boarding-dead-end" to "boarding-leads-nowhere",
                "platform-no-out" to "no-out-anchor",
                "junction-no-rail" to "junction-has-no-rail",
                "junction-duplicate" to "two-junctions-on-one-rail",
                "junction-bad-block" to "junction-does-not-fit",
                "junction-side-split" to "split-entered-from-the-side",
                "junction-join-backward" to "join-ramp-against-travel",
                "point-shared-block" to "two-anchors-on-one-rail",
                "point-on-junction" to "anchor-on-a-junction",
                "junction-branch-dead-end" to "split-branch-leads-nowhere",
                "junction-unoriented" to "junction-path-has-no-direction",
                "junction-unmarked" to "unmarked-switch",
                "junction-unused" to "junction-not-used",
                "search-limit" to "search-gave-up",
                "detours" to "one-way-detours",
            )
    }
}
