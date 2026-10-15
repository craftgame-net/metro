package net.craftgame.metro.debug

import net.craftgame.metro.RecordingSink
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DebugHubTest {
    private var now = 0L
    private val hub = DebugHub { now }
    private val alice = UUID.randomUUID()
    private val bob = UUID.randomUUID()

    private fun watch(
        viewer: UUID?,
        level: Int,
        scope: DebugScope,
    ): RecordingSink {
        val sink = RecordingSink()
        assertTrue(hub.watch(viewer, DebugHub.Watch(level, scope), sink))
        return sink
    }

    private fun texts(sink: RecordingSink) = sink.lines.map { it.text }

    @Test
    fun allScopeGetsEveryLineUpToItsLevel() {
        val sink = watch(null, 2, DebugScope.All)
        hub.log(1, alice) { "a" }
        hub.log(2, null) { "b" }
        hub.log(3, bob) { "c" }
        assertEquals(listOf("a", "b"), texts(sink))
        assertEquals(alice, sink.lines[0].subject)
    }

    @Test
    fun ownScopeGetsOnlyLinesAboutThePlayer() {
        val sink = watch(alice, 2, DebugScope.Own)
        hub.log(1, alice) { "mine" }
        hub.log(1, bob) { "other" }
        hub.log(1, null) { "global" }
        assertEquals(listOf("mine"), texts(sink))
    }

    @Test
    fun playerScopeGetsOnlyLinesAboutThatPlayer() {
        val sink = watch(null, 3, DebugScope.Player(bob))
        hub.log(3, alice) { "other" }
        hub.log(3, bob) { "bob" }
        hub.log(3, null) { "global" }
        assertEquals(listOf("bob"), texts(sink))
    }

    @Test
    fun viewersHaveTheirOwnLevels() {
        val console = watch(null, 3, DebugScope.All)
        val chat = watch(alice, 1, DebugScope.All)
        hub.log(1, null) { "one" }
        hub.log(3, null) { "three" }
        assertEquals(listOf("one", "three"), texts(console))
        assertEquals(listOf("one"), texts(chat))
    }

    @Test
    fun messageIsNotBuiltWhenNoViewerMatches() {
        watch(alice, 1, DebugScope.Own)
        var built = 0
        hub.log(1, bob) { "x".also { built++ } }
        hub.log(2, alice) { "x".also { built++ } }
        assertEquals(0, built)
        assertFalse(hub.enabled(1, bob))
        assertFalse(hub.enabled(2, alice))
        assertTrue(hub.enabled(1, alice))
        hub.log(1, alice) { "x".also { built++ } }
        assertEquals(1, built)
    }

    @Test
    fun messageIsBuiltOnceForSeveralViewers() {
        watch(null, 1, DebugScope.All)
        watch(alice, 1, DebugScope.All)
        var built = 0
        hub.log(1, null) { "x".also { built++ } }
        assertEquals(1, built)
    }

    @Test
    fun nothingIsEnabledWithoutViewers() {
        assertFalse(hub.enabled(1, null))
        watch(null, 1, DebugScope.All)
        hub.stop(null)
        assertFalse(hub.enabled(1, null))
    }

    @Test
    fun chatViewerCannotWatchLevelThree() {
        assertFalse(hub.watch(alice, DebugHub.Watch(3, DebugScope.Own), RecordingSink()))
        assertEquals(null, hub.watchOf(alice))
    }

    @Test
    fun consoleCannotWatchOwn() {
        assertFalse(hub.watch(null, DebugHub.Watch(1, DebugScope.Own), RecordingSink()))
    }

    @Test
    fun watchReplacesThePreviousOne() {
        val first = watch(alice, 1, DebugScope.Own)
        val second = watch(alice, 2, DebugScope.Own)
        hub.log(2, alice) { "x" }
        assertTrue(first.lines.isEmpty())
        assertEquals(1, second.lines.size)
        assertEquals(DebugHub.Watch(2, DebugScope.Own), hub.watchOf(alice))
    }

    @Test
    fun chatViewerIsRateLimitedAndToldAboutTheDroppedLines() {
        val chat = watch(alice, 1, DebugScope.All)
        val console = watch(null, 1, DebugScope.All)
        repeat(8) { hub.log(1, null) { "line $it" } }
        assertEquals(DebugHub.CHAT_LINES, chat.lines.size)
        assertEquals(8, console.lines.size)
        now += DebugHub.CHAT_WINDOW_MILLIS
        hub.log(1, null) { "later" }
        assertEquals(listOf(3), chat.suppressed)
        assertEquals("later", chat.lines.last().text)
    }
}
