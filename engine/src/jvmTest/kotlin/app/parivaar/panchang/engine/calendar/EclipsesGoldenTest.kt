package app.parivaar.panchang.engine.calendar

import app.parivaar.panchang.engine.calendar.M04Golden.a
import app.parivaar.panchang.engine.calendar.M04Golden.d
import app.parivaar.panchang.engine.calendar.M04Golden.i
import app.parivaar.panchang.engine.calendar.M04Golden.isNull
import app.parivaar.panchang.engine.calendar.M04Golden.l
import app.parivaar.panchang.engine.calendar.M04Golden.o
import app.parivaar.panchang.engine.calendar.M04Golden.s
import app.parivaar.panchang.engine.Place
import kotlinx.serialization.json.JsonArray
import org.junit.jupiter.api.Tag
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * G-M04 eclipses: both backends x 233 global rows and 1,928 local rows of `eclipses.json` (docs/specs/M04.md
 * section 4). The eclipse set (solar flag, kind) equals the fixture unless the row is fragile for the backend;
 * near-miss rows (eclipse 0, kind null) must be "no eclipse found" unless fragile. Greatest and contacts are
 * gated by min(cap, 1 s + e/rate) with the fixture's contact rate, magnitudes by the section 4 delta, visibility
 * endpoints by the horizon limit or, when the endpoint is a contact, by that contact's limit, sutak by the
 * contact limit and exactly against the engine's own contacts.
 *
 * Gate choices the spec leaves open (logged in the summary of the reviewer): the greatest instant of a global
 * eclipse uses min(cap, 1 s + sqrt(2 e / curvature)) with the fixture curvature; the greatest instant of a
 * local solar eclipse uses the cap alone.
 */
@Suppress(
    "CyclomaticComplexMethod", "LongMethod", "NestedBlockDepth", "LoopWithTooManyJumpStatements", "ReturnCount",
    "SwallowedException",
) // a golden comparison is one flat list of gates per fixture row; splitting scatters the limits
@Tag("golden")
class EclipsesGoldenTest {
    private class Tally {
        val failures = M04Golden.Failures()
        val greatestLunar = M04Golden.Stat()
        val greatestSolar = M04Golden.Stat()
        val magnitude = M04Golden.Stat()
        val contactDt = M04Golden.Stat()
        val contactRatio = M04Golden.Stat()
        val maxAt = M04Golden.Stat()
        val horizon = M04Golden.Stat()
        val sutak = M04Golden.Stat()
        var contacts = 0
        var fragileGlobal = 0
        var fragileLocal = 0
        var unmatchedFragile = 0
        var absentSolarRows = 0
        var localCompared = 0
        var localAbsent = 0
        var visCompared = 0
        var visSkipped = 0

        @Synchronized
        fun merge(o: Tally) {
            contacts += o.contacts
            fragileLocal += o.fragileLocal
            absentSolarRows += o.absentSolarRows
            localCompared += o.localCompared
            localAbsent += o.localAbsent
            visCompared += o.visCompared
            visSkipped += o.visSkipped
        }
    }

    @Test
    fun precision() = run(M04Golden.PRECISION)

    @Test
    fun base() = run(M04Golden.BASE)

    private fun fr(row: JsonArray, idx: Int): Set<String> = row[idx].a().map { it.s() }.toSet()

