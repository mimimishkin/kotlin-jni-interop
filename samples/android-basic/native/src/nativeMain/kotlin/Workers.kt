import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.native.concurrent.ObsoleteWorkersApi
import kotlin.native.concurrent.Worker

/**
 * Runs [block] on [count] workers and returns once all of them have finished.
 *
 * A `Worker` is a real OS thread that has to attach to the VM before it may touch a `JniEnv`.
 */
@OptIn(ExperimentalAtomicApi::class, ObsoleteWorkersApi::class)
internal fun runOnWorkers(count: Int, block: () -> Unit) {
    if (count <= 0) return

    val pending = AtomicLong(count.toLong())
    val workers = List(count) { index ->
        val worker = Worker.start(name = "example-worker-$index")
        worker.executeAfter(0) {
            try {
                block()
            } finally {
                pending.addAndFetch(-1L)
            }
        }
        worker
    }

    while (pending.load() > 0L) {
        Worker.current.park(1_000L, process = true)
    }

    workers.forEach { it.requestTermination(processScheduledJobs = false).result }
}
