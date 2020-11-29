package reposanalyzer.utils

import java.io.File
import java.io.FileOutputStream
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

class WorkLogger(
    private val pathToLogFile: String,
    private val isDebug: Boolean = false
) {
    companion object {
        const val DUMP_EVERY_N_MESSAGES = 10
    }

    private val logFile: File = File(pathToLogFile)
    private val messages: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue<String>()

    init {
        logFile.createNewFile()
        clearFile()
        add("> logger loaded at ${Date(System.currentTimeMillis())}")
    }

    fun add(message: String) {
        if (isDebug) {
            println(message)
        }
        messages.add(message)
        if (messages.size >= DUMP_EVERY_N_MESSAGES) {
            dump()
        }
    }

    fun dump() {
        FileOutputStream(logFile, true).bufferedWriter().use { out ->
            while (!messages.isEmpty()) {
                out.appendLine(messages.poll())
            }
        }
    }

    private fun clearFile() {
        FileOutputStream(logFile, false).bufferedWriter()
    }
}
