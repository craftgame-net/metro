package net.craftgame.metro.data

import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.logging.Logger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SnapshotWriterTest {
    @TempDir
    lateinit var dir: File

    private val logger = Logger.getLogger("test")

    @Test
    fun theLastSnapshotEndsUpOnDiskWithoutATemporaryFile() {
        val file = File(dir, "nested/players.yml")
        val writer = SnapshotWriter(file, logger)

        repeat(50) { writer.submit("snapshot $it") }
        writer.flush()

        assertEquals("snapshot 49", file.readText())
        assertFalse(File(file.parentFile, "players.yml.tmp").exists())
        writer.close()
    }

    @Test
    fun afterCloseWritesRunOnTheCallingThread() {
        val file = File(dir, "data.yml")
        val writer = SnapshotWriter(file, logger)
        writer.submit("before")
        writer.close()
        assertEquals("before", file.readText())

        writer.submit("after")

        assertEquals("after", file.readText())
        // Flushing a closed writer only waits; it must not fail.
        writer.flush()
        assertEquals("after", file.readText())
    }

    @Test
    fun aFailedWriteLeavesTheOldFile() {
        val blocker = File(dir, "blocked")
        blocker.writeText("a file where the folder should be")
        val writer = SnapshotWriter(File(blocker, "data.yml"), logger)
        writer.close()

        writer.submit("lost")

        assertEquals("a file where the folder should be", blocker.readText())
    }
}
