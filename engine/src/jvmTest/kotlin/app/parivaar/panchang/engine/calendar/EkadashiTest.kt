package app.parivaar.panchang.engine.calendar

import app.parivaar.panchang.engine.Place
import app.parivaar.panchang.engine.astro.GoldenLoader
import app.parivaar.panchang.engine.jyotish.RuleStatus
import app.parivaar.panchang.engine.panchang.LimbKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

/** docs/specs/M04.md section 3.7 on real data (Precision): Delhi and New York, 2024-2030. */
class EkadashiTest {
    private val engine = FestivalEngine(GoldenLoader.openDe440())
    private val cal = LunarCalendar(GoldenLoader.openDe440())

    private fun def(t: Tradition): FestivalDef {
        val id = "ekadashi_${t.name.lowercase()}"
        val rule = FestivalRule.Ekadashi(t, null, MonthSystem.AMANTA, null)
        return FestivalDef(id, "term:fest_$id", setOf("all"), rule, AdhikaPolicy.BOTH, emptyList(), RuleStatus.VERIFY,
            "src.test")
    }

    private fun run(place: Place, offset: Int): List<FestivalOccurrence> {
        val defs = listOf(def(Tradition.SMARTA), def(Tradition.VAISHNAVA))
        return engine.occurrences(defs, LocalDate(2024, 1, 1), LocalDate(2030, 12, 31), place) { offset }
    }

    @Test
    fun fastDaysAndParanaAreConsistent() {
        val occ = run(Place(28.6139, 77.2090), 19_800)
        val smarta = occ.filter { it.id == "ekadashi_smarta" }
        assertTrue(smarta.size in 168..176)
        for (o in occ) {
            val window = checkNotNull(o.kalaWindow)
            val span = cal.limbs.spanAt(LimbKind.TITHI, window.end)
            if (o.id == "ekadashi_smarta" && OccFlag.KSHAYA_TITHI !in o.flags) assertEquals(o.tithi, span.index)
            val parana = o.parana
            if (parana == null) {
                assertTrue(OccFlag.PARANA_UNDEFINED in o.flags)
            } else {
                assertTrue(parana.start > window.end)
            }
            assertEquals(if (o.id == "ekadashi_smarta") Kala.SUNRISE else Kala.ARUNODAYA, o.kala)
        }
        assertTrue(occ.any { OccFlag.KSHAYA_TITHI in it.flags } && occ.any { OccFlag.VRIDDHI_TITHI in it.flags })
    }

    @Test
    fun newYorkIsComputedLocally() {
        val delhi = run(Place(28.6139, 77.2090), 19_800).filter { it.id == "ekadashi_smarta" }.map { it.date }.toSet()
        val ny = run(Place(40.7128, -74.0060), -18_000).filter { it.id == "ekadashi_smarta" }.map { it.date }
        assertTrue(ny.any { it !in delhi }, "a New York date differs from Delhi")
    }
}
