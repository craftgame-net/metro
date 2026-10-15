package net.craftgame.metro.platform

import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin

/** [Scheduler] on Bukkit's main thread. Bukkit cancels the tasks of [plugin] when it is disabled. */
class PaperScheduler(
    private val plugin: Plugin,
) : Scheduler {
    override fun isServerThread(): Boolean = Bukkit.isPrimaryThread()

    override fun later(
        delay: Long,
        task: () -> Unit,
    ): Scheduler.Task {
        val scheduled = Bukkit.getScheduler().runTaskLater(plugin, Runnable { task() }, delay)
        return Scheduler.Task { scheduled.cancel() }
    }

    override fun repeat(
        delay: Long,
        period: Long,
        task: () -> Unit,
    ): Scheduler.Task {
        val scheduled = Bukkit.getScheduler().runTaskTimer(plugin, Runnable { task() }, delay, period)
        return Scheduler.Task { scheduled.cancel() }
    }
}
