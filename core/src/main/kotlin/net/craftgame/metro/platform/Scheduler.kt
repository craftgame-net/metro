package net.craftgame.metro.platform

/**
 * Runs code on the server thread in a later tick. Implementations run every task on the server thread and stop all
 * of them when the plugin is disabled, so a task nobody cancels ends with the plugin.
 */
interface Scheduler {
    /** A task that has been scheduled. */
    fun interface Task {
        /** Stops the task. Does nothing when it has already run or been stopped. */
        fun cancel()
    }

    /** True when the calling thread is the server thread. */
    fun isServerThread(): Boolean

    /** Runs [task] once after [delay] ticks. A delay of 0 runs it in the next tick. */
    fun later(
        delay: Long = 0,
        task: () -> Unit,
    ): Task

    /** Runs [task] every [period] ticks, the first time after [delay] ticks. */
    fun repeat(
        delay: Long,
        period: Long,
        task: () -> Unit,
    ): Task
}
