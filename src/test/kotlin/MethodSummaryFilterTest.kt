
import org.junit.Test
import analysis.config.Language
import analysis.granularity.method.MethodSummary
import analysis.granularity.method.filter.MethodSummaryFilter
import analysis.granularity.method.filter.MethodSummaryFilterConfig
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MethodSummaryFilterTest {

    private companion object {
        fun createMethodSummary(
            name: String,
            lang: Language,
            body: String? = null,
            startLine: Int = 0,
            endLine: Int = 0
        ) = MethodSummary(
            name = name,
            fullName = "",
            splitName = "",
            filePath = "",
            language = lang,
            body = body,
            firstLineInFile = startLine,
            lastLineInFile = endLine
        )

        fun initFilterConfig(
            minBodyLength: Int = 0,
            prefs: List<String> = listOf(),
            names: List<String> = listOf(),
            annos: List<String> = listOf()
        ): MethodSummaryFilterConfig {
            val filterConfig = MethodSummaryFilterConfig()
            filterConfig.minBodyLinesLength = minBodyLength
            filterConfig.excludeWithExactName.addAll(names)
            filterConfig.excludeWithNamePrefix.addAll(prefs)
            filterConfig.javaExcludeWithAnnotation.addAll(annos)
            return filterConfig
        }
    }

    @Test
    fun minBodyLenTest() {
        val filterConfig = initFilterConfig(minBodyLength = 10)
        val methodFilter = MethodSummaryFilter(filterConfig)
        val ms1 = createMethodSummary("", Language.JAVA, "", 0, 5)
        val ms2 = createMethodSummary("", Language.JAVA, "", 0, 10)
        val ms3 = createMethodSummary("", Language.JAVA, "", 0, 150)
        assertFalse(methodFilter.isSummaryGood(ms1))
        assertTrue(methodFilter.isSummaryGood(ms2))
        assertTrue(methodFilter.isSummaryGood(ms3))
    }

    @Test
    fun exactNameTest() {
        val filterConfig = initFilterConfig(names = listOf("exactName1", "exactName2"))
        val methodFilter = MethodSummaryFilter(filterConfig)
        val ms1 = createMethodSummary("exactName1", Language.JAVA, "")
        val ms2 = createMethodSummary("exactName2", Language.JAVA, "")
        val ms3 = createMethodSummary("exactName3", Language.JAVA, "")
        assertFalse(methodFilter.isSummaryGood(ms1))
        assertFalse(methodFilter.isSummaryGood(ms2))
        assertTrue(methodFilter.isSummaryGood(ms3))
    }

    @Test
    fun namePrefixTest() {
        val filterConfig = initFilterConfig(prefs = listOf("exact", "abra"))
        val methodFilter = MethodSummaryFilter(filterConfig)
        val ms1 = createMethodSummary("exactName1", Language.JAVA, "")
        val ms2 = createMethodSummary("exactName2", Language.JAVA, "")
        val ms3 = createMethodSummary("abra", Language.JAVA, "")
        val ms4 = createMethodSummary("goodName", Language.JAVA, "")
        assertFalse(methodFilter.isSummaryGood(ms1))
        assertFalse(methodFilter.isSummaryGood(ms2))
        assertFalse(methodFilter.isSummaryGood(ms3))
        assertTrue(methodFilter.isSummaryGood(ms4))
    }

    @Test
    fun javaAnnoTest() {
        val filterConfig = initFilterConfig(annos = listOf("@Override"))
        val methodFilter = MethodSummaryFilter(filterConfig)
        val ms1 = createMethodSummary("", Language.JAVA, "@Override void main() { };")
        val ms2 = createMethodSummary("", Language.JAVA, "     @Override void main() { };")
        val ms3 = createMethodSummary("", Language.JAVA, "public static void main(){ @Override something }")
        val ms4 = createMethodSummary("", Language.JAVA, "no body")
        assertFalse(methodFilter.isSummaryGood(ms1))
        assertFalse(methodFilter.isSummaryGood(ms2))
        assertTrue(methodFilter.isSummaryGood(ms3))
        assertTrue(methodFilter.isSummaryGood(ms4))
    }

    @Test
    fun combinedTest() {
        val filterConfig = initFilterConfig(
            minBodyLength = 10,
            names = listOf("exactName"),
            prefs = listOf("abra"),
            annos = listOf("@Override")
        )
        val methodFilter = MethodSummaryFilter(filterConfig)
        val badName = createMethodSummary("exactName", Language.JAVA, "", startLine = 0, endLine = 50)
        val badPref = createMethodSummary("abraName", Language.JAVA, "", startLine = 0, endLine = 50)
        val badLength = createMethodSummary("exactNameGood", Language.JAVA, "", startLine = 0, endLine = 5)
        val badOverride = createMethodSummary(
            "exactNameGood", Language.JAVA, "@Override", startLine = 0, endLine = 50
        )
        val goodAll = createMethodSummary("exactNameGood", Language.JAVA, "", startLine = 0, endLine = 50)
        val badAll = createMethodSummary("exactName", Language.JAVA, "@Override")
        assertFalse(methodFilter.isSummaryGood(badName))
        assertFalse(methodFilter.isSummaryGood(badPref))
        assertFalse(methodFilter.isSummaryGood(badLength))
        assertFalse(methodFilter.isSummaryGood(badOverride))
        assertFalse(methodFilter.isSummaryGood(badAll))
        assertTrue(methodFilter.isSummaryGood(goodAll))
    }
}