    private fun run(lim: M04Golden.Limits) {
        val root = M04Golden.fixture("eclipses.json")
        val meta = M04Golden.meta(root)
        val counts = meta.getValue("counts").o()
        val grows = root.getValue("global").a().map { it.a() }
        assertEquals(M04Golden.ECLIPSE_GLOBAL_ROWS, grows.size, "global rows")
        assertEquals(M04Golden.ECLIPSE_GLOBAL_ROWS, counts.getValue("globalRows").i(), "meta globalRows")
        val sites = root.getValue("local").a().map { it.o() }
        val metaSites = meta.getValue("sites").a().map { it.o() }
        assertEquals(M04Golden.ECLIPSE_SITES, sites.size, "local sites")
        assertEquals(M04Golden.ECLIPSE_SITES, metaSites.size, "meta sites")
        assertEquals(M04Golden.ECLIPSE_LOCAL_ROWS, sites.sumOf { it.getValue("rows").a().size }, "local rows")
        assertEquals(M04Golden.ECLIPSE_LOCAL_ROWS, counts.getValue("localRows").i(), "meta localRows")
        for ((k, st) in sites.withIndex()) {
            assertEquals(metaSites[k].getValue("name").s(), st.getValue("site").s(), "site $k name")
            assertEquals(metaSites[k].getValue("rows").i(), st.getValue("rows").a().size, "site $k rows")
        }
        val range = meta.getValue("range").a().map { it.s() }
        val from = Instant.parse(range[0] + "T00:00:00Z")
        val to = Instant.parse(range[1] + "T00:00:00Z")
        val t = Tally()
        val search = EclipseSearch(lim.eph())
        val engine = search.global(from, to)
        val matched = matchGlobal(lim, grows, engine, t)
        M04Golden.parallel(sites.indices.toList()) { k ->
            checkSite(lim, sites[k], metaSites[k], grows, matched, t)
        }
        val rowsFragile = grows.count { fr(it, lim.frag(9)).isNotEmpty() } + sites.sumOf { st ->
            st.getValue("rows").a().count { fr(it.a(), lim.frag(9)).isNotEmpty() }
        }
        M04Golden.report(
            "eclipses ${lim.name}",
            listOf(
                "global: fixture rows ${grows.size} (eclipses ${grows.count { it[8].i() == 1 }}, near-miss " +
                    "${grows.count { it[8].i() == 0 }}), engine eclipses ${engine.size}, " +
                    "unmatched-but-fragile ${t.unmatchedFragile}",
                "global greatest max: lunar ${t.greatestLunar.fmt()} s, solar ${t.greatestSolar.fmt()} s " +
                    "(cap ${lim.contactCapS})",
                "magnitude max |d|: ${t.magnitude.fmt()} (limit ${lim.magnitudeTol})",
                "contacts compared ${t.contacts}: max |dt| ${t.contactDt.fmt()} s, " +
                    "max dt/limit ${t.contactRatio.fmt()}",
                "local rows compared ${t.localCompared}, no-eclipse rows asserted ${t.localAbsent}, " +
                    "absent solar sites asserted ${t.absentSolarRows}; max |d maxAt| ${t.maxAt.fmt()} s",
                "visibility endpoints: horizon max ${t.horizon.fmt()} s (limit ${lim.horizonS}); " +
                    "sutak max ${t.sutak.fmt()} s; rows compared ${t.visCompared}, " +
                    "skipped as fragile ${t.visSkipped}",
                "fragile rows (this backend) $rowsFragile of ${grows.size + M04Golden.ECLIPSE_LOCAL_ROWS}",
            ),
        )
        M04Golden.assertNoFailures("eclipses ${lim.name}", t.failures.list())
    }

