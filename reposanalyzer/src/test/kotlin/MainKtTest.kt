import org.junit.Test
import reposanalyzer.config.Language
import reposanalyzer.methods.MethodSummary
import reposanalyzer.methods.MethodSummaryStorage
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class MainKtTest {

    private fun createMethodSummary(name: String, fullName: String, filePath: String) =
        MethodSummary(name = name, fullName = fullName, filePath = filePath, language = Language.JAVA)

    @Test
    fun methodStorageTest() {
        val file = File("dump_file")
        val methodSummaryStorage = MethodSummaryStorage(file.absolutePath, 1000, null)

        val (m_name1, m_name2) = Pair("method1", "method2")
        val (f_name1, f_name2) = Pair("full.$m_name1", "full.$m_name2")
        val (path1, path2) = Pair("path1", "path2")

        val ms1 = createMethodSummary(m_name1, f_name1, path1) //  method 1
        val ms2 = createMethodSummary(m_name1, f_name1, path1) //  method 1
        val ms3 = createMethodSummary(m_name1, f_name1, path2) //  method 1 with other path
        val ms4 = createMethodSummary(m_name2, f_name2, path1) //  method 2

        // notContains
        assertTrue(methodSummaryStorage.notContains(ms1))
        assertTrue(methodSummaryStorage.notContains(ms2))
        assertTrue(methodSummaryStorage.notContains(ms3))
        assertTrue(methodSummaryStorage.notContains(ms4))

        // contains
        assertFalse(methodSummaryStorage.contains(ms1))
        assertFalse(methodSummaryStorage.contains(ms2))
        assertFalse(methodSummaryStorage.contains(ms3))
        assertFalse(methodSummaryStorage.contains(ms4))

        // contains added
        assertTrue(methodSummaryStorage.add(ms1))
        assertTrue(methodSummaryStorage.contains(ms1))
        assertEquals(1, methodSummaryStorage.size)

        // duplicates contains yes
        assertTrue(methodSummaryStorage.contains(ms2))

        // duplicates add false
        assertFalse(methodSummaryStorage.add(ms2))
        assertEquals(1, methodSummaryStorage.size)

        // add with same name but different path
        assertTrue(methodSummaryStorage.add(ms3))
        assertTrue(methodSummaryStorage.contains(ms3))
        assertEquals(2, methodSummaryStorage.size)

        // add with different name same path
        assertTrue(methodSummaryStorage.add(ms4))
        assertTrue(methodSummaryStorage.contains(ms4))
        assertEquals(3, methodSummaryStorage.size)

        // last contains
        assertTrue(methodSummaryStorage.contains(ms1))
        assertTrue(methodSummaryStorage.contains(ms2))
        assertTrue(methodSummaryStorage.contains(ms3))
        assertTrue(methodSummaryStorage.contains(ms4))
        assertEquals(3, methodSummaryStorage.size)

        // contains visited after dump
        methodSummaryStorage.dump()
        assertTrue(methodSummaryStorage.contains(ms1))
        assertTrue(methodSummaryStorage.contains(ms2))
        assertTrue(methodSummaryStorage.contains(ms3))
        assertTrue(methodSummaryStorage.contains(ms4))
        assertEquals(0, methodSummaryStorage.size)

        // not contains after clear
        methodSummaryStorage.clear()
        assertFalse(methodSummaryStorage.contains(ms1))
        assertFalse(methodSummaryStorage.contains(ms2))
        assertFalse(methodSummaryStorage.contains(ms3))
        assertFalse(methodSummaryStorage.contains(ms4))

        file.delete()
    }
}
