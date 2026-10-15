package net.craftgame.metro.route

import net.craftgame.metro.MetroPlugin
import net.craftgame.metro.debug.DebugLevel
import org.bukkit.Bukkit
import org.bukkit.Chunk
import org.bukkit.World

/**
 * Loads the chunks a rebuild will read, so the rebuild does not stall the main thread on synchronous loads.
 * At most one preload runs at a time. Each loaded chunk gets a plugin ticket from [ChunkTickets], so it stays
 * loaded until the rebuild callback has returned or the preload is cancelled. All callbacks run on the main thread.
 */
class ChunkPreloader(
    private val plugin: MetroPlugin,
) {
    private var job: Job? = null

    /** True from [preload] until its `then` callback has returned or [cancel] was called. */
    val running: Boolean get() = job != null

    /**
     * Starts loading the chunks of the stored graph without blocking, so call it on the main thread.
     * Returns false and does nothing when a preload is already running.
     *
     * - [onStart] runs at once on the calling thread with the number of chunks. It is skipped when there are none.
     * - [onProgress] runs on the main thread after a chunk has loaded, when another quarter of the chunks is done or
     *   [PROGRESS_INTERVAL_NANOS] have passed since the last report.
     * - [then] runs on the main thread in a later tick, once every chunk has loaded. The chunks are still held then.
     *   They are released when [then] returns. It does not run after [cancel].
     */
    fun preload(
        onStart: (total: Int) -> Unit,
        onProgress: (done: Int, total: Int) -> Unit,
        then: () -> Unit,
    ): Boolean {
        if (job != null) return false
        val requests = requests(ChunkSet.forRebuild(plugin.dataStore))
        val started = Job(requests, onProgress, then)
        job = started
        plugin.debugLog.log(DebugLevel.DECISION, null) { "chunk preload: ${requests.size} chunks to load" }
        if (requests.isNotEmpty()) onStart(requests.size)
        started.pump()
        return true
    }

    /** Stops the running preload, if any, and releases its chunk tickets. `then` of [preload] will not run. */
    fun cancel() {
        job?.cancel()
        job = null
    }

    private fun requests(chunks: ChunkSet): List<Pair<World, Long>> {
        val requests = ArrayList<Pair<World, Long>>()
        for ((name, keys) in chunks.byWorld()) {
            val world = Bukkit.getWorld(name)
            if (world == null) {
                plugin.logger.warning("Metro: world '$name' is not loaded, not preloading its ${keys.size} chunk(s)")
                continue
            }
            keys.mapTo(requests) { world to it }
        }
        return requests
    }

    private inner class Job(
        requests: List<Pair<World, Long>>,
        private val onProgress: (done: Int, total: Int) -> Unit,
        private val then: () -> Unit,
    ) {
        private val queue = ArrayDeque(requests)
        private val total = requests.size
        private val tickets = ArrayList<Pair<World, Long>>()
        private var inFlight = 0
        private var done = 0
        private var pumping = false
        private var finished = false
        private var cancelled = false
        private var reportedQuarter = 0

        /** [System.nanoTime] of the last progress report. */
        private var reportedAt = System.nanoTime()

        fun pump() {
            if (pumping || cancelled) return
            pumping = true
            try {
                while (inFlight < MAX_IN_FLIGHT && queue.isNotEmpty()) request(queue.removeFirst())
            } finally {
                pumping = false
            }
            if (done == total && !finished) finish()
        }

        fun cancel() {
            cancelled = true
            release()
        }

        private fun request(target: Pair<World, Long>) {
            val (world, key) = target
            inFlight++
            world.getChunkAtAsync(ChunkSet.chunkX(key), ChunkSet.chunkZ(key)).whenComplete { chunk, error ->
                onMain { landed(world, chunk, error) }
            }
        }

        private fun landed(
            world: World,
            chunk: Chunk?,
            error: Throwable?,
        ) {
            if (cancelled) return
            inFlight--
            done++
            if (chunk == null) {
                plugin.logger.warning("Metro: could not preload a chunk in '${world.name}': ${error?.message}")
            } else {
                plugin.chunkTickets.acquire(world.name, chunk.x, chunk.z)
                tickets.add(world to ChunkSet.key(chunk.x, chunk.z))
            }
            progress()
            pump()
        }

        private fun progress() {
            if (done >= total) return
            val quarter = done * QUARTERS / total
            val now = System.nanoTime()
            if (quarter > reportedQuarter || now - reportedAt >= PROGRESS_INTERVAL_NANOS) {
                reportedQuarter = quarter
                reportedAt = now
                onProgress(done, total)
            }
        }

        private fun finish() {
            finished = true
            // A task of its own: an exception thrown by [then] is logged by the scheduler instead of
            // breaking the chunk-load callback that got us here.
            plugin.scheduler.later {
                if (!cancelled) {
                    try {
                        then()
                    } finally {
                        release()
                        job = null
                    }
                }
            }
        }

        private fun release() {
            if (tickets.isNotEmpty()) {
                plugin.debugLog.log(DebugLevel.DECISION, null) { "chunk preload: released ${tickets.size} chunks" }
            }
            for ((world, key) in tickets) {
                plugin.chunkTickets.release(world.name, ChunkSet.chunkX(key), ChunkSet.chunkZ(key))
            }
            tickets.clear()
        }

        private fun onMain(action: () -> Unit) {
            if (plugin.scheduler.isServerThread()) {
                action()
            } else {
                plugin.scheduler.later { action() }
            }
        }
    }

    private companion object {
        /** Most chunk loads requested at once. */
        const val MAX_IN_FLIGHT = 48

        /** Progress is reported when another quarter of the chunks is done. */
        const val QUARTERS = 4

        /** Nanoseconds (2 seconds) after which progress is reported even if no quarter was completed. */
        const val PROGRESS_INTERVAL_NANOS = 2_000_000_000L
    }
}
