package app.parivaar.panchang.engine.panchang

import app.parivaar.panchang.engine.astro.Body
import app.parivaar.panchang.engine.astro.MaxError
import app.parivaar.panchang.engine.astro.RiseSet
import app.parivaar.panchang.engine.panchang.M03Golden.a
import app.parivaar.panchang.engine.panchang.M03Golden.d
import app.parivaar.panchang.engine.panchang.M03Golden.i
import app.parivaar.panchang.engine.panchang.M03Golden.l
import app.parivaar.panchang.engine.panchang.M03Golden.o
import app.parivaar.panchang.engine.panchang.M03Golden.s
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import org.junit.jupiter.api.Tag
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * G-M03 limbs: both backends x 8,300 city-days against `panchang_limbs.json` (docs/specs/M03.md section 4):
 * boundary index sequences and times, day flags, unavailable days, sun events, fallback residual, atSunrise,
 * kshaya and adhika. Fragile (kind, instant) pairs are exempt from atSunrise and from kshaya/adhika only.
 */
@Tag("golden")
class PanchangLimbsGoldenTest {
    private val kinds = listOf(
        LimbKind.TITHI, LimbKind.KARANA, LimbKind.NAKSHATRA, LimbKind.YOGA, LimbKind.MOON_RASHI,
    )

    private class Pass(val limits: M03Golden.Limits) {
        val calc = PanchangCalculator(limits.eph(), M03Golden.tables)
        val rs = RiseSet(limits.eph())
        val failures = mutableListOf<String>()
        val dt = LimbKind.entries.associateWith { MaxError() }
        val dts = LimbKind.entries.associateWith { mutableListOf<Double>() }
        val boundaries = LimbKind.entries.associateWith { 0 }.toMutableMap()
        val sun = MaxError()
        val residual = MaxError()
        var days = 0
        var unavailable = 0
        var fallbackDays = 0
        var atSunriseChecked = 0
        var atSunriseFragile = 0
        var kshayaEngine = 0
        var adhikaEngine = 0
    }

    // ------------------------------------------------------------------------------------------ boundaries
    private fun checkBoundaries(p: Pass, c: M03Golden.City) {
        val bnd = c.row.getValue("boundaries").o()
        assertEquals(kinds.map { it.name }.toSet(), bnd.keys, "boundary kinds of ${c.name}")
        val dayRows = c.row.getValue("days").a().map { it.a() }.filter { it[1] !is JsonNull }
        val segs = dayRows.map { (it[1].l() - PAD_MS) to (it[3].l() + PAD_MS) }.distinct().sortedBy { it.first }
        val merged = mutableListOf<Pair<Long, Long>>()
        for (sg in segs) {
            val last = merged.lastOrNull()
            if (last != null && sg.first <= last.second) {
                merged[merged.size - 1] = last.first to maxOf(last.second, sg.second)
            } else {
                merged += sg
            }
        }
        for (kind in kinds) {
            val fx = bnd.getValue(kind.name).a().map { it.a() }
            p.boundaries[kind] = p.boundaries.getValue(kind) + fx.size
            for ((from, to) in merged) {
                val want = fx.filter { it[1].l() in (from + 1) until to }
                val m = ((p.limits.boundaryCapS + MARGIN_S) * MS).toLong()
                val spans = p.calc.limbs.spans(
                    kind, Instant.fromEpochMilliseconds(from - m), Instant.fromEpochMilliseconds(to + m),
                )
                compare(p, kind, "${c.name} [$from,$to)", want, spans.drop(1), from + m to to - m)
            }
        }
    }

    /**
     * Pairs every fixture boundary in (from, to) with the nearest engine boundary (same index, |dt| gated) and
     * fails any engine boundary inside [inner] that has no fixture partner: the same index sequence, with no
     * false failure when a boundary lies within the tolerance of a segment edge.
     */
    private fun compare(
        p: Pass,
        kind: LimbKind,
        at: String,
        want: List<JsonElement>,
        got: List<LimbSpan>,
        inner: Pair<Long, Long>,
    ) {
        val paired = mutableSetOf<Int>()
        val starts = got.map { it.start.toEpochMilliseconds() }
        for ((k, w) in want.withIndex()) pairOne(p, kind, "$kind $at #$k", w.a(), got, starts, paired)
        for (j in starts.indices) {
            if (starts[j] > inner.first && starts[j] < inner.second && j !in paired) {
                p.failures += "${p.limits.name} $kind $at: engine boundary ${got[j].index} has no fixture partner"
            }
        }
    }

