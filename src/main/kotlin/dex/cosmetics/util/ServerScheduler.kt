package dex.cosmetics.util

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents

/**
 * Tick-based delayed-task queue. Tasks run on the main server thread at end-of-tick when their
 * countdown hits zero. Use for any "do X N ticks from now" need (e.g. GUI frame swaps).
 *
 * Single global queue; safe to schedule from the main thread. Must be initialized once at mod
 * startup via [register].
 */
object ServerScheduler {
    private data class Task(var ticksLeft: Int, val action: () -> Unit)

    private val queue = ArrayDeque<Task>()
    private var registered = false

    fun register() {
        if (registered) return
        registered = true
        ServerTickEvents.END_SERVER_TICK.register { _ ->
            if (queue.isEmpty()) return@register
            val iter = queue.iterator()
            val ready = mutableListOf<Task>()
            while (iter.hasNext()) {
                val t = iter.next()
                t.ticksLeft -= 1
                if (t.ticksLeft <= 0) {
                    ready.add(t)
                    iter.remove()
                }
            }
            // Run after iteration so a task's own schedule() call appends safely.
            for (t in ready) {
                try {
                    t.action()
                } catch (e: Exception) {
                    dex.cosmetics.DexCosmetics.LOGGER.error("Scheduled task threw", e)
                }
            }
        }
    }

    fun clear() = queue.clear()

    /** Schedule [action] to run after [ticks] server ticks. Minimum delay is 1 tick. */
    fun schedule(ticks: Int, action: () -> Unit) {
        queue.add(Task(maxOf(1, ticks), action))
    }
}
