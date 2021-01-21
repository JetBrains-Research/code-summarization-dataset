import org.junit.Test
import reposanalyzer.git.isRepoCloned
import reposanalyzer.logic.AnalysisRepository
import reposanalyzer.utils.isDotGitPresent
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnalysisRepositoryTest {
    private val testFolder =
        File(System.getProperty("user.dir")).resolve("_analysis_repo_test_tmp")

    @BeforeTest
    fun createFolder() {
        testFolder.mkdirs()
    }

    @AfterTest
    fun removeFolder() {
        testFolder.deleteRecursively()
    }

    @Test(expected = UninitializedPropertyAccessException::class)
    fun badCloneTest() {
        val owner = "JetBrains-Research"
        val name = "Kotlin_C++_Haskell_Java_Python"
        val repository = AnalysisRepository("", owner, name)
        assertFalse(repository.cloneRepository(testFolder.absolutePath))

        repository.git
        repository.repository
    }

    @Test
    fun goodOnlineCloneTest() {
        val owner = "JetBrains-Research"
        val name = "code-summarization-dataset"
        val repository = AnalysisRepository("", owner, name)

        assertTrue(repository.cloneRepository(testFolder.absolutePath))
        assertTrue(repository.path.isRepoCloned())
        assertTrue(repository.path.isDotGitPresent())

        // lateinit test
        try {
            repository.git
            repository.repository
            repository.defaultBranchHead
        } catch (e: UninitializedPropertyAccessException) {
            assertTrue(false)
        }
    }

    @Test
    fun goodOfflineCloneTest() {
        val owner = "JetBrains-Research"
        val name = "code-summarization-dataset"
        val onlineRepo = AnalysisRepository("", owner, name)

        assertTrue(onlineRepo.cloneRepository(testFolder.absolutePath))
        assertTrue(onlineRepo.path.isRepoCloned())
        assertTrue(onlineRepo.path.isDotGitPresent())

        val repoFromOffline = AnalysisRepository(onlineRepo.path)
        assertTrue(repoFromOffline.path.isRepoCloned())
        assertFalse(repoFromOffline.isLoaded)

        assertTrue(repoFromOffline.openRepositoryByDotGitDir())
        assertTrue(repoFromOffline.isLoaded)
        assertTrue(repoFromOffline.path.isDotGitPresent())
    }
}
