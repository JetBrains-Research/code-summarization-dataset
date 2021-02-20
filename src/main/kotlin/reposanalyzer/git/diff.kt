package reposanalyzer.git

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.RenameDetector
import org.eclipse.jgit.lib.ObjectReader
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.treewalk.CanonicalTreeParser

/*
 *  reader from Repository().newObjectReader() 
 */
fun Git.getCommitsDiff(reader: ObjectReader, youngCommit: RevCommit?, oldCommit: RevCommit?): List<DiffEntry> {
    if (youngCommit == null || oldCommit == null) {
        return listOf()
    }
    val newTree = CanonicalTreeParser()
    val oldTree = CanonicalTreeParser()

    // reset() -- position this iterator on the first entry
    newTree.reset(reader, youngCommit.tree)
    oldTree.reset(reader, oldCommit.tree)

    return this.diff()
        .setNewTree(newTree)
        .setOldTree(oldTree)
        .call()
}

/*
 *  similarityScore in range [0, 100] == equality % of bytes between two files
 */
fun Repository.renameCopyDetection(diff: List<DiffEntry>, similarityScore: Int = 60): List<DiffEntry> {
    if (diff.isEmpty()) {
        return listOf()
    }
    val detector = RenameDetector(this)
    detector.renameScore = similarityScore
    detector.addAll(diff)
    return detector.compute().filter { diffEntry -> diffEntry.score >= detector.renameScore }
}

fun List<DiffEntry>.getDiffFiles(repository: Repository, copyDetection: Boolean = false): List<String> {
    val filePatches = mutableListOf<String>()
    val addEntries = mutableListOf<DiffEntry>()
    val modifyEntries = mutableListOf<DiffEntry>()
    this.forEach { entry ->
        when (entry.changeType) {
            DiffEntry.ChangeType.ADD -> addEntries.add(entry)
            DiffEntry.ChangeType.MODIFY -> modifyEntries.add(entry)
            DiffEntry.ChangeType.COPY -> {} // not implemented? in DiffEntry
            DiffEntry.ChangeType.RENAME -> {} // not implemented? in DiffEntry
            else -> {} // DiffEntry.ChangeType.DELETE -- useless
        }
    }
    // EXPERIMENTAL (maybe very slow)
    if (copyDetection) { // new not copied/renamed files (ADD)
        val copyEntries = repository.renameCopyDetection(addEntries, similarityScore = 60) // 100% similarity
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
