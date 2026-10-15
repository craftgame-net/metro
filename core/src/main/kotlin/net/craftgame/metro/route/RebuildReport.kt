package net.craftgame.metro.route

import net.craftgame.metro.data.Nodes

/** What a rebuild found. Node ids are as built by [Nodes]. */
data class RebuildReport(
    /** Nodes that the track reaches in two opposite directions. */
    val conflicts: Set<String> = emptySet(),
    /** Station nodes whose travel direction no known direction reached, so no main-line edge was built for them. */
    val unoriented: List<String> = emptyList(),
    /** Ids of stations where a link joins two different platforms, so a ride can turn around there. */
    val turnarounds: Set<String> = emptySet(),
    /** Junction problems that keep the junction out of the graph. */
    val junctionErrors: List<RebuildWarning> = emptyList(),
    /** All other findings: junctions, point placement and the like. */
    val junctionWarnings: List<RebuildWarning> = emptyList(),
    /** `begin` nodes of boarding platforms from which no eject of another station can be reached. */
    val boardingDeadEnds: List<String> = emptyList(),
    /** `begin` nodes of platforms that have `board` and `begin` but no `out`. */
    val missingOut: List<String> = emptyList(),
    /** Ids of the terminal stations when the rebuild ran. */
    val terminals: Set<String> = emptySet(),
    /** One warning per search start that stopped at `rebuild.max-search`. */
    val searchLimitHits: List<RebuildWarning> = emptyList(),
)
