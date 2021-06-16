package analysis.logic

import analysis.git.AnalysisRepository
import org.eclipse.jgit.revwalk.RevCommit

data class CommonInfo(
    val rootPath: String? = null,
    val repository: AnalysisRepository? = null,
    val commit: RevCommit? = null
)
