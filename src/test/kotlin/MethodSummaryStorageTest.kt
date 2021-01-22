import org.junit.Test
import reposanalyzer.config.Language
import reposanalyzer.methods.MethodSummary
import reposanalyzer.methods.MethodSummaryStorage
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class MethodSummaryStorageTest {
    private val testFolder =
        File(System.getProperty("user.dir")).resolve("_method_summary_storage_test_tmp")

    private fun createMethodSummary(name: String, fullName: String, filePath: String) =
        MethodSummary(name = name, fullName = fullName, filePath = filePath, language = Language.JAVA)

    private val mName1 = "method1"
    private val mName2 = "method2"
    private val fName1 = "full_name.$mName1"
    private val fName2 = "full_name.$mName2"
    private val path1 = "path1"
    private val path2 = "path2"
    private val ms1 = createMethodSummary(mName1, fName1, path1) //  method 1
    private val ms2 = createMethodSummary(mName1, fName1, path1) //  method 1
    private val ms3 = createMethodSummary(mName1, fName1, path2) //  method 1 with other path
    private val ms4 = createMethodSummary(mName2, fName2, path1) //  method 2

    @BeforeTest
    fun createFolder() {
        testFolder.mkdirs()
    }

    @AfterTest
    fun removeFolder() {
        testFolder.deleteRecursively()
    }

    @Test
    fun containsTest() {
        val file = testFolder.resolve("dump_test_file1.json")
        file.createNewFile()
        val mss = MethodSummaryStorage(file.absolutePath, 1000, null)

        // notContains
        assertTrue(mss.notContains(ms1))
        assertTrue(mss.notContains(ms2))
        assertTrue(mss.notContains(ms3))
        assertTrue(mss.notContains(ms4))

        // contains
        assertFalse(mss.contains(ms1))
        assertFalse(mss.contains(ms2))
        assertFalse(mss.contains(ms3))
        assertFalse(mss.contains(ms4))
    }

    @Test
    fun containsAddedTest() {
        val file = testFolder.resolve("dump_test_file2.json")
        file.createNewFile()
        val mss = MethodSummaryStorage(file.absolutePath, 1000, null)

        // contains added
        assertTrue(mss.add(ms1))
        assertTrue(mss.contains(ms1))
        assertEquals(1, mss.size)

        // duplicates add false
        assertFalse(mss.add(ms2))
        assertEquals(1, mss.size)

        // add with same name but different path
        assertTrue(mss.add(ms3))
        assertTrue(mss.contains(ms3))
        assertEquals(2, mss.size)

        // add with different name same path
        assertTrue(mss.add(ms4))
        assertTrue(mss.contains(ms4))
        assertEquals(3, mss.size)

        // last contains
        assertTrue(mss.contains(ms1))
        assertTrue(mss.contains(ms2))
        assertTrue(mss.contains(ms3))
        assertTrue(mss.contains(ms4))
        assertEquals(3, mss.size)
    }

    @Test
    fun containsAfterDump() {
        val file = testFolder.resolve("dump_test_file3.json")
        file.createNewFile()
        val mss = MethodSummaryStorage(file.absolutePath, 1000, null)

        mss.add(ms1)
        mss.add(ms2)
        mss.add(ms3)
        mss.add(ms4)

        // contains visited after dump
        mss.dump()
        assertTrue(mss.contains(ms1))
        assertTrue(mss.contains(ms2))
        assertTrue(mss.contains(ms3))
        assertTrue(mss.contains(ms4))
        assertEquals(0, mss.size)
    }

    @Test
    fun containsAfterClear() {
        val file = testFolder.resolve("dump_test_file4.json")
        file.createNewFile()
        val mss = MethodSummaryStorage(file.absolutePath, 1000, null)

        mss.add(ms1)
        mss.add(ms2)
        mss.add(ms3)
        mss.add(ms4)

        // not contains after clear
        mss.clear()
        assertFalse(mss.contains(ms1))
        assertFalse(mss.contains(ms2))
        assertFalse(mss.contains(ms3))
        assertFalse(mss.contains(ms4))
    }
}
