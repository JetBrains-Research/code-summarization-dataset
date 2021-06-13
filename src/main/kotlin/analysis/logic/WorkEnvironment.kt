package analysis.logic

import analysis.logic.summarizers.Summarizer
import analysis.logic.summarizers.SummarizerStatus
import analysis.utils.Logger
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock

data class ReadyInfo(
    val id: Int,
    val status: SummarizerStatus,
    val type: Summarizer.Type,
    val message: String
) {
    override fun toString(): String {
        return "${type.label} WORKER $id $status $message"
    }
}

class WorkEnvironment(
    val workersPool: ExecutorService,
    val mainLogger: Logger? = null,
    val doneLogger: Logger? = null,
    val awaitSleepSeconds: Long = 30
) {

    companion object {
        fun construct(threadsNumber: Int, mainLogger: Logger? = null, doneLogger: Logger? = null): WorkEnvironment {
            return WorkEnvironment(Executors.newFixedThreadPool(threadsNumber), mainLogger, doneLogger)
        }
    }

    @Volatile var awaitNewWorkers: Boolean = true
        set(value) {
            if (field) {
                field = value
            }
        }

    @Volatile var isShutdown: Boolean = false
        set(value) {
            if (!field) {
                field = value
            }
        }

    private val lock: ReentrantLock = ReentrantLock()
    private val readyCond: Condition = lock.newCondition()
    private val workersCount: AtomicInteger = AtomicInteger(0)

    fun submitWorker(worker: Summarizer): Boolean {
        if (awaitNewWorkers && !isShutdown) {
            workersPool.submit(worker)
            return true
        }
        return false
    }

    fun registerReady(readyInfo: ReadyInfo) {
        workersCount.decrementAndGet()
        signalReady()
        doneLogger?.add(readyInfo.toString())
        doneLogger?.dump()
    }

    fun waitUntilAnyRunning() {
        if (workersCount.get() > 0 && !isShutdown) {
            try {
                lock.lockInterruptibly()
                while (workersCount.get() > 0 && !isShutdown) {
                    readyCond.await(awaitSleepSeconds, TimeUnit.SECONDS)
                }
            } catch (e: InterruptedException) {
                println(e.printStackTrace())
            } finally {
                lock.unlock()
            }
        }
    }

    fun waitUntilNotDoneAndAfterUntilAnyRunning() {
        if (awaitNewWorkers && !isShutdown) {
            try {
                lock.lockInterruptibly()
                while (awaitNewWorkers && !isShutdown) {
                    readyCond.await(awaitSleepSeconds, TimeUnit.SECONDS)
                }
            } catch (e: InterruptedException) {
                // ignore
            } finally {
                lock.unlock()
            }
            waitUntilAnyRunning()
        }
    }

    fun shutdown() {
        if (!isShutdown) {
            isShutdown = true
            signalReady()
            closePool()
        }
    }

    fun signalReady() {
        try {
            lock.lockInterruptibly()
            readyCond.signal()
        } catch (e: InterruptedException) {
            // ignore
        } finally {
            lock.unlock()
        }
    }

    private fun closePool() {
        try {
            workersPool.shutdownNow()
        } catch (e: Exception) {
            // ignore
        }
    }

    fun incrementWorkersCount() = workersCount.incrementAndGet()

    fun addMessage(message: String) = mainLogger?.add(message)
}