    // ----------------------------------------------------------------------------------------- global set
    private fun matchGlobal(
        lim: M04Golden.Limits,
        grows: List<JsonArray>,
        engine: List<GlobalEclipse>,
        t: Tally,
    ): Array<GlobalEclipse?> {
        val matched = arrayOfNulls<GlobalEclipse>(grows.size)
        val col = lim.frag(9)
        for (e in engine) {
            var best = -1
            var bestDt = Double.MAX_VALUE
            for ((j, r) in grows.withIndex()) {
                if ((r[0].i() == 1) != e.solar) continue
                val dt = M04Golden.dtS(e.greatest, r[1].l())
                if (dt < bestDt) {
                    bestDt = dt
                    best = j
                }
            }
            if (best < 0 || !(bestDt < MATCH_S)) {
                t.failures += "${lim.name} engine eclipse (solar=${e.solar}) without a fixture row"
                continue
            }
            val row = grows[best]
            if (matched[best] != null) t.failures += "${lim.name} two engine eclipses for global row $best"
            if (row[8].i() == 0) {
                if ("kind" in fr(row, col)) {
                    t.unmatchedFragile++
                } else {
                    t.failures += "${lim.name} near-miss row $best found as eclipse"
                }
                continue
            }
            matched[best] = e
        }
        for ((j, r) in grows.withIndex()) {
            val kindFragile = "kind" in fr(r, col)
            if (kindFragile) t.fragileGlobal++
            if (r[8].i() == 1 && matched[j] == null) {
                if (kindFragile) t.unmatchedFragile++ else t.failures += "${lim.name} fixture eclipse $j not found"
                continue
            }
            val e = matched[j] ?: continue
            compareGlobal(lim, j, r, e, kindFragile, t)
        }
        return matched
    }

    private fun compareGlobal(
        lim: M04Golden.Limits,
        j: Int,
        r: JsonArray,
        e: GlobalEclipse,
        kindFragile: Boolean,
        t: Tally,
    ) {
        val at = "global row $j"
        val curv = r[7].d()
        val eArc = if (e.solar) lim.solarContactArcsec else lim.lunarContactArcsec
        val gLimit = if (curv.isFinite() && curv > 0.0) {
            minOf(lim.contactCapS, 1.0 + sqrt(2.0 * eArc / curv))
        } else {
            Double.NaN
        }
        val dt = M04Golden.dtS(e.greatest, r[1].l())
        (if (e.solar) t.greatestSolar else t.greatestLunar).add(dt, at)
        if (!(dt <= gLimit)) t.failures += "${lim.name} $at greatest |dt| $dt s > $gLimit s"
        val fixtureContacts = r[6].a().associate { it.a()[0].s() to it.a() }
        if (e.solar) {
            if (e.contacts.isNotEmpty()) t.failures += "${lim.name} $at solar global contacts must be empty"
            if (e.lunarKind != null || e.umbralMag != null || e.penumbralMag != null) {
                t.failures += "${lim.name} $at solar global eclipse carries lunar fields"
            }
            return
        }
        if (!kindFragile && e.lunarKind?.name != r[3].s()) {
            t.failures += "${lim.name} $at kind ${e.lunarKind} vs ${r[3].s()}"
        }
        fun mag(name: String, got: Double?, want: Double) {
            val d = if (got == null) Double.NaN else abs(got - want)
            t.magnitude.add(d, "$at $name")
            if (!(d <= lim.magnitudeTol)) t.failures += "${lim.name} $at $name |d| $d > ${lim.magnitudeTol}"
        }
        mag("umbral", e.umbralMag, r[4].d())
        mag("penumbral", e.penumbralMag, r[5].d())
        val got = e.contacts.mapKeys { it.key.name }
        if (!kindFragile && got.keys != fixtureContacts.keys) {
            t.failures += "${lim.name} $at contacts ${got.keys} vs ${fixtureContacts.keys}"
        }
        val frag = fr(r, lim.frag(9))
        for ((name, x) in fixtureContacts) {
            val inst = got[name] ?: continue
            if ("C:$name" in frag) continue
            contactGate(lim, "$at $name", inst, x[1].l(), x[2].d(), eArc, t)
        }
    }

    private fun contactGate(
        lim: M04Golden.Limits,
        at: String,
        got: Instant,
        ms: Long,
        rate: Double,
        eArc: Double,
        t: Tally,
    ) {
        val dt = M04Golden.dtS(got, ms)
        val limit = M04Golden.rateLimit(lim.contactCapS, eArc, rate)
        synchronized(t) { t.contacts++ }
        t.contactDt.add(dt, at)
        t.contactRatio.add(dt / limit, at)
        if (!(dt <= limit)) t.failures += "${lim.name} $at contact |dt| $dt s > $limit s (rate $rate)"
    }

