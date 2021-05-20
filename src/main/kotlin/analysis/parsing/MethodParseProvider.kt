package analysis.parsing

import astminer.cli.LabeledResult
import astminer.common.model.Node
import astminer.paths.PathMiner
import astminer.paths.PathRetrievalSettings
import org.eclipse.jgit.revwalk.RevCommit
import analysis.config.AnalysisConfig
import analysis.config.Granularity
import analysis.config.Language
import analysis.logic.AnalysisRepository
import analysis.logic.calculateLinesStarts
import analysis.logic.getFileLinesLength
import analysis.logic.splitToParents
import analysis.methods.MethodIdentity
import analysis.methods.MethodSummary
import analysis.methods.MethodSummaryStorage
import analysis.methods.extractors.getMethodFullName
import analysis.methods.filter.MethodSummaryFilter
import analysis.methods.normalizeAstLabel
import analysis.methods.summarizers.MethodSummarizer
import analysis.methods.summarizers.MethodSummarizersFactory
import analysis.parsing.utils.normalizeParseResult
import analysis.parsing.utils.toPathContextNormalizedToken
import analysis.utils.readFileToString
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class MethodParseProvider(
    private val parsers: ConcurrentHashMap<Language, SafeParser<out Node>>,
    private val summaryStorage: MethodSummaryStorage,
    private val config: AnalysisConfig,
    private val analysisRepo: AnalysisRepository? = null
) {

    private val methodsFilter = MethodSummaryFilter(config.summaryFilterConfig)
    private val pathMiner = PathMiner(PathRetrievalSettings(config.maxPathLength, config.maxPathWidth))

    fun parse(
        files: List<File>,
        language: Language,
        rootPath: String,
        currCommit: RevCommit? = null
    ): Boolean {
        val parser = parsers[language] ?: return false
        val labelExtractor = getLabelExtractor()
        val summarizer = getMethodSummarizer(language)

        files.forEach { file ->
            val parseResult = parser.parseFile(file)
            parseResult.root ?: return@forEach

            normalizeParseResult(parseResult, true)
            val labeledResults = labelExtractor.toLabeledData(parseResult)

            labeledResults.processParseResults(
                summarizer = summarizer,
                language = language,
                rootPath = rootPath,
                filePath = parseResult.filePath,
                currCommit = currCommit
            )
        }
        return true
    }

    private fun List<LabeledResult<out Node>>.processParseResults(
        summarizer: MethodSummarizer,
        language: Language,
        filePath: String,
        rootPath: String,
        currCommit: RevCommit? = null
    ) {
        val fileContent = filePath.readFileToString()
        val fileLinesStarts = filePath.getFileLinesLength().calculateLinesStarts()
        val relativePath = filePath
            .removePrefix(rootPath + File.separator)
            .splitToParents()
            .joinToString("/")

        this.forEach { (root, label) ->
            val methodName = normalizeAstLabel(label)
            val methodFullName = root.getMethodFullName(label, language)
            val (returnType, argsTypes) = when (language) {
                Language.JAVA -> summarizer.extractReturnTypeAndArgs(root)
                Language.PYTHON -> summarizer.extractReturnTypeAndArgs(root, fileContent)
            }
            val realIdentity = MethodIdentity(
                methodName, methodFullName, returnType, argsTypes, relativePath, language
            )
            if (summaryStorage.contains(realIdentity)) {
                return@forEach
            }
            val methodSummary = summarizer.summarize(
                root,
                label,
                fileContent,
                relativePath,
                fileLinesStarts
            )
            // 1. excluding nodes from ast for C2S paths generation
            root.excludeNodes(language)
            // 2. retrieving paths
            methodSummary.paths = root.retrievePaths()
            addCommonInfo(methodSummary, currCommit)

            // methods filtering
            if (methodsFilter.isSummaryGood(methodSummary)) {
                summaryStorage.add(methodSummary)
            }
        }
    }

    private fun <T : Node> T.excludeNodes(language: Language) {
        when (language) {
            Language.PYTHON -> {
                this.excludeNodes(config.excludeNodes)
                if (config.excludeDocNode) {
                    this.excludePythonDocNode()
                }
            }
            Language.JAVA -> if (config.excludeDocNode) {
                this.excludeNodes(listOf(GumTreeJavaTypeLabels.JAVA_DOC) + config.excludeNodes)
            } else {
                this.excludeNodes(config.excludeNodes)
            }
        }
    }

    private fun <T : Node> T.retrievePaths(): List<String> {
        if (!config.isPathMining) return emptyList()
        val paths = pathMiner.retrievePaths(this)
        val pathContexts = paths.map { toPathContextNormalizedToken(it) }
            .shuffled()
            .filter { pathContext -> pathContext.startToken.isNotEmpty() && pathContext.endToken.isNotEmpty() }
            .take(config.maxPaths)
        return pathContexts.map { pathContext ->
            val nodePath = pathContext.orientedNodeTypes.map { node -> node.typeLabel }
            "${pathContext.startToken},${nodePath.joinToString("|")},${pathContext.endToken}"
        }
    }

    private fun addCommonInfo(methodSummary: MethodSummary, currCommit: RevCommit? = null) {
        currCommit?.let {
            methodSummary.commit = it
        }
        analysisRepo?.let {
            methodSummary.repoOwner = analysisRepo.owner
            methodSummary.repoName = analysisRepo.name
            methodSummary.repoLicense = analysisRepo.licence
        }
    }

    private fun getLabelExtractor() = LabelExtractorFactory.getLabelExtractor(
        config.task, Granularity.METHOD, config.hideMethodName, config.filterPredicates
    )

    private fun getMethodSummarizer(lang: Language) =
        MethodSummarizersFactory.getMethodSummarizer(lang, config.hideMethodName)
}
