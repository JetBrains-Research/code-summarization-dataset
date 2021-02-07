package reposanalyzer.methods

class MethodSummaryStorageStats {
    private val fullNames = mutableSetOf<String>()
    private val visitedFiles = mutableSetOf<String>()

    var totalMethods: Int = 0
    var methodsWithDoc: Int = 0
    var methodsWithComment: Int = 0
    var pathsNumber: Int = 0
    var linesNumber: Long = 0L
    val meanLinesLength: Float
        get() = if (totalMethods != 0) (linesNumber.toFloat() / totalMethods) else 0f

    val totalUniqMethodsFullNames: Int
        get() = fullNames.size

    val totalFiles: Int
        get() = visitedFiles.size

    fun registerMethod(summary: MethodSummary) {
        fullNames.add(summary.fullName)
        visitedFiles.add(summary.filePath)

        totalMethods += 1
        methodsWithDoc += if (summary.doc != null) 1 else 0
        methodsWithComment += if (summary.comment != null) 1 else 0
        pathsNumber += summary.paths.size
        linesNumber += summary.getLinesNumber()
    }

    fun clear() {
        fullNames.clear()
        visitedFiles.clear()
    }
}
