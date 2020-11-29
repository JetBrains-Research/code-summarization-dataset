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
