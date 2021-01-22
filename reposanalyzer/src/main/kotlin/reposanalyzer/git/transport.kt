package reposanalyzer.git

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import java.io.IOException

fun Git.checkoutHashOrName(name: String?): Ref? = this.checkout().setName(name).call()

fun Git.checkoutCommit(commit: RevCommit): Ref? = this.checkout().setName(commit.name).call()

fun Repository.getDefaultBranch(): Ref? = try { this.findRef(this.fullBranch) } catch (e: IOException) { null }
