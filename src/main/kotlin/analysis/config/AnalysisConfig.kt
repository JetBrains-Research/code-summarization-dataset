package analysis.config

import analysis.config.enums.CommitsType
import analysis.config.enums.Granularity
import analysis.config.enums.SupportedLanguage
import analysis.config.enums.SupportedParser
import analysis.config.enums.Task
import analysis.granularity.method.filter.MethodSummaryFilterConfig
import analysis.utils.AnalysisConfigException
import analysis.utils.BadPathException
import analysis.utils.MissingConfigFieldsException
import analysis.utils.UnsupportedLanguage
import astminer.cli.ConstructorFilterPredicate
import astminer.cli.MethodFilterPredicate
import com.fasterxml.jackson.databind.JsonNode
import utils.loadJSON
import java.io.File

class AnalysisConfig(
    val configPath: String,
    val isDebugAnalyzer: Boolean = false,
    val isDebugWorkers: Boolean = false,
) {
    private companion object {
        // paths
        const val REPOS_URLS_PATH = "repos_urls_path"
        const val FILES_LIST_PATH = "files_list_path"
        const val DUMP_DIR_PATH = "dump_dir_path"

        // list
        const val LANGUAGES = "languages"
        const val EXCLUDE_NODES = "exclude_nodes"
        const val METHOD_UNIQUENESS = "method_uniqueness"

        // string
        const val COMMITS_TYPE = "commits_type"
        const val TASK = "task"
        const val GRANULARITY = "granularity"
        const val PARSER = "parser"

        // bool
        const val IS_HISTORY_MODE = "HISTORY_MODE"
        const val HIDE_METHODS_NAME = "hide_methods_names"
        const val EXCLUDE_CONSTRUCTORS = "exclude_constructors"
        const val IS_GZIP = "gzip_files"
        const val REMOVE_AFTER = "remove_repo_after_analysis"
        const val REMOVE_AFTER_ZIP = "remove_files_after_gzip"
        const val IS_DOT_FORMAT = "ast_dot_format"
        const val C2S_DUMP_FORMAT = "code2sec_format_dump"
        const val EXCLUDE_DOC_NODE = "exclude_doc_node"

        // int
        const val MIN_COMMITS_NUMBER = "min_commits_number"
        const val WORKERS_COUNT = "workers_count"
        const val LOG_DUMP_THRESHOLD = "log_dump_threshold"
        const val METHODS_DUMP_THRESHOLD = "summary_dump_threshold"
        const val MAX_PATHS = "max_paths"
        const val MAX_PATH_WIDTH = "max_path_width"
        const val MAX_PATH_LENGTH = "max_path_length"
        const val MIN_BODY_LINES_LENGTH = "min_body_lines_length" // in MethodSummaryFilterConfig

        // float
        const val MERGES_PART = "merges_part_in_history"

        // defaults
        const val DEFAULT_DATA_DUMP_FOLDER = "data"
        const val DEFAULT_THREADS_COUNT = 1
        const val DEFAULT_LOG_DUMP_THRESHOLD = 200
        const val DEFAULT_METHOD_DUMP_THRESHOLD = 200
    }

    // path fields
    private val pathFields = listOf(REPOS_URLS_PATH, FILES_LIST_PATH, DUMP_DIR_PATH)
    lateinit var filesListPath: String
    lateinit var reposUrlsPath: String
    lateinit var dumpFolder: String
    lateinit var dataDumpFolder: String

    // list fields
    private val listFields = listOf(LANGUAGES, EXCLUDE_NODES, METHOD_UNIQUENESS)
    val languages: MutableList<SupportedLanguage> = mutableListOf()
    val excludeNodes: MutableList<String> = mutableListOf()
    var identityConfig: IdentityConfig = IdentityConfig(emptyList())
    val filterPredicates = mutableListOf<MethodFilterPredicate>()
    val supportedFileExtensions = mutableListOf<String>()
    val summaryFilterConfig = MethodSummaryFilterConfig()

    // string fields
    private val stringFields = listOf(COMMITS_TYPE, TASK, GRANULARITY, PARSER)
    var task: Task = Task.NAME
    var parser: SupportedParser = SupportedParser.GUMTREE
    var granularity: Granularity = Granularity.METHOD
    var commitsType: CommitsType = CommitsType.FIRST_PARENTS_INCLUDE_MERGES

    // bool fields
    private val boolFields = listOf(
        IS_HISTORY_MODE,
        HIDE_METHODS_NAME, EXCLUDE_CONSTRUCTORS, REMOVE_AFTER,
        IS_GZIP, REMOVE_AFTER_ZIP, IS_DOT_FORMAT, C2S_DUMP_FORMAT, EXCLUDE_DOC_NODE
    )
    var isHistoryMode: Boolean = true
    var isAstDotFormat: Boolean = false
    var hideMethodName: Boolean = false
    var excludeConstructors: Boolean = false
    var removeRepoAfterAnalysis: Boolean = false
    var zipFiles: Boolean = false
    var removeAfterZip: Boolean = false
    var excludeDocNode = true
    var isPathMining = false
    var isCode2SeqDump = false
    val copyDetection = false // for JGit files copy detection -- unused, not researched

    // int fields
    private val intFields = listOf(
        WORKERS_COUNT, LOG_DUMP_THRESHOLD,
        METHODS_DUMP_THRESHOLD, MIN_COMMITS_NUMBER,
        MAX_PATHS, MAX_PATH_LENGTH, MAX_PATH_WIDTH
    )
    var workersCount: Int = DEFAULT_THREADS_COUNT
    var logDumpThreshold: Int = DEFAULT_LOG_DUMP_THRESHOLD
    var summaryDumpThreshold: Int = DEFAULT_METHOD_DUMP_THRESHOLD
    var minCommitsNumber: Int = 0

    // float fields
    private val floatFields = listOf(MERGES_PART)
    var mergesPart: Float = 0.0f
    var maxPaths: Int = 0
    var maxPathWidth: Int = 0
    var maxPathLength: Int = 0

    init {
        val jsonNode = loadJSON(configPath)
        jsonNode.checkFields()
        jsonNode.processAllFields()
    }

    private fun JsonNode.checkFields() {
        val badFields = mutableListOf<String>()
        badFields.addAll(pathFields.filter { !this.has(it) })
        badFields.addAll(listFields.filter { !this.has(it) })
        badFields.addAll(stringFields.filter { !this.has(it) })
        badFields.addAll(boolFields.filter { !this.has(it) })
        badFields.addAll(intFields.filter { !this.has(it) })
        badFields.addAll(floatFields.filter { !this.has(it) })
        if (badFields.isNotEmpty()) {
            throw MissingConfigFieldsException("no $badFields fields in config")
        }
    }

    private fun JsonNode.processAllFields() {
        this.processPathFields()
        this.processListFields()
        this.processStringFields()
        this.processBoolFields()
        this.processIntFields()
        this.processFloatFields()
        if (excludeConstructors) {
            filterPredicates.add(ConstructorFilterPredicate())
        }
        summaryFilterConfig.apply {
            this@processAllFields.parseFields()
        }
    }

    private fun JsonNode.processPathFields() {
        pathFields.forEach { field ->
            val value = this.get(field).asText()
            when (field) {
                REPOS_URLS_PATH -> reposUrlsPath = value
                FILES_LIST_PATH -> filesListPath = value
                DUMP_DIR_PATH -> {
                    dumpFolder = value
                    dataDumpFolder = File(dumpFolder).resolve(DEFAULT_DATA_DUMP_FOLDER).absolutePath
                }
            }
        }
        reposUrlsPath.checkPathsExist(REPOS_URLS_PATH)
        filesListPath.checkPathsExist(FILES_LIST_PATH)
        if (dumpFolder.isEmpty()) {
            throw BadPathException("no dump folder `$DUMP_DIR_PATH` specified")
        }
    }

    private fun JsonNode.processListFields() = listFields.forEach { field ->
        when (field) {
            LANGUAGES -> this.get(field).processLanguages()
            EXCLUDE_NODES -> this.get(field).processExcludeNodes()
            METHOD_UNIQUENESS -> {
                identityConfig = IdentityConfig.createFromJson(this.get(field))
            }
        }
    }

    private fun JsonNode.processLanguages() {
        val langLabels = SupportedLanguage.values().map { it.label.toLowerCase() }.toSet()
        this.forEach { maybeLangNode ->
            val maybeLang = maybeLangNode.asText()
            if (!langLabels.contains(maybeLang.toLowerCase())) {
                throw UnsupportedLanguage("unsupported language in field `$LANGUAGES`: $maybeLang")
            }
            for (realLang in SupportedLanguage.values()) {
                if (maybeLang.equals(realLang.label, ignoreCase = true)) {
                    languages.add(realLang)
                    break
                }
            }
        }
        if (languages.isEmpty()) {
            throw UnsupportedLanguage("no languages specified")
        }
        supportedFileExtensions.addAll(languages.flatMap { it.extensions })
    }

    private fun JsonNode.processExcludeNodes() = this.forEach { excludeNodes.add(it.asText()) }

    private fun JsonNode.processStringFields() {
        val valsMap = mutableMapOf<String, String>()
        stringFields.forEach { field -> valsMap[field] = this.get(field).asText() }
        var badField: Pair<String, String>? = null
        when (valsMap[COMMITS_TYPE]) {
            CommitsType.ONLY_MERGES.label -> commitsType = CommitsType.ONLY_MERGES
            CommitsType.FIRST_PARENTS_INCLUDE_MERGES.label -> commitsType = CommitsType.FIRST_PARENTS_INCLUDE_MERGES
            else -> badField = Pair(COMMITS_TYPE, valsMap[COMMITS_TYPE]!!)
        }
        when (valsMap[GRANULARITY]) {
            Granularity.METHOD.label -> granularity = Granularity.METHOD
            else -> badField = Pair(GRANULARITY, valsMap[GRANULARITY]!!)
        }
        when (valsMap[TASK]) {
            Task.NAME.label -> task = Task.NAME
            else -> badField = Pair(TASK, valsMap[TASK]!!)
        }
        when (valsMap[PARSER]) {
            SupportedParser.GUMTREE.label -> parser = SupportedParser.GUMTREE
            else -> badField = Pair(PARSER, valsMap[PARSER]!!)
        }
        badField?.let {
            throw AnalysisConfigException("bad value for field `${it.first}`: `${it.second}`")
        }
    }

    private fun JsonNode.processBoolFields() = boolFields.forEach { field ->
        val value = this.get(field)?.asBoolean()
            ?: throw AnalysisConfigException("bad value type for field `$field`: `${this.get(field)}`")
        when (field) {
            IS_HISTORY_MODE -> isHistoryMode = value
            HIDE_METHODS_NAME -> hideMethodName = value
            EXCLUDE_CONSTRUCTORS -> excludeConstructors = value
            REMOVE_AFTER -> removeRepoAfterAnalysis = value
            IS_GZIP -> zipFiles = value
            REMOVE_AFTER_ZIP -> removeAfterZip = value
            IS_DOT_FORMAT -> isAstDotFormat = value
            C2S_DUMP_FORMAT -> isCode2SeqDump = value
            EXCLUDE_DOC_NODE -> excludeDocNode = value
        }
    }

    private fun JsonNode.processIntFields() = intFields.forEach { field ->
        val excludeCheck = listOf(MIN_COMMITS_NUMBER, MAX_PATHS, MAX_PATH_LENGTH, MAX_PATH_WIDTH)
        val value = this.get(field).asInt()
        if (value < 0 || (value == 0 && !excludeCheck.contains(field))) {
            throw AnalysisConfigException("incorrect value for field `$field`: $value")
        }
        when (field) {
            WORKERS_COUNT -> workersCount = value
            LOG_DUMP_THRESHOLD -> logDumpThreshold = value
            METHODS_DUMP_THRESHOLD -> summaryDumpThreshold = value
            MIN_COMMITS_NUMBER -> minCommitsNumber = value
            MAX_PATHS -> {
                maxPaths = value
                isPathMining = maxPaths > 0
            }
            MAX_PATH_WIDTH -> maxPathWidth = value
            MAX_PATH_LENGTH -> maxPathLength = value
        }
    }

    private fun JsonNode.processFloatFields() = floatFields.forEach { field ->
        val value = this.get(field)?.asDouble()?.toFloat()
            ?: throw AnalysisConfigException("bad value type for field `$field`: `${this.get(field)}`")
        if (value < 0) {
            throw AnalysisConfigException("incorrect value for field `$field`: $value")
        }
        when (field) {
            MERGES_PART -> mergesPart = value
        }
    }
}
