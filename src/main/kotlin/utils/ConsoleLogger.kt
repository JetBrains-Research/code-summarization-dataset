package utils

class ConsoleLogger(
    private val isParent: Boolean = false,
    private val parentLogger: Logger? = null
) : Logger {

    override fun add(message: String) {
        println("> $message")
        if (isParent) {
            parentLogger?.add(message)
        }
    }

    override fun addAll(messageList: List<String>) = messageList.forEach { add(it) }

    override fun dump(): Unit = Unit
}
