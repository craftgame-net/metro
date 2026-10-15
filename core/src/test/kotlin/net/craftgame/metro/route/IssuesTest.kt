package net.craftgame.metro.route

import net.craftgame.metro.data.Platform
import net.craftgame.metro.data.Station
import net.craftgame.metro.keyText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IssuesTest {
    @Test
    fun splitsMultiObjectFindingsIntoOneEntryPerObject() {
        val report =
            RebuildReport(
                unoriented = listOf("a:p1:out", "a:p1:in", "b:p2:out", "junction.j.ns"),
                conflicts = setOf("c:p1:out", "junction.k.ew"),
                boardingDeadEnds = listOf("d:p1:begin", "e:p3:begin"),
                terminals = setOf("t1", "t2"),
            )

        val issues = Issues.of(report, keyText)

        assertEquals(
            listOf(
                IssueTarget.Node("c:p1:out"),
                IssueTarget.Node("junction.k.ew"),
                IssueTarget.Platform("a", "p1"),
                IssueTarget.Platform("b", "p2"),
                IssueTarget.Station("t1"),
                IssueTarget.Station("t2"),
                IssueTarget.Platform("d", "p1"),
                IssueTarget.Platform("e", "p3"),
            ),
            issues.map { it.target },
        )
        assertEquals("unoriented a:p1", issues[2].text)
        assertEquals("boarding-dead-end e:p3", issues.last().text)
    }

    @Test
    fun terminalsWithATurnaroundAreFine() {
        val report = RebuildReport(turnarounds = setOf("t1"), terminals = setOf("t1", "t2"))
        val issues = Issues.of(report, keyText)
        assertEquals(listOf<IssueTarget?>(IssueTarget.Station("t2")), issues.map { it.target })
    }

    @Test
    fun listsErrorsFirstKeepingTheirOrder() {
        val report =
            RebuildReport(
                junctionErrors = listOf(warning("junction-no-rail", "j1"), warning("junction-bad-block", "j2")),
                junctionWarnings = listOf(warning("junction-unmarked", "w1")),
                searchLimitHits = listOf(warning("search-limit", "s1")),
                unoriented = listOf("a:p1:out"),
            )

        val issues = Issues.of(report, keyText)

        assertEquals(
            listOf("junction-no-rail", "junction-bad-block", "unoriented", "junction-unmarked", "search-limit"),
            issues.map { it.key },
        )
        assertEquals(listOf(true, true, false, false, false), issues.map { it.error })
    }

    @Test
    fun platformWithoutOutGetsItsOwnFinding() {
        val report = RebuildReport(missingOut = listOf("a:p1:begin"))
        val issues = Issues.of(report, keyText)
        assertEquals(listOf("platform-no-out"), issues.map { it.key })
        assertEquals("platform-no-out a:p1", issues.single().text)
        assertEquals(IssueTarget.Platform("a", "p1"), issues.single().target)
    }

    @Test
    fun dropsFindingsAboutDeletedPlaces() {
        val stations = mapOf("a" to Station("a", "A", "world", platforms = mutableListOf(Platform(id = "p1"))))
        val report =
            RebuildReport(
                unoriented = listOf("a:p1:out", "a:p2:out", "gone:p1:out"),
                conflicts = setOf("junction.j.x", "junction.old.x"),
                terminals = setOf("a", "gone"),
                junctionWarnings =
                    listOf(
                        RebuildWarning("junction-unused", "j", IssueTarget.Junction("j")),
                        RebuildWarning("junction-unused", "old", IssueTarget.Junction("old")),
                        RebuildWarning("junction-unmarked", "block", IssueTarget.Block("minecraft:overworld", 0, 0, 0)),
                    ),
            )

        val issues = Issues.of(report, keyText, Issues.present(stations, setOf("j")))

        assertEquals(
            listOf(
                IssueTarget.Node("junction.j.x"),
                IssueTarget.Platform("a", "p1"),
                IssueTarget.Station("a"),
                IssueTarget.Junction("j"),
                IssueTarget.Block("minecraft:overworld", 0, 0, 0),
            ),
            issues.map { it.target },
        )
    }

    @Test
    fun terminalCheckUsesTheSnapshotNotLiveFlags() {
        val report = RebuildReport(terminals = setOf("t1"))
        val stations = mapOf("t1" to Station("t1", "T1", "world", terminal = false))
        val issues = Issues.of(report, keyText, Issues.present(stations, emptySet()))
        assertEquals(listOf<IssueTarget?>(IssueTarget.Station("t1")), issues.map { it.target })
    }

    @Test
    fun orderedIsStable() {
        val list = listOf(warning("unoriented", "1"), warning("heading-conflict", "2"), warning("search-limit", "3"))
        assertEquals(listOf("2", "1", "3"), Issues.ordered(list).map { it.text })
    }

    private fun warning(
        key: String,
        text: String,
    ) = RebuildWarning(key, text)

    @Test
    fun presentChecksNodesByTheirStationPlatformOrJunction() {
        val stations = mapOf("a" to Station("a", "A", "world", platforms = mutableListOf(Platform(id = "0"))))
        val present = Issues.present(stations, setOf("j"))

        assertTrue(present(IssueTarget.Node("a:0:out")))
        assertFalse(present(IssueTarget.Node("a:1:out")))
        assertTrue(present(IssueTarget.Node("junction.j.x")))
        assertFalse(present(IssueTarget.Node("junction.gone.x")))
        assertTrue(present(IssueTarget.Node("something else")))
        assertFalse(present(IssueTarget.Platform("gone", "0")))
        assertFalse(present(IssueTarget.Station("gone")))
    }
}
