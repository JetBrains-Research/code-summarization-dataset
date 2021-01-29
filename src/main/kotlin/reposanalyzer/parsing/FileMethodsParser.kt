package reposanalyzer.parsing

import astminer.cli.MethodFilterPredicate
import astminer.cli.normalizeParseResult
import astminer.common.model.Node
import astminer.common.model.Parser
import astminer.common.preOrder
import org.eclipse.jgit.revwalk.RevCommit
import reposanalyzer.config.Granularity
import reposanalyzer.config.Language
import reposanalyzer.config.Task
import reposanalyzer.logic.AnalysisRepository
import reposanalyzer.logic.calculateLinesStarts
import reposanalyzer.logic.getFileLinesLength
import reposanalyzer.logic.splitToParents
import reposanalyzer.methods.MethodSummary
import reposanalyzer.methods.MethodSummaryStorage
import reposanalyzer.methods.summarizers.MethodSummarizersFactory
import reposanalyzer.utils.readFileToString
import java.io.File

class FileMethodsParser(
    private val analysisRepo: AnalysisRepository,
    private val summaryStorage: MethodSummaryStorage,
    private val task: Task,
    private val hideMethodsNames: Boolean,
    private val filterPredicates: List<MethodFilterPredicate> = emptyList(),
    private val excludeNodes: List<String> = emptyList()
) {

    fun parse(parser: Parser<out Node>, files: List<File>, language: Language): List<MethodSummary> {
        val labelExtractor = getLabelExtractor()
        val summarizer = getMethodSummarizer(language)
        val results = mutableListOf<MethodSummary>()

        parser.parseFiles(files) { parseResult ->
            val fileContent = parseResult.filePath.readFileToString()
            val fileLinesStarts = parseResult.filePath.getFileLinesLength().calculateLinesStarts()
            val relativePath = parseResult.filePath
                .removePrefix(analysisRepo.path + File.separator)
                .splitToParents()
                .joinToString("/")

            normalizeParseResult(parseResult, true)
            val labeledParseResults = labelExtractor.toLabeledData(parseResult)
            labeledParseResults.forEach { (root, label) ->
                val methodFullName = summarizer.getMethodFullName(root, label)
                if (summaryStorage.contains(methodFullName, parseResult.filePath)) {
                    return@forEach
                }
                root.preOrder().forEach { node ->
                    excludeNodes.forEach {
                        node.removeChildrenOfType(it)
                    }
                }
                val methodSummary = summarizer.summarize(
                    root,
                    label,
                    fileContent,
                    relativePath,
                    fileLinesStarts
                )
                results.add(methodSummary)
            }
        }
        return results
    }

    fun addCommonInfo(methodSummary: MethodSummary, currCommit: RevCommit? = null) {
        currCommit?.let {
            methodSummary.commit = it
        }
        methodSummary.repoOwner = analysisRepo.owner
        methodSummary.repoName = analysisRepo.name
        methodSummary.repoLicense = analysisRepo.licence
    }

    private fun getLabelExtractor() = LabelExtractorFactory.getLabelExtractor(
        task, Granularity.METHOD, hideMethodsNames, filterPredicates
    )

    private fun getMethodSummarizer(lang: Language) =
        MethodSummarizersFactory.getMethodSummarizer(lang, hideMethodsNames)
}
