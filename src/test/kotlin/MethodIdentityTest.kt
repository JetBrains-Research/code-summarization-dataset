import org.junit.Test
import reposanalyzer.methods.MethodIdentity
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
    private val path2 = "path2"
    private val argsTypes1 = listOf("int", "String")
    private val argsTypes2 = listOf("String", "int")

    @Test
    fun equalsTest() {
        val id1 = MethodIdentity(path1, fName1)
        val id2 = MethodIdentity(path1, fName1)
        assertEquals(id1, id2)

        val id3 = MethodIdentity(path1, fName1, argsTypes1)
        val id4 = MethodIdentity(path1, fName1, argsTypes1)
        assertEquals(id3, id4)

        val id5 = MethodIdentity(path1, fName1, argsTypes1, "T1")
        val id6 = MethodIdentity(path1, fName1, argsTypes1, "T1")
        assertEquals(id5, id6)
    }

    @Test
    fun notEqualsTest() {
        val id1 = MethodIdentity(path1, fName1)
        val id2 = MethodIdentity(path2, fName1)
        assertNotEquals(id1, id2)

        val id3 = MethodIdentity(path1, fName1)
        val id4 = MethodIdentity(path1, fName2)
        assertNotEquals(id3, id4)

        val id5 = MethodIdentity(path1, fName1, argsTypes1)
        val id6 = MethodIdentity(path1, fName1, argsTypes2)
        assertNotEquals(id5, id6)

        val id7 = MethodIdentity(path1, fName1, argsTypes1, "T1")
        val id8 = MethodIdentity(path1, fName1, argsTypes1, "T2")
        assertNotEquals(id7, id8)
    }

    @Test
    fun listContains() {
        val id1 = MethodIdentity(path1, fName1)
        val id2 = MethodIdentity(path1, fName1)
        val id3 = MethodIdentity(path1, fName1, argsTypes1)
        val id4 = MethodIdentity(path2, fName1, argsTypes1)
        val list = mutableListOf(id1)

        assertTrue(list.contains(id1))
        assertTrue(list.contains(id2))

        assertFalse(list.contains(id3))
        assertFalse(list.contains(id4))
    }
}
