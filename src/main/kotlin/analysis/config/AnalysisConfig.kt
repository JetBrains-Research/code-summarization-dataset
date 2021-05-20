package analysis.config

import astminer.cli.ConstructorFilterPredicate
import astminer.cli.MethodFilterPredicate
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import analysis.methods.filter.MethodSummaryFilterConfig
import analysis.utils.AnalysisConfigException
import java.io.File

class AnalysisConfig(
    val configPath: String,
    val isDebugAnalyzer: Boolean = false,
    val isDebugSummarizers: Boolean = false,
) {
    private companion object {
        const val IS_HISTORY_MODE = "HISTORY_MODE"

        const val REPOS_URLS_PATH = "repos_urls_path"
        const val DIRS_LIST_PATH = "dirs_list_path"
        const val DUMP_DIR_PATH = "dump_dir_path"
        const val LANGUAGES = "languages"
        const val HIDE_METHODS_NAME = "hide_methods_names"
        const val EXCLUDE_CONSTRUCTORS = "exclude_constructors"
        const val REMOVE_AFTER = "remove_repo_after_analysis"
        const val COMMITS_TYPE = "commits_type"
        const val MIN_COMMITS_NUMBER = "min_commits_number"
        const val MERGES_PART = "merges_part_in_history"
        const val TASK = "task"
        const val GRANULARITY = "granularity"
        const val EXCLUDE_NODES = "exclude_nodes"
        const val THREADS_COUNT = "threads_count"
        const val LOG_DUMP_THRESHOLD = "log_dump_threshold"
        const val METHODS_DUMP_THRESHOLD = "summary_dump_threshold"
        const val COPY_DETECTION = "copy_detection"
        const val IS_ZIP = "gzip_files"
        const val REMOVE_AFTER_ZIP = "remove_files_after_gzip"
        const val DATA_DUMP_FOLDER = "data"
        const val IS_DOT_FORMAT = "ast_dot_format"
        const val METHOD_UNIQUENESS = "method_uniqueness"

        const val MAX_PATHS = "max_paths"
        const val MAX_PATH_WIDTH = "max_path_width"
        const val MAX_PATH_LENGTH = "max_path_length"
        const val C2S_DUMP_FORMAT = "code2sec_format_dump"
        const val EXCLUDE_DOC_NODE = "exclude_doc_node"

        const val DEFAULT_THREADS_COUNT = 1
        const val DEFAULT_LOG_DUMP_THRESHOLD = 200
        const val DEFAULT_METHOD_DUMP_THRESHOLD = 200
    }

    private val pathFields = listOf(REPOS_URLS_PATH, DIRS_LIST_PATH, DUMP_DIR_PATH)
    private val listFields = listOf(LANGUAGES, EXCLUDE_NODES, METHOD_UNIQUENESS)
    private val intFields = listOf(
        THREADS_COUNT, LOG_DUMP_THRESHOLD,
        METHODS_DUMP_THRESHOLD, MIN_COMMITS_NUMBER,
        MAX_PATHS, MAX_PATH_LENGTH, MAX_PATH_WIDTH
    )
    private val stringFields = listOf(COMMITS_TYPE, TASK, GRANULARITY)
    private val boolFields = listOf(
        IS_HISTORY_MODE,
        HIDE_METHODS_NAME, EXCLUDE_CONSTRUCTORS, REMOVE_AFTER,
        IS_ZIP, REMOVE_AFTER_ZIP, COPY_DETECTION, IS_DOT_FORMAT, C2S_DUMP_FORMAT, EXCLUDE_DOC_NODE
    )
    private val floatFields = listOf(MERGES_PART)

    lateinit var dirsListPath: String
    lateinit var reposUrlsPath: String
    lateinit var dumpFolder: String
    lateinit var dataDumpFolder: String

    val languages: MutableList<Language> = mutableListOf()
    val excludeNodes: MutableList<String> = mutableListOf()

    var isHistoryMode: Boolean = true

    var identityConfig: IdentityConfig = IdentityConfig(emptyList())
    var threadsCount: Int = DEFAULT_THREADS_COUNT
    var logDumpThreshold: Int = DEFAULT_LOG_DUMP_THRESHOLD
    var summaryDumpThreshold: Int = DEFAULT_METHOD_DUMP_THRESHOLD
    var task: Task = Task.NAME
    var granularity: Granularity = Granularity.METHOD
    var commitsType: CommitsType = CommitsType.FIRST_PARENTS_INCLUDE_MERGES
    var minBodyLinesLength: Int = 0
    var minCommitsNumber: Int = 0
    var mergesPart: Float = 0.0f
    var isAstDotFormat: Boolean = false
    var copyDetection: Boolean = false
    var hideMethodName: Boolean = false
    var excludeConstructors: Boolean = false
    var removeRepoAfterAnalysis: Boolean = false
    var zipFiles: Boolean = false
    var removeAfterZip: Boolean = false
    var excludeDocNode = true

    var maxPaths: Int = 0
    var maxPathWidth: Int = 0
    var maxPathLength: Int = 0
    var isPathMining = false
    var isCode2SeqDump = false

    val filterPredicates = mutableListOf<MethodFilterPredicate>()
    val supportedExtensions = mutableListOf<String>()
    val summaryFilterConfig = MethodSummaryFilterConfig()

    init {
        val file = File(configPath)
        val jsonMapper = jacksonObjectMapper()
        val jsonNode = jsonMapper.readValue<JsonNode>(file)
        jsonNode.checkFields()
        jsonNode.processAllFields()
    }

    private fun JsonNode.processAllFields() {
        this.processPathFields()
        this.processListFields()
        this.processIntFields()
        this.processStringFields()
        this.processBoolFields()
        this.processFloatFields()
        if (languages.isEmpty()) {
            throw AnalysisConfigException("no language specified")
        }
        if (dumpFolder.isEmpty()) {
            throw AnalysisConfigException("no dump folder specified")
        }
        if (excludeConstructors) {
            filterPredicates.add(ConstructorFilterPredicate())
        }
        summaryFilterConfig.apply {
            this@processAllFields.parseFields()
        }
        supportedExtensions.addAll(languages.flatMap { it.extensions })
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

    private fun JsonNode.processFloatFields() = floatFields.forEach { field ->
        val value = this.get(field).asDouble().toFloat()
        if (value < 0) {
            throw AnalysisConfigException("impossible value `$value` for field $field")
        }
        when (field) {
            MERGES_PART -> mergesPart = value
        }
    }

    private fun JsonNode.processIntFields() = intFields.forEach { field ->
        val excludeCheck = listOf(MIN_COMMITS_NUMBER, MAX_PATHS, MAX_PATH_LENGTH, MAX_PATH_WIDTH)
        val value = this.get(field).asInt()
        if (value < 0 || (value == 0 && !excludeCheck.contains(field))) {
            throw AnalysisConfigException("impossible value `$value` for field $field")
        }
        when (field) {
            THREADS_COUNT -> threadsCount = value
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

    private fun JsonNode.processPathFields() = pathFields.forEach { field ->
        val value = this.get(field).asText()
        when (field) {
            REPOS_URLS_PATH -> reposUrlsPath = value
            DIRS_LIST_PATH -> dirsListPath = value
            DUMP_DIR_PATH -> {
                dumpFolder = value
                dataDumpFolder = File(dumpFolder).resolve(DATA_DUMP_FOLDER).absolutePath
            }
        }
    }

    private fun JsonNode.processStringFields() = stringFields.forEach { field ->
        val value = this.get(field).asText()
        when (field) {
            COMMITS_TYPE -> commitsType = when (value) {
                CommitsType.ONLY_MERGES.label -> CommitsType.ONLY_MERGES
                CommitsType.FIRST_PARENTS_INCLUDE_MERGES.label -> CommitsType.FIRST_PARENTS_INCLUDE_MERGES
                else -> throw AnalysisConfigException("bad value: `$value` for config field `$field`")
            }
            GRANULARITY -> granularity = when (value) {
                Granularity.METHOD.label -> Granularity.METHOD
                else -> throw AnalysisConfigException("bad value: `$value` for config field `$field`")
            }
            TASK -> task = when (value) {
                Task.NAME.label -> Task.NAME
                else -> throw AnalysisConfigException("bad value: `$value` for config field `$field`")
            }
        }
    }

    private fun JsonNode.processBoolFields() = boolFields.forEach { field ->
        val value = this.get(field)?.asBoolean() ?: return@forEach
        when (field) {
            IS_HISTORY_MODE -> isHistoryMode = value
            HIDE_METHODS_NAME -> hideMethodName = value
            EXCLUDE_CONSTRUCTORS -> excludeConstructors = value
            REMOVE_AFTER -> removeRepoAfterAnalysis = value
            COPY_DETECTION -> copyDetection = value
            IS_ZIP -> zipFiles = value
            REMOVE_AFTER_ZIP -> removeAfterZip = value
            IS_DOT_FORMAT -> isAstDotFormat = value
            C2S_DUMP_FORMAT -> isCode2SeqDump = value
            EXCLUDE_DOC_NODE -> excludeDocNode = value
        }
    }

    private fun JsonNode.processExcludeNodes() = this.forEach { excludeNodes.add(it.asText()) }

    private fun JsonNode.processLanguages() = this.forEach { maybeLang ->
        for (realLang in Language.values()) {
            if (maybeLang.asText().equals(realLang.label, ignoreCase = true)) {
                languages.add(realLang)
                break
            }
        }
    }

    private fun JsonNode.checkFields() {
        val badFields = mutableListOf<String>()
        badFields.addAll(pathFields.filter { !this.has(it) })
        badFields.addAll(listFields.filter { !this.has(it) })
        badFields.addAll(stringFields.filter { !this.has(it) })
        badFields.addAll(boolFields.filter { it != COPY_DETECTION && !this.has(it) })
        badFields.addAll(intFields.filter { !this.has(it) })
        if (badFields.isNotEmpty()) {
            throw AnalysisConfigException("no $badFields fields in config")
        }
    }
}
