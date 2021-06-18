package analysis

import analysis.config.enums.SupportedLanguage
import analysis.granularity.method.MethodIdentity
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MethodIdentityTest {

    private val mName1 = "method1"
    private val mName2 = "method2"
    private val fName1 = "full_name.$mName1"
    private val fName2 = "full_name.$mName2"
    private val path1 = "path1"
    private val java = SupportedLanguage.JAVA
    private val python = SupportedLanguage.PYTHON
    private val argsTypes1 = listOf("int", "String")
    private val argsTypes2 = listOf("String", "int")

    @Test
    fun equalsTest() {
        val id1 = MethodIdentity(fullName = fName1, language = java)
        val id2 = MethodIdentity(fullName = fName1, language = java)
        assertEquals(id1, id2)

        val id3 = MethodIdentity(fullName = fName1, argsTypes = argsTypes1, language = java)
        val id4 = MethodIdentity(fullName = fName1, argsTypes = argsTypes1, language = java)
        assertEquals(id3, id4)

        val id5 = MethodIdentity(fullName = fName1, argsTypes = argsTypes1, returnType = "T1", language = java)
        val id6 = MethodIdentity(fullName = fName1, argsTypes = argsTypes1, returnType = "T1", language = java)
        assertEquals(id5, id6)

        val id7 = MethodIdentity(
            filePath = path1, name = mName1, argsTypes = argsTypes1, returnType = "T1", language = java
        )
        val id8 = MethodIdentity(
            filePath = path1, name = mName1, argsTypes = argsTypes1, returnType = "T1", language = java
        )
        assertEquals(id7, id8)
    }

    @Test
    fun notEqualsTest() {
        val id1 = MethodIdentity(fullName = fName1, argsTypes = argsTypes1, language = java)
        val id2 = MethodIdentity(fullName = fName1, argsTypes = argsTypes2, language = java)
        assertNotEquals(id1, id2)

        val id3 = MethodIdentity(name = mName1, argsTypes = argsTypes1, returnType = "T1", language = java)
        val id4 = MethodIdentity(name = mName1, argsTypes = argsTypes1, returnType = "T2", language = java)
        assertNotEquals(id3, id4)

        val id5 = MethodIdentity(name = mName1, argsTypes = argsTypes1, language = java)
        val id6 = MethodIdentity(name = mName1, argsTypes = argsTypes2, language = java)
        assertNotEquals(id5, id6)
    }

    @Test
    fun listContains() {
        val id1 = MethodIdentity(fullName = fName1, language = java)
        val id2 = MethodIdentity(fullName = fName2, language = java)
        val id3 = MethodIdentity(fullName = fName1, argsTypes = argsTypes1, language = java)
        val id4 = MethodIdentity(fullName = fName1, argsTypes = argsTypes2, language = java)
        val id5 = MethodIdentity(filePath = path1, fullName = fName1, argsTypes = argsTypes1, language = java)
        val list = mutableListOf(id1, id2, id3)

        assertTrue(list.contains(id1))
        assertTrue(list.contains(id2))
        assertTrue(list.contains(id3))

        assertFalse(list.contains(id4))
        assertFalse(list.contains(id5))
    }

    @Test
    fun noConstructorFieldsNoEffect() {
        val id1 = MethodIdentity(name = mName1, argsTypes = argsTypes1, language = java)
        val id2 = MethodIdentity(name = mName1, argsTypes = argsTypes1, language = java)
        assertEquals(id1, id2)

        id1.id = 42
        id2.id = 17
        assertEquals(id1, id2)

        id1.id = null
        id2.id = null
        id1.isDoc = true
        id2.isDoc = false
        assertEquals(id1, id2)
    }

    @Test
    fun differentLanguages() {
        val id1 = MethodIdentity(name = mName1, argsTypes = argsTypes1, language = java)
        val id2 = MethodIdentity(name = mName1, argsTypes = argsTypes1, language = java)
        val id3 = MethodIdentity(name = mName1, argsTypes = argsTypes1, language = python)
        assertEquals(id1, id2)
        assertNotEquals(id1, id3)
    }
}
