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

    private fun createMethodSummary(
        fullName: String,
        filePath: String,
        argsTypes: List<String> = emptyList(),
        returnType: String? = null
    ) = MethodSummary(
        name = "",
        splittedName = "",
        argsTypes = argsTypes,
        returnType = returnType,
        fullName = fullName,
        filePath = filePath,
        language = Language.JAVA
    )

    private val mName1 = "method1"
    private val mName2 = "method2"
    private val fName1 = "full_name.$mName1"
    private val fName2 = "full_name.$mName2"
    private val path1 = "path1"
    private val path2 = "path2"
    private val argsTypes1 = listOf("int", "String")
    private val argsTypes2 = listOf("String", "int")
    private val argsTypes3 = listOf("Type")

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
        val mss = MethodSummaryStorage(
            testFolder.absolutePath, false, isCode2SecDump = false, 1000
        )

        val ms1 = createMethodSummary(fName1, path1, argsTypes1) //  method 1
        val ms2 = createMethodSummary(fName1, path1, argsTypes1) //  method 1
        val ms3 = createMethodSummary(fName1, path2, argsTypes1) //  method 1 with other path
        val ms4 = createMethodSummary(fName2, path1, argsTypes2) //  method 2

        // notContains
        assertFalse(mss.contains(ms1))
        assertFalse(mss.contains(ms2))
        assertFalse(mss.contains(ms3))
        assertFalse(mss.contains(ms4))

        // contains
        assertFalse(mss.contains(ms1))
        assertFalse(mss.contains(ms2))
        assertFalse(mss.contains(ms3))
        assertFalse(mss.contains(ms4))
    }

    @Test
    fun containsAddedTest() {
        val mss = MethodSummaryStorage(
            testFolder.absolutePath, false, isCode2SecDump = false, 1000
        )

        val ms1 = createMethodSummary(fName1, path1, argsTypes1) //  method 1
        val ms2 = createMethodSummary(fName1, path1, argsTypes1) //  method 1
        val ms3 = createMethodSummary(fName1, path2, argsTypes1) //  method 1 with other path
        val ms4 = createMethodSummary(fName2, path1, argsTypes2) //  method 2
        val ms5 = createMethodSummary(fName2, path1, argsTypes3) //  method 3
        val ms6 = createMethodSummary(fName1, path1, argsTypes3) //  method 4

        // contains added
        assertTrue(mss.add(ms1))
        assertTrue(mss.contains(ms1))
        assertEquals(1, mss.size)

        // duplicates add false
        assertFalse(mss.add(ms2))
        assertEquals(1, mss.size)

        // add with different name same path
        assertTrue(mss.add(ms4))
        assertTrue(mss.contains(ms4))
        assertEquals(2, mss.size)

        // add with different args with same name and path
        assertTrue(mss.add(ms5))
        assertTrue(mss.contains(ms5))
        assertEquals(3, mss.size)

        // last contains
        assertTrue(mss.contains(ms1))
        assertTrue(mss.contains(ms2))
        assertTrue(mss.contains(ms3))
        assertTrue(mss.contains(ms4))
        assertTrue(mss.contains(ms5))
        assertFalse(mss.contains(ms6))
        assertEquals(3, mss.size)
    }

    @Test
    fun containsAfterDump() {
        val mss = MethodSummaryStorage(
            testFolder.absolutePath, false, isCode2SecDump = false, 1000
        )

        val ms1 = createMethodSummary(fName1, path1, argsTypes1) //  method 1
        val ms2 = createMethodSummary(fName1, path1, argsTypes1) //  method 1
        val ms3 = createMethodSummary(fName1, path2, argsTypes1) //  method 1 with other path
        val ms4 = createMethodSummary(fName2, path1, argsTypes2) //  method 2

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
        val mss = MethodSummaryStorage(
            testFolder.absolutePath, false, isCode2SecDump = false, 1000
        )

        val ms1 = createMethodSummary(fName1, path1, argsTypes1) //  method 1
        val ms2 = createMethodSummary(fName1, path1, argsTypes1) //  method 1
        val ms3 = createMethodSummary(fName1, path2, argsTypes1) //  method 1 with other path
        val ms4 = createMethodSummary(fName2, path1, argsTypes2) //  method 2

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
