package filtration.logic

import utils.Logger
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock

class WorkEnvironment(
    val mainLogger: Logger? = null,
    val signalThreshold: Int = 5,
    val awaitSleepSeconds: Long = 30
) {
    @Volatile var isShutdown: Boolean = false
        set(value) {
            if (!field) {
                field = value
            }
        }

    private val lock: ReentrantLock = ReentrantLock()
    private val readyCond: Condition = lock.newCondition()
    private val filteredRepos: AtomicInteger = AtomicInteger(0)
    private val currentRepos: AtomicInteger = AtomicInteger(0)

    fun registerReady() {
        filteredRepos.incrementAndGet()
        if (currentRepos.incrementAndGet() >= signalThreshold) {
            signalReady()
            currentRepos.set(0)
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

    fun awaitUntilNextReadyOrTimeout() {
        if (!isShutdown) {
            try {
                lock.lockInterruptibly()
                readyCond.await(awaitSleepSeconds, TimeUnit.SECONDS)
            } catch (e: InterruptedException) {
                // ignore
            } finally {
                lock.unlock()
            }
        }
    }

    fun shutdown() {
        if (!isShutdown) {
            isShutdown = true
            signalReady()
        }
    }

    fun addMessage(message: String) = mainLogger?.add(message)
}
