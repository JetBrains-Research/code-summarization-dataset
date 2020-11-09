package reposanalyzer.logic

import astminer.cli.normalizeParseResult
import astminer.common.model.Node
import astminer.common.preOrder
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import reposanalyzer.config.Granularity
import reposanalyzer.config.Language
import reposanalyzer.config.Task
import reposanalyzer.git.checkoutCommit
import reposanalyzer.git.checkoutHashOrName
import reposanalyzer.git.getCommitsDiff
import reposanalyzer.git.getFirstParentHistory
import reposanalyzer.git.openRepositoryByDotGitDir
import reposanalyzer.git.renameCopyDetection
import reposanalyzer.methods.MethodSummarizer
import reposanalyzer.methods.MethodSummaryStorage
import reposanalyzer.parsing.GumTreeParserFactory
import reposanalyzer.parsing.LabelExtractorFactory
import reposanalyzer.utils.LogStorage
import reposanalyzer.utils.getFilesByLanguage
import reposanalyzer.utils.getNotHiddenNotDirectoryFiles
import reposanalyzer.utils.readFileToString
import java.io.File

class RepoSummarizer(
    private val repoPath: String,
    private val dumpFolder: String,
    private val languages: List<Language>,
    private val task: Task,
    private val granularity: Granularity,
    private val copyDetection: Boolean = false,
    private val hideMethodsNames: Boolean = true,
    private val silent: Boolean = false,
    private val excludeNodes: List<String> = listOf()
) : Runnable {

    enum class State {
        NOT_INITIALIZED,
        LOADED,
        DONE,
        EMPTY_HISTORY,
        INIT_ERROR,
        INIT_BAD_DEF_BRANCH_ERROR,
        RUN_ERROR
    }

    var state = State.NOT_INITIALIZED
        private set

    private val labelExtractorFactory = LabelExtractorFactory()
    private val parserFactory = GumTreeParserFactory()
    private val methodSummarizer = MethodSummarizer(hideMethodsNames)

    private val repoFolderRootPath = repoPath.substringBeforeLast(File.separator)
    private val methodsSummaryPath = dumpFolder + File.separator + "methods.json"
    private val logPath = dumpFolder + File.separator + "log.json"

    private val logStorage = LogStorage()
    private val summaryStorage = MethodSummaryStorage()

    private lateinit var repository: Repository
    private lateinit var git: Git

    private val commitsLog = mutableListOf<RevCommit>()
    private var defaultBranchHead: Ref? = null
    private var currCommit: RevCommit? = null
    private var prevCommit: RevCommit? = null

    private val supportedExtensions = languages.flatMap { it.extensions }

    fun init() {
        if (state != State.NOT_INITIALIZED) {
            return
        }
        val repo = openRepositoryByDotGitDir(repoPath + File.separator + ".git")
        state = when (repo) {
            null -> State.INIT_ERROR
            else -> {
                repository = repo
                git = Git(repository)
                defaultBranchHead = repository.findRef(repository.fullBranch)
                println("Default branch: ${defaultBranchHead?.name}")
                when (defaultBranchHead) {
                    null -> State.INIT_BAD_DEF_BRANCH_ERROR
                    else -> {
                        loadHistory()
                        if (commitsLog.isEmpty()) {
                            State.EMPTY_HISTORY
                        } else {
                            State.LOADED
                        }
                    }
                }
            }
        }
    }

    override fun run() {
        when (state) {
            State.NOT_INITIALIZED -> throw UninitializedPropertyAccessException()
            State.LOADED -> {
                processFirstCommit()
                processAllCommits()
                git.checkoutHashOrName(defaultBranchHead?.name) // back to normal head
                dumpData()
                state = State.DONE
            }
            else -> {} // TODO
        }
    }

    private fun processFirstCommit() {
        currCommit = commitsLog.firstOrNull() ?: return // log must be not empty by state
        currCommit?.let { commit ->
            git.checkoutCommit(commit)
            val files = getNotHiddenNotDirectoryFiles(repoPath) // files must exist
            val filesByLang = getFilesByLanguage(files, languages)
            parseFilesByLanguage(filesByLang)
            logStorage.add(
                currCommit,
                currCommit,
                filesByLang.flatMap { it.value }
                    .map { it.absolutePath.removePrefix(repoPath + File.separator) }
            )
        }
    }

    private fun processAllCommits() {
        currCommit = commitsLog.firstOrNull() ?: return
        for (i in 1 until commitsLog.size) { // from 1 commit, 0 is done
            prevCommit = currCommit
            currCommit = commitsLog[i]
            val diff = git.getCommitsDiff(repository.newObjectReader(), currCommit, prevCommit)
            val supportedFiles = getSupportedFiles(processDiff(diff))
            val filesPatches = absolutePatches(supportedFiles) // files with supported extension
            if (filesPatches.isNotEmpty()) {
                currCommit?.let { commit ->
                    git.checkoutCommit(commit)
                    val files = getNotHiddenNotDirectoryFiles(filesPatches)
                    val filesByLang = getFilesByLanguage(files, languages)
                    parseFilesByLanguage(filesByLang)
                }
            }
            logStorage.add(currCommit, prevCommit, supportedFiles)
            if (!silent) {
                println("prev ${i - 1}: ${prevCommit?.name?.substring(0, 7)} -- ${prevCommit?.shortMessage}")
                println("curr $i: ${currCommit?.name?.substring(0, 7)} -- ${currCommit?.shortMessage}")
                println("raw diff size: ${diff.size}")
                println("-".repeat(10))
            }
        }
    }

    private fun dumpData() {
        val objectMapper = jacksonObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
        val methodsSummaryList = summaryStorage.toJSON(objectMapper)
        val log = logStorage.toJSON(objectMapper)
        File(dumpFolder).mkdirs()
        objectMapper.writeValue(File(methodsSummaryPath), methodsSummaryList)
        objectMapper.writeValue(File(logPath), log)
    }

    private fun parseFilesByLanguage(filesByLang: Map<Language, List<File>>) {
        filesByLang.forEach { (lang, files) ->
            parseFiles(lang, files)
        }
    }

    private fun parseFiles(language: Language, files: List<File>) {
        val parser = parserFactory.getParser(language)
        val labelExtractor = labelExtractorFactory.getLabelExtractor(task, granularity)

        parser.parseFiles(files) { parseResult ->
            val fileContent = readFileToString(parseResult.filePath)
            val relativePath = parseResult.filePath.removePrefix(repoFolderRootPath + File.separator)

            normalizeParseResult(parseResult, true)
            val labeledParseResults = labelExtractor.toLabeledData(parseResult)
            labeledParseResults.forEach { (root, label) ->
                if (!isMethodNew(root, label, parseResult.filePath)) {
                    return@forEach
                }
                root.preOrder().forEach { node ->
                    excludeNodes.forEach {
                        node.removeChildrenOfType(it)
                    }
                }
                val methodSummary = methodSummarizer.summarize(
                    root,
                    label,
                    parseResult.filePath,
                    fileContent,
                    language
                )
                methodSummary.commit = currCommit
                methodSummary.filePath = relativePath
                summaryStorage.add(methodSummary)
            }
        }
    }

    private fun processDiff(diff: List<DiffEntry>): List<String> {
        val filePatches = mutableListOf<String>()
        val addEntries = mutableListOf<DiffEntry>()
        val modifyEntries = mutableListOf<DiffEntry>()
        for (entry in diff) {
            when (entry.changeType) {
                DiffEntry.ChangeType.ADD -> addEntries.add(entry)
                DiffEntry.ChangeType.MODIFY -> modifyEntries.add(entry)
                DiffEntry.ChangeType.COPY -> {} // not implemented? in DiffEntry
                DiffEntry.ChangeType.RENAME -> {} // not implemented? in DiffEntry
                else -> {} // DiffEntry.ChangeType.DELETE -- useless
            }
        }
        if (copyDetection) { // new not copied/renamed files (ADD)
            val copyEntries = repository.renameCopyDetection(addEntries, similarityScore = 100) // 100% similarity
            val copiedFiles = copyEntries.map { it.newPath }
            val newFiles = addEntries.map { it.newPath }.filter { !copiedFiles.contains(it) }
            filePatches.addAll(newFiles)
        } else { // new and maybe copied/renamed files (ADD)
            addEntries.forEach { diffEntry ->
                filePatches.add(diffEntry.newPath) // newPath
            }
        }
        // modified files (MODIFY)
        modifyEntries.forEach { diffEntry ->
            filePatches.add(diffEntry.oldPath) // oldPath
        }
        return filePatches
    }

    private fun absolutePatches(filesPatches: List<String>): List<String> {
        return filesPatches.map { projectPath ->
            repoPath + File.separator + projectPath
        }
    }

    private fun loadHistory() {
        defaultBranchHead?.let { head ->
            commitsLog.addAll(repository.getFirstParentHistory(head.objectId))
        }
        commitsLog.reverse() // reverse history == from oldest commit to newest
    }

    private fun <T : Node> isMethodNew(root: T, label: String, filePath: String): Boolean {
        val fullName = methodSummarizer.getMethodFullName(root, label)
        return summaryStorage.notContains(fullName, filePath)
    }

    private fun getSupportedFiles(filePatches: List<String>): List<String> {
        return filePatches.filter { path ->
            supportedExtensions.any { ext ->
                path.endsWith(ext)
            }
        }
    }
}
