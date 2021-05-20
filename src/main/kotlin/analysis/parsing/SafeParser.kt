package analysis.parsing

import astminer.common.model.Node
import astminer.common.model.ParseResult
import astminer.common.model.Parser
import java.io.File
import java.io.InputStream

class SafeParser<T : Node>(val parser: Parser<T>) {

    fun parseInputStream(content: InputStream): T? = try {
        parser.parseInputStream(content)
    } catch (e: Exception) {
        null
    }

    fun parseFile(file: File): ParseResult<T> {
        val node = parseInputStream(file.inputStream())
        return ParseResult(node, file.path)
    }

    fun parseFiles(files: List<File>, handleResult: (ParseResult<T>) -> Any) {
        files.forEach { handleResult(parseFile(it)) }
    }
}