    // ----------------------------------------------------------------------------------------------- local
    private fun checkSite(
        lim: M04Golden.Limits,
        site: kotlinx.serialization.json.JsonObject,
        meta: kotlinx.serialization.json.JsonObject,
        grows: List<JsonArray>,
        matched: Array<GlobalEclipse?>,
        shared: Tally,
    ) {
        val t = Tally()
        val place = Place(meta.getValue("lat").d(), meta.getValue("lon").d(), 0.0)
        val search = EclipseSearch(lim.eph())
        val name = site.getValue("site").s()
        val rows = site.getValue("rows").a().map { it.a() }
        val have = HashSet<Int>()
        for (row in rows) {
            val gi = row[0].i()
            assertTrue(gi in grows.indices && have.add(gi), "local row global index $gi")
            val g = matched[gi] ?: continue
            checkLocal(lim, "$name row for global $gi", grows[gi], row, g, search.local(g, place), t)
        }
        // solar eclipses the fixture lists no row for: the engine must not reach this site (within the delta)
        for ((gi, g) in matched.withIndex()) {
            if (g == null || !g.solar || gi in have) continue
            val local = search.local(g, place)
            t.absentSolarRows++
            if (local != null && !(local.magnitude < lim.magnitudeTol)) {
                t.failures += "${lim.name} $name global $gi: engine reaches the site " +
                    "(magnitude ${local.magnitude}), no fixture row"
            }
        }
        shared.merge(t)
        t.failures.list().forEach { shared.failures += it }
        t.contactDt.let { shared.contactDt.add(it.value, "site"); }
        t.contactRatio.let { shared.contactRatio.add(it.value, "site") }
        t.maxAt.let { shared.maxAt.add(it.value, "site") }
        t.horizon.let { shared.horizon.add(it.value, "site") }
        t.sutak.let { shared.sutak.add(it.value, "site") }
        t.magnitude.let { shared.magnitude.add(it.value, "site") }
    }

