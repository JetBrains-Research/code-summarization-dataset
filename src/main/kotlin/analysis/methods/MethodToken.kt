package analysis.methods

data class MethodToken(
    var id: Long,
    var name: String,
    var type: String
) {
    fun toMap(): Map<String, String> {
        return mapOf(
            Pair("name", name),
            Pair("type", type)
        )
    }
}
