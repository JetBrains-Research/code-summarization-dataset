package utils

import analysis.utils.clearFile
import analysis.utils.prettyCurrentDate
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentLinkedQueue

class FileLogger(
    private val pathToLogFile: String,
    private val isParent: Boolean = false,
    private val parentLogger: Logger? = null,
    private val dumpEveryNMessages: Int = DUMP_EVERY_N_MESSAGES
) : Logger {

    companion object {
        const val DUMP_EVERY_N_MESSAGES = 5
    }

    private val logFile: File = File(pathToLogFile)
    private val messages: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue<String>()

    init {
        logFile.createNewFile()
        logFile.clearFile()
        add("FILE logger loaded ${prettyCurrentDate()}")
    }

    override fun add(message: String) {
        messages.add(message)
        if (isParent) {
            parentLogger?.add(message)
        }
        if (messages.size >= DUMP_EVERY_N_MESSAGES) {
            dump()
        }
    }

    override fun addAll(messageList: List<String>) = messageList.forEach { add(it) }

    override fun dump() = FileOutputStream(logFile, true).bufferedWriter().use { out ->
        while (!messages.isEmpty()) {
            out.appendLine("> " + messages.poll())
        }
    }
}
