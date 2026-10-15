package net.craftgame.metro.route

import net.craftgame.metro.data.Nodes
import net.craftgame.metro.data.Station

/** Turns a rebuild report into the findings shown by `/metro issues`. */
object Issues {
    /**
     * One finding per platform, point or station, so each gets its own teleport. Drops targets for which
     * [exists] is false.
     */
    fun of(
        report: RebuildReport,
        text: WarningText,
        exists: (IssueTarget) -> Boolean = { true },
    ): List<RebuildWarning> {
        val issues = ArrayList<RebuildWarning>()
        for ((stationId, platformId) in platforms(report.unoriented)) {
            val line = text.of("unoriented", "platforms" to Nodes.stop(stationId, platformId))
            issues += RebuildWarning("unoriented", line, IssueTarget.Platform(stationId, platformId))
        }
        for (node in report.conflicts) {
            val line = text.of("heading-conflict", "points" to node)
            issues += RebuildWarning("heading-conflict", line, IssueTarget.Node(node))
        }
        for (station in report.terminals.filter { it !in report.turnarounds }) {
            val line = text.of("terminal-no-turnaround", "stations" to station)
            issues += RebuildWarning("terminal-no-turnaround", line, IssueTarget.Station(station))
        }
        issues += report.junctionErrors
        issues += report.junctionWarnings
        issues += report.searchLimitHits
        for ((stationId, platformId) in platforms(report.missingOut)) {
            val line = text.of("platform-no-out", "platform" to Nodes.stop(stationId, platformId))
            issues += RebuildWarning("platform-no-out", line, IssueTarget.Platform(stationId, platformId))
        }
        for ((stationId, platformId) in platforms(report.boardingDeadEnds)) {
            val line = text.of("boarding-dead-end", "platforms" to Nodes.stop(stationId, platformId))
            issues += RebuildWarning("boarding-dead-end", line, IssueTarget.Platform(stationId, platformId))
        }
        return ordered(issues.filter { issue -> issue.target?.let(exists) ?: true })
    }

    /** Returns [issues] with errors first. The order inside each group is kept. */
    fun ordered(issues: List<RebuildWarning>): List<RebuildWarning> = issues.sortedBy { !it.error }

    /** A block, or a node that is neither station nor junction, always counts as present. */
    fun present(
        stations: Map<String, Station>,
        junctions: Set<String>,
    ): (IssueTarget) -> Boolean {
        fun platform(
            stationId: String,
            platformId: String,
        ) = stations[stationId]?.platform(platformId) != null

        return { target ->
            when (target) {
                is IssueTarget.Junction -> target.id in junctions
                is IssueTarget.Block -> true
                is IssueTarget.Platform -> platform(target.stationId, target.platformId)
                is IssueTarget.Station -> target.stationId in stations
                is IssueTarget.Node -> {
                    val junction = Nodes.parseJunction(target.nodeId)
                    val ref = Nodes.parse(target.nodeId)
                    when {
                        junction != null -> junction.id in junctions
                        ref != null -> platform(ref.stationId, ref.platform)
                        else -> true
                    }
                }
            }
        }
    }

    private fun platforms(nodes: List<String>): List<Pair<String, String>> =
        nodes
            .mapNotNull { Nodes.parse(it) }
            .map { it.stationId to it.platform }
            .distinct()
}