    private fun checkLocal(
        lim: M04Golden.Limits,
        at: String,
        g: JsonArray,
        row: JsonArray,
        e: GlobalEclipse,
        local: LocalEclipse?,
        t: Tally,
    ) {
        val solar = g[0].i() == 1
        val fragL = fr(row, lim.frag(9))
        val kindFragile = if (solar) "kind" in fragL else "kind" in fr(g, lim.frag(9))
        if (row[1].isNull()) {
            if (local != null && !kindFragile) {
                t.failures += "${lim.name} $at engine found a local eclipse in a near-miss row"
            }
            t.localAbsent++
            return
        }
        if (local == null) {
            if (!kindFragile) t.failures += "${lim.name} $at no local eclipse, fixture ${row[1].s()}"
            return
        }
        t.localCompared++
        if (kindFragile) t.fragileLocal++
        if (!kindFragile && local.kind.name != row[1].s()) {
            t.failures += "${lim.name} $at kind ${local.kind} vs ${row[1].s()}"
        }
        val magSame = solar || local.kind.name == row[1].s()
        if (magSame) {
            val dm = abs(local.magnitude - row[2].d())
            t.magnitude.add(dm, at)
            if (!(dm <= lim.magnitudeTol)) t.failures += "${lim.name} $at magnitude |d| $dm > ${lim.magnitudeTol}"
        }
        val dMax = M04Golden.dtS(local.maxAt, row[3].l())
        t.maxAt.add(dMax, at)
        if (!(dMax <= lim.contactCapS)) t.failures += "${lim.name} $at maxAt |dt| $dMax s > ${lim.contactCapS} s"
        val eArc = if (solar) lim.solarContactArcsec else lim.lunarContactArcsec
        val fixture: Map<String, JsonArray> = (if (solar) row[4] else g[6]).a().associate { it.a()[0].s() to it.a() }
        if (solar) {
            val got = local.contacts.mapKeys { it.key.name }
            if (!kindFragile && got.keys != fixture.keys) {
                t.failures += "${lim.name} $at contacts ${got.keys} vs ${fixture.keys}"
            }
            for ((name, x) in fixture) {
                val inst = got[name] ?: continue
                if ("C:$name" in fragL) continue
                contactGate(lim, "$at $name", inst, x[1].l(), x[2].d(), eArc, t)
            }
        } else if (local.contacts != e.contacts) {
            t.failures += "${lim.name} $at lunar local contacts differ from the global row"
        }
        // ---- visibility and sutak
        if ("vis" in fragL || kindFragile) {
            t.visSkipped++
            return
        }
        t.visCompared++
        val (n1, n4) = when {
            solar -> "C1" to "C4"
            local.kind == EclipseKind.LUNAR_PENUMBRAL -> "P1" to "P4"
            else -> "U1" to "U4"
        }
        val c1 = fixture[n1]
        val c4 = fixture[n4]
        if (c1 == null || c4 == null) {
            t.failures += "${lim.name} $at fixture lacks $n1/$n4"
            return
        }
        fun endpoint(label: String, got: Instant, ms: Long, contact: JsonArray) {
            if (ms == contact[1].l()) {
                contactGate(lim, "$at $label", got, ms, contact[2].d(), eArc, t)
            } else {
                val dt = M04Golden.dtS(got, ms)
                t.horizon.add(dt, "$at $label")
                if (!(dt <= lim.horizonS)) t.failures += "${lim.name} $at $label horizon |dt| $dt s > ${lim.horizonS} s"
            }
        }
        val vis = local.visible
        if ((vis == null) != row[5].isNull()) {
            t.failures += "${lim.name} $at visible ${vis != null} vs fixture ${!row[5].isNull()}"
        } else if (vis != null) {
            endpoint("visible start", vis.start, row[5].l(), c1)
            endpoint("visible end", vis.end, row[6].l(), c4)
        }
        val sut = local.sutak
        if ((sut == null) != row[7].isNull()) {
            t.failures += "${lim.name} $at sutak ${sut != null} vs fixture ${!row[7].isNull()}"
        } else if (sut != null) {
            val s1 = if (solar) Contact.C1 else Contact.U1
            val s4 = if (solar) Contact.C4 else Contact.U4
            val before = (if (solar) SOLAR_SUTAK_S else LUNAR_SUTAK_S).seconds
            if (sut.start != local.contacts.getValue(s1) - before || sut.end != local.contacts.getValue(s4)) {
                t.failures += "${lim.name} $at sutak is not contact - 12 h / 9 h .. last contact"
            }
            val x1 = fixture[s1.name]
            val x4 = fixture[s4.name]
            if (x1 == null || x4 == null) {
                t.failures += "${lim.name} $at fixture lacks ${s1.name}/${s4.name}"
            } else {
                val l1 = M04Golden.rateLimit(lim.contactCapS, eArc, x1[2].d())
                val l4 = M04Golden.rateLimit(lim.contactCapS, eArc, x4[2].d())
                val d1 = M04Golden.dtS(sut.start, row[7].l())
                val d4 = M04Golden.dtS(sut.end, row[8].l())
                t.sutak.add(maxOf(d1, d4), at)
                if (!(d1 <= l1) || !(d4 <= l4)) t.failures += "${lim.name} $at sutak |dt| $d1 / $d4 s > $l1 / $l4 s"
            }
        }
    }

    private companion object {
        /** An engine eclipse belongs to the fixture row of the same kind within this many seconds of greatest. */
        const val MATCH_S = 3600.0
        const val SOLAR_SUTAK_S = 43_200L
        const val LUNAR_SUTAK_S = 32_400L
    }
}