    private fun pairOne(
        p: Pass,
        kind: LimbKind,
        where: String,
        x: JsonArray,
        got: List<LimbSpan>,
        starts: List<Long>,
        paired: MutableSet<Int>,
    ) {
        val rate = x[2].d()
        val ms = x[1].l()
        val j = starts.indices.minByOrNull { abs(starts[it] - ms) }
        when {
            !(rate > 0.0) -> p.failures += "${p.limits.name} $where fixture rate $rate is not positive"
            j == null || !paired.add(j) -> p.failures += "${p.limits.name} $where has no distinct engine boundary"
            else -> {
                if (got[j].index != x[0].i()) {
                    p.failures += "${p.limits.name} $where index ${got[j].index} expected ${x[0].i()}"
                }
                val e = checkNotNull(p.limits.positionBudgetArcsec[kind])
                val dt = abs(starts[j] - ms).toDouble() / MS
                val limit = minOf(p.limits.boundaryCapS, 1.0 + e / rate)
                p.dt.getValue(kind).add(dt, where)
                p.dts.getValue(kind) += dt
                if (!(dt <= limit)) p.failures += "${p.limits.name} $where |dt| $dt s > $limit s"
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ days
    private fun checkDay(p: Pass, c: M03Golden.City, row: JsonArray) {
        p.days++
        val date = LocalDate.parse(row[0].s())
        val flags = row[5].s()
        val at = "${c.name} ${row[0].s()}"
        if ('U' in flags) {
            p.unavailable++
            val code = runCatching { p.calc.day(date, c.offsetSeconds, c.place) }
                .exceptionOrNull().let { (it as? PanchangUnavailableException)?.code }
            if (code != PanchangError.NO_SUN_DAY) p.failures += "${p.limits.name} $at expected NO_SUN_DAY, got $code"
            return
        }
        val day = p.calc.day(date, c.offsetSeconds, c.place)
        val fallback = DayFlag.SUN_FALLBACK in day.flags
        val extra = DayFlag.EXTRA_SUNRISE in day.flags
        if (fallback != ('F' in flags) || extra != ('X' in flags)) {
            p.failures += "${p.limits.name} $at flags ${day.flags} vs fixture '$flags'"
        }
        if ('F' !in flags && 'S' !in flags) sunEvents(p, day, row, at)
        if (row[4] !is JsonNull) fallbackResidual(p, c, day, row[4].l(), at)
        compareAtSunrise(p, day, row, at)
        compareKshayaAdhika(p, day, row, flags, at)
    }

    private fun sunEvents(p: Pass, day: PanchangDay, row: JsonArray, at: String) {
        val engine = listOf(day.sunrise, day.sunset, day.nextSunrise)
        for (k in 0..2) {
            val dt = abs((engine[k].toEpochMilliseconds() - row[1 + k].l()).toDouble()) / MS
            p.sun.add(dt, "$at event $k")
            if (!(dt <= p.limits.sunEventS)) p.failures += "${p.limits.name} $at sun event $k |dt| $dt s"
        }
    }

    /** h(sunrise_engine + 6 h) - h(noon): the fallback window is a flat maximum, so altitudes are compared. */
    private fun fallbackResidual(p: Pass, c: M03Golden.City, day: PanchangDay, noonMs: Long, at: String) {
        p.fallbackDays++
        val hNoon = p.rs.altitudeDeg(Body.SUN, c.place, Instant.fromEpochMilliseconds(noonMs))
        val hEngine = p.rs.altitudeDeg(Body.SUN, c.place, day.sunrise + 6.hours)
        val r = abs(hEngine - hNoon) * M03Golden.ARCSEC
        p.residual.add(r, at)
        if (!(r <= p.limits.fallbackResidualArcsec)) p.failures += "${p.limits.name} $at fallback residual $r arcsec"
    }

    private fun compareAtSunrise(p: Pass, day: PanchangDay, row: JsonArray, at: String) {
        val fragile = row[9].a().map { it.a() }.filter { it[1].i() == 0 }.map { it[0].s() }.toSet()
        val want = row[6].a()
        assertEquals(kinds.size, want.size, "atSunrise width $at")
        for ((k, kind) in kinds.withIndex()) {
            if (kind.name in fragile) {
                p.atSunriseFragile++
                continue
            }
            p.atSunriseChecked++
            val got = day.atSunrise(kind).index
            if (got != want[k].i()) p.failures += "${p.limits.name} $at atSunrise $kind $got expected ${want[k].i()}"
        }
    }

    /** Spans with a start or end within tau of a fragile instant are dropped from both lists (kind, index). */
    private fun compareKshayaAdhika(p: Pass, day: PanchangDay, row: JsonArray, flags: String, at: String) {
        val tau = (if ('F' in flags || 'S' in flags) TAU_BAD_S else TAU_S).seconds
        val dropped = mutableSetOf<Pair<String, Int>>()
        for (f in row[9].a().map { it.a() }) {
            val kind = LimbKind.valueOf(f[0].s())
            val instant = when (f[1].i()) {
                0 -> day.sunrise
                1 -> day.nextSunrise
                else -> error("unknown fragile instant selector")
            }
            for (sp in day.limbs.getValue(kind)) {
                if (abs((sp.start - instant).inWholeMilliseconds) <= tau.inWholeMilliseconds ||
                    abs((sp.end - instant).inWholeMilliseconds) <= tau.inWholeMilliseconds
                ) {
                    dropped += kind.name to sp.index
                }
            }
        }
        for ((label, fixture, engineFlag) in listOf(
            Triple("kshaya", row[7], { sp: LimbSpan -> sp.kshaya }),
            Triple("adhika", row[8], { sp: LimbSpan -> sp.adhika }),
        )) {
            val want = fixture.a().map { it.a()[0].s() to it.a()[1].i() }
                .filter { it !in dropped }.sortedBy { it.toString() }
            val got = kinds.flatMap { k -> day.limbs.getValue(k).filter(engineFlag).map { k.name to it.index } }
                .filter { it !in dropped }.sortedBy { it.toString() }
            if (label == "kshaya") p.kshayaEngine += got.size else p.adhikaEngine += got.size
            if (want != got) p.failures += "${p.limits.name} $at $label expected $want got $got"
        }
    }

    // ------------------------------------------------------------------------------------------------- run
    private fun run(limits: M03Golden.Limits) {
        val root = M03Golden.fixture("panchang_limbs.json")
        val meta = M03Golden.meta(root)
        assertEquals(M03Golden.CITY_DAYS, meta.getValue("cityDays").i(), "meta.cityDays")
        val totals = meta.getValue("boundaryTotals").o()
        for ((kind, n) in M03Golden.BOUNDARY_TOTALS) assertEquals(n, totals.getValue(kind.name).i(), "meta total $kind")
        val cities = M03Golden.cities(root, "cityDays")
        val p = Pass(limits)
        var rows = 0
        for (c in cities) {
            assertEquals(M03Golden.DAYS_PER_CITY, c.row.getValue("days").a().size, "days of ${c.name}")
            checkBoundaries(p, c)
            for (r in c.row.getValue("days").a()) {
                checkDay(p, c, r.a())
                rows++
            }
        }
        assertEquals(M03Golden.CITY_DAYS, rows, "city-days iterated")
        assertEquals(M03Golden.CITY_DAYS, p.days, "city-days checked")
        for ((kind, n) in M03Golden.BOUNDARY_TOTALS) {
            assertEquals(n, p.boundaries.getValue(kind), "boundaries read $kind")
        }
        val medians = kinds.associateWith { M03Golden.median(p.dts.getValue(it)) }
        val lines = kinds.map { k ->
            "$k: boundaries=${p.boundaries[k]} maxDt_s=${p.dt.getValue(k).value} (${p.dt.getValue(k).where}) " +
                "median_s=${medians[k]}"
        } + listOf(
            "sun events (non F/S days) max_s=${p.sun.value} (${p.sun.where}); limit ${limits.sunEventS} s",
            "fallback residual days=${p.fallbackDays} max_arcsec=${p.residual.value} " +
                "limit ${limits.fallbackResidualArcsec}",
            "unavailable days=${p.unavailable}; atSunrise compared=${p.atSunriseChecked} " +
                "exempt(fragile)=${p.atSunriseFragile}",
            "fragilePairs(meta)=${meta.getValue("fragilePairs")}",
            "kshaya fixture=${meta.getValue("kshaya")} engine compared=${p.kshayaEngine}",
            "adhika fixture=${meta.getValue("adhika")} engine compared=${p.adhikaEngine}",
            "limits: boundary cap ${limits.boundaryCapS} s, min(cap, 1 s + e/r), e=${limits.positionBudgetArcsec}, " +
                "median cap ${limits.medianCapS}",
        )
        M03Golden.report("M03 limbs ${limits.name} (${M03Golden.CITY_DAYS} city-days)", lines)
        limits.medianCapS?.let { cap ->
            for (k in kinds) {
                if (!(medians.getValue(k) <= cap)) p.failures += "${limits.name} $k median ${medians[k]} s > $cap s"
            }
        }
        M03Golden.assertNoFailures("M03 limbs ${limits.name}", p.failures)
    }

    @Test
    fun precisionBackendMatchesOracle() = run(M03Golden.PRECISION)

    @Test
    fun baseBackendMatchesOracle() = run(M03Golden.BASE)

    private companion object {
        const val MS = 1000.0
        const val MARGIN_S = 10.0
        const val PAD_MS = 3 * 86_400_000L
        const val TAU_S = 150
        const val TAU_BAD_S = 600
    }
}
