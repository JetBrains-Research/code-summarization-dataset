package analysis.granularity.method

import analysis.config.AnalysisConfig
import analysis.config.enums.Granularity
import analysis.config.enums.SupportedLanguage
import analysis.config.enums.SupportedParser
import analysis.granularity.ParseProvider
import analysis.granularity.ParseResult
import analysis.granularity.SummaryStorage
import analysis.granularity.method.extractors.JavaMethodExtractor
import analysis.granularity.method.extractors.MethodExtractor
import analysis.granularity.method.extractors.PythonMethodExtractor
import analysis.granularity.method.extractors.node.getMethodFullName
import analysis.granularity.method.filter.MethodSummaryFilter
import analysis.logic.CommonInfo
import analysis.logic.ParseEnvironment
import analysis.logic.calculateLinesStarts
import analysis.logic.getFileLinesLength
import analysis.logic.splitToParents
import analysis.parsing.LabelExtractorFactory
import analysis.parsing.excludeNodes
import analysis.parsing.normalizeParseResult
import analysis.parsing.retrievePaths
import analysis.utils.readFileToString
import astminer.cli.LabeledResult
import astminer.common.model.Node
import astminer.paths.PathMiner
import astminer.paths.PathRetrievalSettings
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.io.File

class MethodParseProvider(
    private val config: AnalysisConfig,
    private val parseEnv: ParseEnvironment
) : ParseProvider {

    data class FilteredMethodSummary(val methodSummary: MethodSummary, val isGood: Boolean)

    private val methodsFilter = MethodSummaryFilter(config.summaryFilterConfig)
    private val pathMiner = PathMiner(PathRetrievalSettings(config.maxPathLength, config.maxPathWidth))

    override fun parse(
        files: List<File>,
        parser: SupportedParser,
        lang: SupportedLanguage
    ): List<ParseResult> {
        if (files.isEmpty()) {
            return emptyList()
        }
        val methodParser = parseEnv.parsers[parser]?.get(lang) ?: return emptyList()
        val labelExtractor = getLabelExtractor(parser)
        val deferredResults = hashMapOf<String, Deferred<List<LabeledResult<out Node>>>>()
        val parseResults = mutableListOf<ParseResult>()
        runBlocking {
            // parse each file in own coroutine
            files.forEach { file ->
                val deferredResult = async(parseEnv.dispatcher) {
                    val parseResult = methodParser.parseFile(file)
                    parseResult.root ?: return@async emptyList()

                    normalizeParseResult(parseResult, true)
                    labelExtractor.toLabeledData(parseResult)
                }
                deferredResults[file.absolutePath] = deferredResult
            }
            // await all deferred results
            deferredResults.forEach { (filePath, deferredResult) ->
                try {
                    val result = deferredResult.await()
                    parseResults.add(ParseResult(filePath, result = result))
                } catch (e: Exception) {
                    parseResults.add(ParseResult(filePath, exception = e))
                }
            }
        }
        return parseResults
    }

    override fun processParseResults(
        parseResults: List<ParseResult>,
        storage: SummaryStorage,
        lang: SupportedLanguage,
        commonInfo: CommonInfo
    ) = parseResults.forEach { parseResult -> parseResult.processParseResult(storage, lang, commonInfo) }

    private fun ParseResult.processParseResult(
        storage: SummaryStorage,
        lang: SupportedLanguage,
        info: CommonInfo
    ) {
        storage as MethodSummaryStorage
        val summarizer = MethodExtractor.get(lang, config.hideMethodName)
        val fileContent = filePath.readFileToString()
        val fileLinesStarts = filePath.getFileLinesLength().calculateLinesStarts()
        val relativePath = filePath.removePrefix(info.rootPath + File.separator)
            .splitToParents()
            .joinToString("/")

        // 0. checking method wasn't added before
        val newResult = result.filter { labeledResult ->
            val methodIdentity = labeledResult.buildIdentity(fileContent, lang)
            !storage.contains(methodIdentity) // storage isn't thread safe
        }

        val deferredMethodSummaries = mutableListOf<Deferred<FilteredMethodSummary>>()
        runBlocking {
            // data extraction
            newResult.forEach { labeledResult ->
                val deferredSummary = async(parseEnv.dispatcher) {
                    // 1. summarizing method data
                    val methodSummary = summarizer.summarize(
                        labeledResult.root,
                        labeledResult.label,
                        fileContent,
                        relativePath,
                        fileLinesStarts
                    )
                    // 2. excluding nodes from ast
                    labeledResult.root.excludeNodes(lang, config.excludeNodes, config.excludeDocNode)
                    // 3. retrieving paths
                    if (config.isPathMining) {
                        methodSummary.paths = labeledResult.root.retrievePaths(pathMiner, config.maxPaths)
                    }
                    // 4. adding common info if present
                    methodSummary.addCommonInfo(info)
                    // 5. methods filtering
                    FilteredMethodSummary(methodSummary, methodsFilter.isSummaryGood(methodSummary))
                }
                deferredMethodSummaries.add(deferredSummary)
            }
            // await all deferred summaries
            deferredMethodSummaries.forEach { deferredSummary ->
                val summary = deferredSummary.await() // if exception -> go up to worker
                // 5. methods filtering
                if (summary.isGood) {
                    storage.add(summary.methodSummary) // storage isn't thread safe
                }
            }
        }
    }

    private fun MethodSummary.addCommonInfo(info: CommonInfo) {
        info.commit?.let {
            commit = it
        }
        info.repository?.let {
            repoOwner = info.repository.owner
            repoName = info.repository.name
            repoLicense = info.repository.licence
        }
    }

    private fun LabeledResult<out Node>.buildIdentity(fileContent: String, lang: SupportedLanguage): MethodIdentity {
        val methodName = normalizeAstLabel(label)
        val methodFullName = root.getMethodFullName(label, lang)
        val (returnType, argsTypes) = when (lang) {
            SupportedLanguage.JAVA -> JavaMethodExtractor().extractReturnTypeAndArgs(root)
            SupportedLanguage.PYTHON -> PythonMethodExtractor().extractReturnTypeAndArgs(root, fileContent)
        }
        return MethodIdentity(
            methodName, methodFullName, returnType, argsTypes, filePath, lang
        )
    }

    private fun getLabelExtractor(parser: SupportedParser) = LabelExtractorFactory.getLabelExtractor(
        config.task, Granularity.METHOD, config.hideMethodName, config.filterPredicates, parser
    )
}
