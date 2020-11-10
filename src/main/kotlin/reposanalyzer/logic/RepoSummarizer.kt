package reposanalyzer.logic

import astminer.cli.MethodFilterPredicate
import astminer.cli.normalizeParseResult
import astminer.common.model.Node
import astminer.common.preOrder
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import reposanalyzer.config.Config
import reposanalyzer.config.Language
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
import reposanalyzer.utils.absolutePatches
import reposanalyzer.utils.getNotHiddenNotDirectoryFiles
import reposanalyzer.utils.readFileToString
import reposanalyzer.utils.removePrefixPath
import java.io.File

class RepoSummarizer(
    private val repoPath: String,
    private val dumpFolder: String,
    private val config: Config,
    private val filterPredicates: List<MethodFilterPredicate> = listOf()
) : Runnable {

    enum class State {
        NOT_INITIALIZED,
        LOADED,
        DONE,
        EMPTY_HISTORY,
        INIT_ERROR,
        INIT_BAD_DEF_BRANCH_ERROR
    }

    private var state = State.NOT_INITIALIZED

    private val supportedExtensions = config.languages.flatMap { it.extensions }

    private val labelExtractorFactory = LabelExtractorFactory()
    private val parserFactory = GumTreeParserFactory()
    private val methodSummarizer = MethodSummarizer(config.hideMethodsNames)

    private val methodsSummaryPath = dumpFolder + File.separator + "methods.jsonl"
    private val logPath = dumpFolder + File.separator + "log.jsonl"

    private val summaryStorage = MethodSummaryStorage(methodsSummaryPath, config.summaryDumpThreshold)
    private val logStorage = LogStorage(logPath, config.logDumpThreshold)

    private val commitsLog = mutableListOf<RevCommit>()

    private var defaultBranchHead: Ref? = null
    private var currCommit: RevCommit? = null
    private var prevCommit: RevCommit? = null

    private lateinit var repository: Repository
    private lateinit var git: Git

    fun init() {
        if (state != State.NOT_INITIALIZED) {
            return
        }
        File(dumpFolder).mkdirs()
        val repo = openRepositoryByDotGitDir(repoPath + File.separator + ".git")
        state = when (repo) {
            null -> State.INIT_ERROR
            else -> {
                repository = repo
                git = Git(repository)
                defaultBranchHead = repository.findRef(repository.fullBranch)
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
                processCommits()
                dumpData()
                git.checkoutHashOrName(defaultBranchHead?.name) // back to normal head
                state = State.DONE
            }
            else -> {} // TODO
        }
    }

    private fun processCommits() {
        currCommit = commitsLog.firstOrNull() ?: return // log must be not empty by init state
        currCommit?.processCommit(currCommit, repoPath) // process first commit
        for (i in 1 until commitsLog.size) { // process others commits
            prevCommit = currCommit
            currCommit = commitsLog[i]
            val diff = git.getCommitsDiff(repository.newObjectReader(), currCommit, prevCommit)
            val supportedFiles = getSupportedFiles(processDiff(diff), supportedExtensions)
            val filesPatches = absolutePatches(repoPath, supportedFiles) // files with supported extension
            when (filesPatches.isEmpty()) {
                true -> logStorage.add(currCommit, prevCommit, mapOf())
                else -> currCommit?.processCommit(prevCommit, filesPatches = filesPatches)
            }
        }
    }

    private fun RevCommit.processCommit(
        prevCommit: RevCommit? = null,
        dirPath: String? = null,
        filesPatches: List<String>? = null
    ) {
        git.checkoutCommit(this)
        var files = listOf<File>()
        if (dirPath != null) {
            files = getNotHiddenNotDirectoryFiles(repoPath)
        } else if (filesPatches != null) {
            files = getNotHiddenNotDirectoryFiles(filesPatches)
        }
        val filesByLang = getFilesByLanguage(files, config.languages)
        parseFilesByLanguage(filesByLang)
        logStorage.add(
            this, prevCommit,
            removePrefixPath(repoPath + File.separator, filesByLang)
        )
    }

    private fun parseFilesByLanguage(filesByLang: Map<Language, List<File>>) {
        filesByLang.forEach { (lang, files) ->
            if (files.isNotEmpty()) {
                parseFiles(lang, files)
            }
        }
    }

    private fun parseFiles(language: Language, files: List<File>) {
        val parser = parserFactory.getParser(language)
        val labelExtractor = labelExtractorFactory.getLabelExtractor(
            config.task, config.granularity, filterPredicates
        )

        parser.parseFiles(files) { parseResult ->
            val fileContent = readFileToString(parseResult.filePath)
            val relativePath = parseResult.filePath.removePrefix(repoPath)

            normalizeParseResult(parseResult, true)
            val labeledParseResults = labelExtractor.toLabeledData(parseResult)
            labeledParseResults.forEach { (root, label) ->
                if (!isMethodNew(root, label, parseResult.filePath)) {
                    return@forEach
                }
                root.preOrder().forEach { node ->
                    config.excludeNodes.forEach {
                        node.removeChildrenOfType(it)
                    }
                }
                val methodSummary = methodSummarizer.summarize(
                    root,
                    label,
                    fileContent,
                    relativePath,
                    language
                )
                methodSummary.commit = currCommit
                summaryStorage.add(methodSummary)
            }
        }
    }

    private fun dumpData() {
        summaryStorage.dumpData()
        logStorage.dumpData()
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
        if (config.copyDetection) { // new not copied/renamed files (ADD)
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
}
