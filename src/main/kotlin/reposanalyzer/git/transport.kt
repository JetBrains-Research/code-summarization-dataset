package reposanalyzer.git

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.lib.Ref

fun Git.checkoutHashOrName(name: String?): Ref? = this.checkout().setName(name).call()

fun Git.checkoutCommit(commit: RevCommit): Ref? = this.checkout().setName(commit.name).call()
