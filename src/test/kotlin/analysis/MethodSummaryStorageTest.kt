package analysis

import analysis.config.IdentityConfig
import analysis.config.enums.IdentityParameters
import analysis.config.enums.SupportedLanguage
import analysis.granularity.method.MethodIdentity
import analysis.granularity.method.MethodSummary
import analysis.granularity.method.MethodSummaryStorage
import org.junit.Test
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class MethodSummaryStorageTest {

    private val testFolder = File(System.getProperty("user.dir")).resolve(".method_summary_storage_test_tmp")

    @BeforeTest
    fun createFolder() {
        testFolder.mkdirs()
    }

    @AfterTest
    fun removeFolder() {
        testFolder.deleteRecursively()
    }

    private fun genName(n: Int) = "method$n"
    private fun genPath(n: Int) = "path$n"
    private fun genFullName(n: Int) = "full_name.method$n"
    private fun genArgsTypes(n: Int) = (0 until n).map { "ARG$it" }.toList()
    private fun genReturnType(n: Int) = "T$n"

    private fun genIdentityConfig(params: List<IdentityParameters>): IdentityConfig = IdentityConfig(params)

    private fun genIdentityConfigWithIds(paramsIds: List<Int>): IdentityConfig {
        val params = IdentityParameters.values()
        return genIdentityConfig(paramsIds.map { n -> params[n] }.toList())
    }

    private fun genMethodSummary(pathId: Int, nameId: Int, argsId: Int, rtId: Int, config: IdentityConfig): MethodSummary {
        val methodSummary = MethodSummary(
            filePath = "", name = "", fullName = "", splitName = "", language = SupportedLanguage.JAVA
        )
        for (param in config.parameters) {
            when (param) {
                IdentityParameters.NAME -> methodSummary.name = genName(nameId)
                IdentityParameters.FULL_NAME -> methodSummary.fullName = genFullName(nameId)
                IdentityParameters.RETURN_TYPE -> methodSummary.returnType = genReturnType(rtId)
                IdentityParameters.ARGS_TYPES -> methodSummary.argsTypes = genArgsTypes(argsId)
                IdentityParameters.FILE -> methodSummary.filePath = genPath(pathId)
            }
        }
        return methodSummary
    }

    private fun getStorage(
        config: IdentityConfig,
        isAstDotFormat: Boolean = false,
        isCode2SeqDump: Boolean = false,
        dumpThreshold: Int = 200
    ) = MethodSummaryStorage(
        testFolder.absolutePath, config, null, isAstDotFormat, isCode2SeqDump, dumpThreshold
    )

    @Test
    fun basicsWithIdentity() {
        val params = listOf(IdentityParameters.FULL_NAME, IdentityParameters.ARGS_TYPES)
        val config = IdentityConfig(params)
        val mss = getStorage(config)
        val ms1 = genMethodSummary(0, 1, 3, 0, config)
        val ms2 = genMethodSummary(0, 1, 3, 0, config)
        val ms3 = genMethodSummary(0, 1, 4, 0, config)
        val ms4 = genMethodSummary(0, 2, 3, 0, config)
        assertTrue(mss.add(ms1))
        assertTrue(mss.contains(ms1))
        assertEquals(1, mss.size)

        assertFalse(mss.add(ms2))
        assertEquals(1, mss.size)
        assertTrue(mss.contains(ms2))
        assertTrue(
            mss.contains(
                MethodIdentity(
                    name = ms2.name,
                    fullName = ms2.fullName,
                    filePath = ms2.filePath,
                    argsTypes = ms2.argsTypes,
                    returnType = ms2.returnType,
                    language = SupportedLanguage.JAVA
                )
            )
        )

        assertFalse(mss.contains(ms3))
        assertTrue(mss.add(ms3))
        assertEquals(2, mss.size)

        assertFalse(mss.contains(ms4))
        assertTrue(mss.add(ms4))
        assertEquals(3, mss.size)
        mss.clear()
        assertEquals(0, mss.size)
    }

    @Test
    fun basicsNoIdentity() {
        val config = IdentityConfig(emptyList())
        val mss = getStorage(config)
        val ms1 = genMethodSummary(0, 1, 3, 0, config)
        val ms2 = genMethodSummary(0, 1, 3, 0, config)
        val ms3 = genMethodSummary(0, 1, 4, 0, config)
        val ms4 = genMethodSummary(0, 2, 3, 0, config)
        assertTrue(mss.add(ms1))
        assertEquals(1, mss.size)
        assertFalse(mss.contains(ms1))

        assertFalse(mss.contains(ms2)) // no contains because no identity to check
        assertTrue(mss.add(ms2))
        assertEquals(2, mss.size)
        assertFalse(mss.contains(ms2)) // no contains because no identity to check

        assertFalse(
            mss.contains(
                MethodIdentity(
                    name = ms2.name,
                    fullName = ms2.fullName,
                    filePath = ms2.filePath,
                    argsTypes = ms2.argsTypes,
                    returnType = ms2.returnType,
                    language = SupportedLanguage.JAVA
                )
            )
        )
        assertFalse(mss.contains(ms3))
        assertTrue(mss.add(ms3))
        assertEquals(3, mss.size)
        assertFalse(mss.contains(ms3))

        assertFalse(mss.contains(ms4))
        assertTrue(mss.add(ms4))
        assertEquals(4, mss.size)
        assertFalse(mss.contains(ms3))

        mss.clear()
        assertEquals(0, mss.size)
    }

    @Test
    fun noIdentityStress() {
        val config = IdentityConfig(emptyList())
        val mss = getStorage(config, dumpThreshold = 10000)
        val ssAdded = mutableListOf<MethodSummary>()
        for (n in 0..20) {
            for (k in 0 until 20) {
                val summary = genMethodSummary(k, k, 5, k, config)
                ssAdded.add(summary)
                assertFalse(mss.contains(summary))
                assertTrue(mss.add(summary))
                assertEquals(ssAdded.size, mss.size)
            }
        }
        mss.clear()
        assertEquals(0, mss.size)
    }

    @Test
    fun containsWithMinimumOneIdentityParameter() {
        val k = IdentityParameters.values().size
        for (i in 0 until k) {
            val paramsIds = (0..i).toList()
            val identityConfig = genIdentityConfigWithIds(paramsIds)
            val mss = getStorage(identityConfig)
            val ssAdded = mutableListOf<MethodSummary>()
            for (n in 1..50) {
                val summary = genMethodSummary(n, n, 5, n, identityConfig)
                ssAdded.add(summary)
                assertFalse(mss.contains(summary))
                assertTrue(mss.add(summary))
                assertEquals(ssAdded.size, mss.size)
            }
            ssAdded.forEach { summary ->
                assertTrue(mss.contains(summary))
                assertFalse(mss.add(summary))
            }
            val ssNotAdded = mutableListOf<MethodSummary>()
            for (n in 51..100) {
                val summary = genMethodSummary(n, n, 5, n, identityConfig)
                ssNotAdded.add(summary)
                assertFalse(mss.contains(summary))
                assertEquals(ssAdded.size, mss.size)
            }
            ssNotAdded.forEach { summary ->
                assertFalse(mss.contains(summary))
                assertTrue(mss.add(summary))
                assertTrue(mss.contains(summary))
            }
        }
    }

    @Test
    fun containsAfterDump() {
        val dumpThreshold = 20
        val params = listOf(IdentityParameters.FULL_NAME, IdentityParameters.ARGS_TYPES)
        val config = IdentityConfig(params)
        val mss = getStorage(config, dumpThreshold = dumpThreshold)
        for (k in 1..dumpThreshold) {
            val summary = genMethodSummary(k, k, 5, k, config)
            assertFalse(mss.contains(summary))
            assertTrue(mss.add(summary))
        }
        for (n in 0..10) {
            val ssAdded = mutableListOf<MethodSummary>()
            for (k in 1..dumpThreshold) {
                val summary = genMethodSummary(k, k, 5, k, config)
                ssAdded.add(summary)
                assertTrue(mss.contains(summary)) // because visited not dumped
                assertFalse(mss.add(summary))
            }
            assertEquals(0, mss.size)
            assertEquals(dumpThreshold, ssAdded.size)
            ssAdded.forEach {
                assertTrue(mss.contains(it)) // because visited not dumped
            }
        }
    }

    @Test
    fun containsAfterClear() {
        val dumpThreshold = 20
        val params = listOf(IdentityParameters.FULL_NAME, IdentityParameters.ARGS_TYPES)
        val config = IdentityConfig(params)
        val mss = getStorage(config, dumpThreshold = dumpThreshold)
        for (k in 1..dumpThreshold) {
            val summary = genMethodSummary(k, k, 5, k, config)
            assertFalse(mss.contains(summary))
            assertTrue(mss.add(summary))
        }
        for (k in 1..dumpThreshold) {
            val summary = genMethodSummary(k, k, 5, k, config)
            assertTrue(mss.contains(summary)) // because visited not dumped
            assertFalse(mss.add(summary))
        }
        mss.clear()
        assertEquals(0, mss.size)
        for (k in 1..dumpThreshold) {
            val summary = genMethodSummary(k, k, 5, k, config)
            assertFalse(mss.contains(summary)) // because visited dumped
            assertTrue(mss.add(summary))
        }
    }
}
