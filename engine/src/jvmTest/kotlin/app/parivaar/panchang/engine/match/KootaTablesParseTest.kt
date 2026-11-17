package app.parivaar.panchang.engine.match

import app.parivaar.panchang.engine.jyotish.RuleFormatException
import app.parivaar.panchang.engine.jyotish.RuleTestSupport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** docs/specs/M12.md, `KootaTablesParseTest`. */
class KootaTablesParseTest {
    private val good = RuleTestSupport.rule("koota.json")

    private fun rejects(json: String, code: String) {
        val e = assertFailsWith<RuleFormatException> { KootaTables.parse(json) }
        assertEquals(code, e.code)
    }

    private fun replaceOnce(old: String, new: String): String {
        check(good.indexOf(old) >= 0 && good.indexOf(old) == good.lastIndexOf(old)) { "fixture edit not unique" }
        return good.replace(old, new)
    }

    @Test
    fun `the shipped file parses and every table is VERIFY`() {
        val t = KootaTables.parse(good)
        assertEquals("VERIFY", t.tableStatus)
        assertEquals(Koota.entries.toSet(), t.status.keys)
        assertEquals(setOf("VERIFY"), t.status.values.toSet())
    }

    @Test
    fun `rejects an asymmetric yoni matrix`() =
        rejects(replaceOnce("[4,2,2,3,2,2,2,1,0,1,3,3,2,1]", "[4,3,2,3,2,2,2,1,0,1,3,3,2,1]"), "NOT_SYMMETRIC")

    @Test
    fun `rejects a missing koota`() =
        rejects(replaceOnce("\"nadi\":{\"status\":\"VERIFY\"}", "\"x\":{}"), "UNKNOWN_KEY")

    @Test
    fun `rejects a missing koota table key`() {
        val json = good.replace(",\n\"nadi\":{\"status\":\"VERIFY\"}", "")
        check(json != good)
        rejects(json, "MISSING_FIELD")
    }

    @Test
    fun `rejects an out-of-range point`() =
        rejects(replaceOnce("\"points\":[[6,6,1]", "\"points\":[[7,6,1]"), "OUT_OF_RANGE")

    @Test
    fun `rejects a negative point and a non-quarter point`() {
        rejects(replaceOnce("\"points\":[[6,6,1]", "\"points\":[[-1,6,1]"), "OUT_OF_RANGE")
        rejects(replaceOnce("\"points\":[[6,6,1]", "\"points\":[[6,6,0.3]"), "NOT_QUARTER")
    }

    @Test
    fun `rejects a bad status`() =
        rejects(replaceOnce("\"gana\":{\"status\":\"VERIFY\"", "\"gana\":{\"status\":\"OK\""), "BAD_VALUE")

    @Test
    fun `rejects an odd symmetric sum`() =
        rejects(replaceOnce("\"points\":[[6,6,1]", "\"points\":[[6,5.75,1]"), "ODD_SYMMETRIC_SUM")

    @Test
    fun `rejects an incomplete relation table`() =
        rejects(replaceOnce("\"neutral\":[\"JUPITER\"],\"enemies\"", "\"neutral\":[],\"enemies\""), "BAD_COUNT")

    @Test
    fun `rejects an inconsistent bhakoot pair`() = rejects(replaceOnce("[2,12]", "[2,11]"), "BAD_VALUE")

    @Test
    fun `rejects a wrong version and bad json`() {
        rejects(replaceOnce("{\"version\":1,", "{\"version\":2,"), "OUT_OF_RANGE")
        rejects("{", "BAD_JSON")
    }
}
