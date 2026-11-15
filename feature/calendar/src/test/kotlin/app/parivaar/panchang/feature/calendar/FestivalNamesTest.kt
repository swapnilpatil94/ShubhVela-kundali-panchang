package app.parivaar.panchang.feature.calendar

import app.parivaar.panchang.feature.calendar.guide.FestivalNames
import app.parivaar.panchang.feature.calendar.shouldi.ShouldILabels
import app.parivaar.panchang.feature.calendar.shouldi.ShouldIRules
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FestivalNamesTest {
    private val root = File(System.getProperty("pp.root.dir"))
    private val res = File(root, "feature/calendar/src/main/res/values")

    private fun strings(file: String): Map<String, String> =
        Regex("""<string name="([^"]+)"[^>]*>([^<]*)</string>""").findAll(File(res, file).readText())
            .associate { it.groupValues[1] to it.groupValues[2] }

    private fun ruleIds(file: String): List<String> =
        Regex(""""id"\s*:\s*"([^"]+)"""").findAll(File(root, "rules/festivals/$file").readText())
            .map { it.groupValues[1] }.toList()

    @Test fun `every shipped festival id has a non-empty name`() {
        val names = strings("strings_festivals.xml")
        val ids = ruleIds("pan_india.json") + ruleIds("recurring.json")
        assertEquals(37, ids.size)
        for (id in ids) assertTrue(names["fest_name_$id"].orEmpty().isNotBlank(), id)
        assertEquals(ids.toSet(), FestivalNames.ids)
    }

    @Test fun `no fest_name string without a rule`() {
        val ids = (ruleIds("pan_india.json") + ruleIds("recurring.json")).toSet()
        val extra = strings("strings_festivals.xml").keys.filter { it.startsWith("fest_name_") }
            .map { it.removePrefix("fest_name_") }.toSet() - ids
        assertTrue(extra.isEmpty(), extra.toString())
    }

    @Test fun `every intent has a label and every why id has text`() {
        val s = strings("strings_should_i.xml")
        val rules = ShouldIRules.parse(File(root, "rules/should_i.json").readText())
        for (r in rules) {
            assertTrue(s["should_i_${r.id}"].orEmpty().isNotBlank(), r.id)
            assertTrue(ShouldILabels.label(r.id) != null, r.id)
        }
        assertEquals(rules.map { it.id }.toSet(), ShouldILabels.intentIds)
        val whys = listOf("why_tithi", "why_vaar", "why_nakshatra", "why_abhijit", "why_choghadiya_amrit")
        for (id in whys + "why_avoid_rahu_kaal") {
            assertTrue(ShouldILabels.why(id) != null, id)
        }
    }

    @Test fun `footer wording is exact and no fear words ship`() {
        val s = strings("strings_should_i.xml") + strings("strings_festivals.xml")
        assertEquals("Traditional interpretation, not a scientific prediction.", s["should_i_footer"])
        assertEquals(s["should_i_footer"], s["grahan_footer"])
        val bad = listOf("bad", "danger", "doom", "curse", "remedy", "avoid at all costs")
        for ((k, v) in s) assertTrue(bad.none { v.lowercase().contains(it) }, k)
    }
}
