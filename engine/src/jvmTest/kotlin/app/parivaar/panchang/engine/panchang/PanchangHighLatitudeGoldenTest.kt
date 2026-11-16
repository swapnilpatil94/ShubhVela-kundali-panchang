package app.parivaar.panchang.engine.panchang

import app.parivaar.panchang.engine.Place
import app.parivaar.panchang.engine.astro.Body
import app.parivaar.panchang.engine.astro.RiseSet
import app.parivaar.panchang.engine.panchang.M03Golden.a
import app.parivaar.panchang.engine.panchang.M03Golden.d
import app.parivaar.panchang.engine.panchang.M03Golden.i
import app.parivaar.panchang.engine.panchang.M03Golden.l
import app.parivaar.panchang.engine.panchang.M03Golden.o
import app.parivaar.panchang.engine.panchang.M03Golden.s
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonArray
import org.junit.jupiter.api.Tag
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Fable review M1: sun days at Reykjavik, Fairbanks, Oulu and Tromso at their real civil offsets
 * (`panchang_highlat.json`, tools/oracle/gen_panchang.py). Sunsets that cross local midnight must stay real and
 * the convention may replace only the events that have no crossing. Gates of docs/specs/M03.md section 4: an
 * observed event within `sunEventS` (skipped on fragile 'S' days, as in [PanchangLimbsGoldenTest]); a convention
 * event by the altitude residual at its culmination (`fallbackResidualArcsec`); flags and NO_SUN_DAY exactly.
 */
@Tag("golden")
class PanchangHighLatitudeGoldenTest {
    private val root = M03Golden.fixture("panchang_highlat.json")

    @Test
    fun precision() = run(M03Golden.PRECISION)

    @Test
    fun base() = run(M03Golden.BASE)

    /** Counters and failures of one backend pass. */
    private class Pass(val limits: M03Golden.Limits) {
        val calc = PanchangCalculator(limits.eph(), M03Golden.tables)
        val rs = RiseSet(limits.eph())
        val failures = mutableListOf<String>()
        var rows = 0
        var timed = 0
        var conventions = 0
        var worstReal = 0.0
        var worstResidual = 0.0
    }

    private fun run(limits: M03Golden.Limits) {
        val p = Pass(limits)
        val meta = M03Golden.meta(root)
        val places = root.getValue("places").a()
        val metaPlaces = meta.getValue("places").a()
        assertEquals(5, places.size, "place count")
        for ((k, pe) in places.withIndex()) {
            val m = metaPlaces[k].o()
            val o = pe.o()
            val name = o.getValue("name").s()
            assertEquals(m.getValue("name").s(), name)
            val place = Place(m.getValue("lat").d(), m.getValue("lon").d(), 0.0)
            val days = o.getValue("days").a()
            assertEquals(m.getValue("days").i(), days.size, "$name rows")
            for (re in days) checkRow(p, place, o.getValue("offsetMin").i() * 60, re.a(), "${limits.name} $name")
        }
        assertEquals(meta.getValue("cityDays").i(), p.rows, "meta.cityDays")
        assertTrue(p.timed >= 60 && p.conventions >= 60, "coverage: ${p.timed} real, ${p.conventions} convention")
        M03Golden.report(
            "high latitude ${limits.name}",
            listOf(
                "rows ${p.rows}, real timed ${p.timed} (max ${p.worstReal}s), convention ${p.conventions} " +
                    "(max ${p.worstResidual}\")",
            ),
        )
        M03Golden.assertNoFailures("high latitude ${limits.name}", p.failures)
    }

    private fun checkRow(p: Pass, place: Place, offset: Int, row: JsonArray, label: String) {
        p.rows++
        val at = "$label ${row[0].s()}"
        val flags = row[4].s()
        val result = runCatching { p.calc.day(LocalDate.parse(row[0].s()), offset, place) }
        if ('U' in flags) {
            val code = (result.exceptionOrNull() as? PanchangUnavailableException)?.code
            if (code != PanchangError.NO_SUN_DAY) p.failures += "$at expected NO_SUN_DAY, got $code"
            return
        }
        val day = result.getOrNull()
        if (day == null) {
            p.failures += "$at threw ${result.exceptionOrNull()}"
            return
        }
        if ((DayFlag.SUN_FALLBACK in day.flags) != ('F' in flags) ||
            (DayFlag.EXTRA_SUNRISE in day.flags) != ('X' in flags)
        ) {
            p.failures += "$at flags ${day.flags} vs fixture '$flags'"
        }
        val engine = listOf(day.sunrise, day.sunset, day.nextSunrise)
        for (e in 0..2) {
            val want = Instant.fromEpochMilliseconds(row[1 + e].l())
            when {
                row[5].s()[e] != 'R' -> convention(p, place, e, engine[e], want, at)
                'S' !in flags -> real(p, e, engine[e], want, at)
            }
        }
        if (day.sunrise >= day.sunset || day.sunset >= day.nextSunrise) p.failures += "$at events out of order"
    }

    private fun real(p: Pass, e: Int, engine: Instant, want: Instant, at: String) {
        p.timed++
        val dt = abs((engine - want).inWholeMilliseconds) / MS
        p.worstReal = maxOf(p.worstReal, dt)
        if (!(dt <= p.limits.sunEventS)) p.failures += "$at event $e real |dt| $dt s"
    }

    /** A convention RISE is culmination - 6 h, a SET culmination + 6 h. */
    private fun convention(p: Pass, place: Place, e: Int, engine: Instant, want: Instant, at: String) {
        p.conventions++
        val shift = if (e == 1) (-6).hours else 6.hours
        val r = residualArcsec(p.rs, place, engine + shift, want + shift)
        p.worstResidual = maxOf(p.worstResidual, r)
        if (!(r <= p.limits.fallbackResidualArcsec)) p.failures += "$at event $e convention residual $r"
    }

    /** |h(engine culmination) - h(oracle culmination)|: the culmination is a flat maximum, so altitudes compare. */
    private fun residualArcsec(rs: RiseSet, place: Place, engine: Instant, oracle: Instant): Double =
        abs(rs.altitudeDeg(Body.SUN, place, engine) - rs.altitudeDeg(Body.SUN, place, oracle)) * M03Golden.ARCSEC

    private companion object {
        const val MS = 1000.0
    }
}
