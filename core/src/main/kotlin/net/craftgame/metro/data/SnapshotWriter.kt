package net.craftgame.metro.data

import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Writes text snapshots of a file on one background thread, replacing it atomically.
 * Only the latest pending snapshot is written. After [close], writes run on the calling thread.
 */
class SnapshotWriter(
    private val file: File,
    private val logger: Logger,
) {
    private val pending = AtomicReference<String?>()
    private val lock = Any()
    private val executor: ExecutorService =
        Executors.newSingleThreadExecutor { task ->
            Thread(task, "Metro-${file.name}-writer").apply { isDaemon = true }
        }

    /**
     * Queues [text] as the next content of the file and returns at once. The write runs on the writer thread,
     * or on the calling thread after [close]. A snapshot still pending is dropped in favour of this one.
     */
    fun submit(text: String) {
        pending.set(text)
        try {
            executor.execute(::drain)
        } catch (_: RejectedExecutionException) {
            drain()
        }
    }

    /**
     * Blocks the calling thread until every queued snapshot is on disk, so a following read is not stale.
     * Gives up after [timeoutSeconds] with a warning.
     */
    fun flush(timeoutSeconds: Long = CLOSE_TIMEOUT_SECONDS) {
        try {
            // One thread, in order: once this no-op has run, every earlier write has finished.
            executor.submit {}.get(timeoutSeconds, TimeUnit.SECONDS)
        } catch (_: RejectedExecutionException) {
            // Closed: saves run on the caller's thread, but one may still be finishing on the executor.
            close(timeoutSeconds)
        } catch (_: TimeoutException) {
            logger.warning("Metro: writing ${file.name} did not finish within $timeoutSeconds s")
        } catch (e: ExecutionException) {
            logger.log(Level.WARNING, "Metro: waiting for ${file.name} to be written failed", e)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            logger.warning("Metro: interrupted while waiting for ${file.name} to be written")
        }
    }

    /**
     * Waits up to [timeoutSeconds] for queued writes, then stops the writer thread. Later [submit] calls write on
     * the calling thread.
     */
    fun close(timeoutSeconds: Long = CLOSE_TIMEOUT_SECONDS) {
        executor.shutdown()
        try {
            if (!executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
                logger.warning("Metro: writing ${file.name} did not finish within $timeoutSeconds s")
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            logger.warning("Metro: interrupted while waiting for ${file.name} to be written")
        }
    }

    private fun drain() {
        synchronized(lock) {
            val text = pending.getAndSet(null) ?: return
            try {
                write(text)
            } catch (e: IOException) {
                logger.log(Level.SEVERE, "Metro: could not save ${file.name}", e)
            }
        }
    }

    private fun write(text: String) {
        file.parentFile?.mkdirs()
        val target = file.toPath()
        val tmp = target.resolveSibling("${file.name}.tmp")
        Files.writeString(tmp, text, StandardCharsets.UTF_8)
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private companion object {
        const val CLOSE_TIMEOUT_SECONDS = 10L
    }
}
