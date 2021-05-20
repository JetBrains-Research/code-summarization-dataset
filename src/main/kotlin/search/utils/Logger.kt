package search.utils

import analysis.utils.clearFile
import java.io.File
import java.io.FileOutputStream
import java.util.LinkedList
import java.util.Queue

class Logger(
    private val pathToLogFile: String,
    private val isDebug: Boolean = false
) {
    companion object {
        const val DUMP_EVERY_N_MESSAGES = 10
    }

    private val logFile: File = File(pathToLogFile)
    private val messages: Queue<String> = LinkedList()

    init {
        logFile.createNewFile()
        logFile.clearFile()
    }

    fun add(message: String) {
        if (isDebug) {
            println(message)
        }
        messages.add(message)
        if (messages.size > DUMP_EVERY_N_MESSAGES) {
            dump()
        }
    }

    fun dump() = FileOutputStream(logFile, true).bufferedWriter().use { out ->
        while (!messages.isEmpty()) {
            out.appendLine(messages.poll())
        }
    }
}
