package net.craftgame.metro.util

import net.craftgame.metro.recordingLogger
import net.craftgame.metro.ride.StationSpot
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayerStateStoreTest {
    @TempDir
    lateinit var dir: File

    private val file get() = File(dir, "players.yml")

    private fun store(): PlayerStateStore = PlayerStateStore(file, recordingLogger())

    private fun id(text: String): UUID = UUID.fromString(text)

    @Test
    fun oddRecordsLoadAndIncompleteOnesAreSkipped() {
        file.writeText(checkNotNull(javaClass.getResource("/data/players-odd.yml")).readText())
        val store = store()
        store.load()

        assertNull(store.lastStation(id("11111111-2222-3333-4444-555555555555")))
        // Unquoted `yes` and `0` are a boolean and a number in YAML and read as text.
        assertEquals(StationSpot("true", "0"), store.lastStation(id("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")))
        assertNull(store.lastStation(id("aaaaaaaa-bbbb-cccc-dddd-ffffffffffff")))
        assertFalse(store.isPendingRescue(id("bbbbbbbb-bbbb-cccc-dddd-eeeeeeeeeeee")))
        store.close()
    }

    @Test
    fun rescuesSurviveARoundTripWithIdsThatLookLikeOtherTypes() {
        val player = UUID(0, 1)
        val store = store()
        store.markPendingRescue(player, StationSpot("on", "0"))
        store.close()

        assertEquals(
            "pending-rescue:\n  $player:\n    station: 'on'\n    platform: '0'\n",
            file.readText(),
        )
        val loaded = store()
        loaded.load()
        assertTrue(loaded.isPendingRescue(player))
        assertEquals(StationSpot("on", "0"), loaded.lastStation(player))
        loaded.close()
    }

    @Test
    fun noRescuesWriteAnEmptyFile() {
        val player = UUID(0, 1)
        val store = store()
        store.markPendingRescue(player, StationSpot("a", "0"))
        store.clearPendingRescue(player)
        store.close()

        assertEquals("", file.readText())
    }

    @Test
    fun anUnreadableFileLoadsNoRescues() {
        file.writeText("pending-rescue: [unclosed\n")
        val store = store()
        store.load()

        assertNull(store.lastStation(UUID(0, 1)))
        store.close()
    }
}
