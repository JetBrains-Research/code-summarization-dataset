package reposanalyzer.config

import astminer.cli.ConstructorFilterPredicate
import astminer.cli.MethodFilterPredicate
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import reposanalyzer.utils.AnalysisConfigException
import java.io.File

class AnalysisConfig(
    val configPath: String,
    val isDebug: Boolean = false
) {
    private companion object {
        const val REPOS_DIRS_PATH = "repos_dirs_list_path"
        const val DUMP_DIR_PATH = "dump_dir_path"
        const val LANGUAGES = "languages"
        const val HIDE_METHODS_NAME = "hide_methods_names"
        const val EXCLUDE_CONSTRUCTORS = "exclude_constructors"
        const val REMOVE_AFTER = "remove_repo_after_analysis"
        const val COMMITS_TYPE = "commits_type"
        const val TASK = "task"
        const val GRANULARITY = "granularity"
        const val EXCLUDE_NODES = "exclude_nodes"
        const val LOG_DUMP_THRESHOLD = "log_dump_threshold"
        const val METHODS_DUMP_THRESHOLD = "summary_dump_threshold"
        const val COPY_DETECTION = "copy_detection"
        const val IS_ZIP = "gzip_files"
        const val REMOVE_AFTER_ZIP = "remove_after_gzip"

        const val DEFAULT_LOG_DUMP_THRESHOLD = 200
        const val DEFAULT_METHOD_DUMP_THRESHOLD = 200
    }

    private val pathFields = listOf(REPOS_DIRS_PATH, DUMP_DIR_PATH)
    private val listFields = listOf(LANGUAGES)
    private val intFields = listOf(LOG_DUMP_THRESHOLD, METHODS_DUMP_THRESHOLD)
    private val stringFields = listOf(COMMITS_TYPE, TASK, GRANULARITY)
    private val boolFields = listOf(
        HIDE_METHODS_NAME, EXCLUDE_CONSTRUCTORS, REMOVE_AFTER,
        IS_ZIP, REMOVE_AFTER_ZIP, COPY_DETECTION
    )

    lateinit var reposUrlsPath: String
    lateinit var dumpFolder: String

    val languages: MutableList<Language> = mutableListOf()
    val excludeNodes: List<String> = listOf()

    var logDumpThreshold: Int = DEFAULT_LOG_DUMP_THRESHOLD
    var summaryDumpThreshold: Int = DEFAULT_METHOD_DUMP_THRESHOLD
    var task: Task = Task.NAME
    var granularity: Granularity = Granularity.METHOD
    var commitsType: CommitsType = CommitsType.FIRST_PARENTS_INCLUDE_MERGES
    var copyDetection: Boolean = false
    var hideMethodName: Boolean = false
    var excludeConstructors: Boolean = false
    var removeRepoAfterAnalysis: Boolean = false
    var zipFiles: Boolean = false
    var removeAfterZip: Boolean = false

    val filterPredicates = mutableListOf<MethodFilterPredicate>()
    val supportedExtensions = mutableListOf<String>()

    init {
        val file = File(configPath)
        val jsonMapper = jacksonObjectMapper()
        val jsonNode = jsonMapper.readValue<JsonNode>(file)
        jsonNode.checkFields()
        jsonNode.processAllFields()
        File(dumpFolder).mkdirs()
    }

    private fun JsonNode.processAllFields() {
        this.processPathFields()
        this.processListFields()
        this.processIntFields()
        this.processStringFields()
        this.processBoolFields()
        if (languages.isEmpty()) {
            throw AnalysisConfigException("no language specified")
        }
        if (dumpFolder.isEmpty()) {
            throw AnalysisConfigException("no dump folder specified")
        }
        if (excludeConstructors) {
            filterPredicates.add(ConstructorFilterPredicate())
        }
        supportedExtensions.addAll(languages.flatMap { it.extensions })
    }

    private fun JsonNode.processListFields() = listFields.forEach { field ->
        when (field) {
            LANGUAGES -> this.get(field).processLanguages()
            EXCLUDE_NODES -> this.get(field).processExcludeNodes() // TODO
        }
    }

    private fun JsonNode.processIntFields() = intFields.forEach { field ->
        val value = this.get(field).asInt()
        if (value <= 0) {
            throw AnalysisConfigException("impossible threshold value `$value` for field $field")
        }
        when (field) {
            LOG_DUMP_THRESHOLD -> logDumpThreshold = value
            METHODS_DUMP_THRESHOLD -> summaryDumpThreshold = value
        }
    }

    private fun JsonNode.processPathFields() = pathFields.forEach { field ->
        when (field) {
            REPOS_DIRS_PATH -> reposUrlsPath = this.get(field).asText()
            DUMP_DIR_PATH -> dumpFolder = this.get(field).asText()
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
        val value = this.get(field).asBoolean()
        when (field) {
            HIDE_METHODS_NAME -> hideMethodName = value
            EXCLUDE_CONSTRUCTORS -> excludeConstructors = value
            REMOVE_AFTER -> removeRepoAfterAnalysis = value
            COPY_DETECTION -> copyDetection = value
            IS_ZIP -> zipFiles = value
            REMOVE_AFTER_ZIP -> removeAfterZip = value
        }
    }

    private fun JsonNode.processExcludeNodes() = Unit // TODO

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
        badFields.addAll(boolFields.filter { !this.has(it) })
        badFields.addAll(intFields.filter { !this.has(it) })
        if (badFields.isNotEmpty()) {
            throw AnalysisConfigException("no $badFields fields in config")
        }
    }
}
