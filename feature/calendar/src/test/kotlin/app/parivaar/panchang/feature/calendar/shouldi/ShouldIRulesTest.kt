package app.parivaar.panchang.feature.calendar.shouldi

import app.parivaar.panchang.engine.jyotish.RuleStatus
import app.parivaar.panchang.engine.panchang.WindowKind
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ShouldIRulesTest {
    private val json = File(System.getProperty("pp.root.dir"), "rules/should_i.json").readText()
    private val rules = ShouldIRules.parse(json)

    @Test fun `thirty unique verify intents with sources`() {
        assertEquals(30, rules.size)
        assertEquals(30, rules.map { it.id }.toSet().size)
        assertTrue(rules.all { it.status == RuleStatus.VERIFY && it.sourceId.isNotBlank() })
    }

    @Test fun `v1 rules use only the general good and avoid sets`() {
        for (r in rules) {
            assertEquals(setOf("AMRIT", "SHUBH", "LABH"), r.prefer)
            assertTrue(r.tithiAvoid.isEmpty() && r.vaarPrefer.isEmpty() && r.nakshatraPrefer.isEmpty())
            assertTrue(WindowKind.RAHU_KAAL in r.avoid && WindowKind.BHADRA in r.avoid && r.avoid.size == 6)
        }
    }

    private fun one(extra: String) =
        """{"version":1,"intents":[{"id":"a","status":"VERIFY","sourceId":"s"$extra}]}"""

    @Test fun `minimal intent parses`() {
        assertEquals(1, ShouldIRules.parse(one("")).size)
    }

    @Test fun `unknown key rejected`() {
        assertFailsWith<IllegalArgumentException> { ShouldIRules.parse(one(""","prose":"x"""")) }
        assertFailsWith<IllegalArgumentException> { ShouldIRules.parse("""{"version":1,"intents":[],"x":1}""") }
    }

    @Test fun `missing status or source rejected`() {
        assertFailsWith<IllegalArgumentException> { ShouldIRules.parse("""{"intents":[{"id":"a","sourceId":"s"}]}""") }
        val noSource = """{"intents":[{"id":"a","status":"VERIFY"}]}"""
        assertFailsWith<IllegalArgumentException> { ShouldIRules.parse(noSource) }
    }

    @Test fun `bad values rejected`() {
        assertFailsWith<IllegalArgumentException> { ShouldIRules.parse(one(""","avoid":["NOPE"]""")) }
        assertFailsWith<IllegalArgumentException> { ShouldIRules.parse(one(""","tithiAvoid":[31]""")) }
        val badStatus = """{"intents":[{"id":"a","status":"X","sourceId":"s"}]}"""
        assertFailsWith<IllegalArgumentException> { ShouldIRules.parse(badStatus) }
        assertFailsWith<IllegalArgumentException> { ShouldIRules.parse("not json") }
    }
}
