package utils

interface Logger {
    fun add(message: String)
    fun addAll(messageList: List<String>)
    fun dump()
}
