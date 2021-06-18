package analysis.git

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ListBranchCommand.ListMode
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk

/*
 *  git log --first-parent branch
 *
 *      RevWalk().lookUpCommit -- loads only ref to commit, without! data
 *      RevWalk().parseCommit  -- loads commit data
 */
fun Repository.getFirstParentHistory(startObjectId: ObjectId): List<RevCommit> {
    val history = mutableListOf<RevCommit>()
    val revisionWalk = RevWalk(this)
    var currentCommit = revisionWalk.parseCommit(startObjectId)
    while (currentCommit != null) {
        history.add(currentCommit)
        currentCommit = if (currentCommit.parents != null && currentCommit.parents.isNotEmpty()) {
            revisionWalk.parseCommit(currentCommit.getParent(0)) // loads first parent commit data
        } else {
            null // oldest commit in branch -- hasn't parents
        }
    }
    return history
}

data class MergeHistory(
    val isYoungestMerge: Boolean,
    val isOldestMerge: Boolean,
    val history: MutableList<RevCommit>
) {
    val mergeCommitsNumber: Int
        get() = history.size - (if (isYoungestMerge) 0 else 1) - (if (isOldestMerge) 0 else 1)
}

/*
 *   git log --first-parent --merges branch
 */
fun Repository.getMergeCommitsHistory(startObjectId: ObjectId, includeYoungest: Boolean = true): MergeHistory {
    var isYoungestMerge = true
    var isOldestMerge = true
    val history = mutableListOf<RevCommit>()
    val revisionWalk = RevWalk(this)
    var currentCommit = revisionWalk.parseCommit(startObjectId)
    currentCommit?.let {
        // not oldest commit and not merge commit
        if (includeYoungest && currentCommit.parents != null && currentCommit.parentCount != 2) {
            history.add(currentCommit)
            isYoungestMerge = false
        }
    }
    while (currentCommit != null) {
        if (currentCommit.parentCount == 2) { // merge commit has 2 parents
            history.add(currentCommit)
        }
        currentCommit = if (currentCommit.parents != null && currentCommit.parents.isNotEmpty()) {
            revisionWalk.parseCommit(currentCommit.getParent(0)) // loads first parent commit data
        } else {
            history.add(currentCommit) // oldest commit in branch -- hasn't parents
            isOldestMerge = false
            null
        }
    }
    return MergeHistory(isYoungestMerge = isYoungestMerge, isOldestMerge = isOldestMerge, history)
}

/*
 *   listMode: ListMode.ALL     ==   git branch -a
 *             ListMode.REMOTE  ==   git branch -r
 */
fun Git.getBranchesList(listMode: ListMode = ListMode.ALL): MutableList<Ref>? = this.branchList()
    .setListMode(listMode)
    .call()
