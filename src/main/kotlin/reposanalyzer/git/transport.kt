package reposanalyzer.git

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.revwalk.RevCommit

fun Git.checkoutHashOrName(name: String?) {
    this.checkout().setName(name).call()
}

fun Git.checkoutCommit(commit: RevCommit) {
    this.checkout().setName(commit.name).call()
}
