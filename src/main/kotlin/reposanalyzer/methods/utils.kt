package reposanalyzer.methods

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

/*
 *  From astminer sources
 */
fun normalizeAstLabel(label: String): String =
    label.replace("[^A-z^0-9^_]".toRegex(), "_")

/*
 *  Parent pair == [node ast label, node ast type label]
 */
fun buildNormalizedFullName(label: String, parents: List<Pair<String, String>>? = null): String {
    val normalizedLabel = normalizeAstLabel(label)
    if (parents == null || parents.isEmpty()) {
        return normalizedLabel
    }
    return parents.reversed().joinToString(separator = ".", postfix = ".") { (label, _) ->
        normalizeAstLabel(label)
    } + normalizedLabel
}

fun extractContent(content: String, pos: Int, length: Int): String? {
    if (length != 0 && pos + length <= content.length) {
        return content.substring(pos, pos + length).trimStart().trimEnd()
    }
    return null
}

fun getObjectMapper(objectMapper: ObjectMapper? = null) =
    objectMapper ?: jacksonObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)

