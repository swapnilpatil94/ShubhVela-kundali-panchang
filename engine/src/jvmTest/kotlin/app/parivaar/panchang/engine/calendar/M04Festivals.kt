package app.parivaar.panchang.engine.calendar

import app.parivaar.panchang.engine.Place
import app.parivaar.panchang.engine.calendar.M04Golden.a
import app.parivaar.panchang.engine.calendar.M04Golden.b
import app.parivaar.panchang.engine.calendar.M04Golden.d
import app.parivaar.panchang.engine.calendar.M04Golden.o
import app.parivaar.panchang.engine.calendar.M04Golden.i
import app.parivaar.panchang.engine.calendar.M04Golden.isNull
import app.parivaar.panchang.engine.calendar.M04Golden.l
import app.parivaar.panchang.engine.calendar.M04Golden.s
import app.parivaar.panchang.engine.jyotish.RuleTestSupport
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.time.Instant

/**
 * Festival side of the M04 golden harness: the shipped rule files, the row comparison against
 * `festivals_oracle.json` (docs/specs/M04.md section 4) and per-rule-type tallies.
 */
internal object M04Festivals {
    val FILES = listOf("pan_india", "recurring", "tamil", "malayalam", "bengali", "odia")
    const val ROWS = 34_777
    const val DEFS = 45
    const val ROWS_PER_CITY_MIN = 3_000

    /** Lower bounds of the fixture-free rates a limb limit divides by (see [limbLimit]). */
    private const val ELONGATION_RATE_MIN = 0.44
    private const val SUN_RATE_MIN = 0.0396

    val defs: List<FestivalDef> by lazy {
        FILES.flatMap { FestivalRules.parse(it, RuleTestSupport.rule("festivals/$it.json")).defs }
    }

    fun typeOf(def: FestivalDef): String = when (def.rule) {
        is FestivalRule.TithiAt, is FestivalRule.WeekdayTithi -> "tithi_at"
        is FestivalRule.Ekadashi -> "ekadashi"
        is FestivalRule.SankrantiRule -> "sankranti"
        is FestivalRule.NakshatraInSolarMonth -> "nakshatra_in_solar_month"
        is FestivalRule.Relative -> "relative"
        else -> "other"
    }

    /** Limb-boundary endpoints (L): M03 gate with the smallest elongation rate (the row carries no rate). */
    fun limbLimit(lim: M04Golden.Limits): Double =
        minOf(lim.momentCapS, 1.0 + lim.momentArcsec / ELONGATION_RATE_MIN)

    fun sankrantiLimit(lim: M04Golden.Limits): Double = lim.sankrantiBaseS + lim.sankrantiArcsec / SUN_RATE_MIN

    class Type {
        var rows = 0
        var fragile = 0
        var absentFragile = 0
        var failures = 0
    }

    class Tally {
        val failures = M04Golden.Failures()
        val types = java.util.concurrent.ConcurrentHashMap<String, Type>()
        val sun = M04Golden.Stat()
        val limb = M04Golden.Stat()
        val sankranti = M04Golden.Stat()
        val flagRows = java.util.concurrent.ConcurrentHashMap<String, Int>()

        fun type(t: String): Type = types.getOrPut(t) { Type() }

        @Synchronized
        fun count(t: String, f: Type.() -> Unit) = type(t).f()
    }

    /**
     * Compares one city's fixture [rows] with the engine's [occ] (both restricted to the same dates by the
     * caller). Non-fragile rows must be matched exactly (date, label, tithi, kala, flags, window endpoints);
     * fragile rows (this backend) only need their date set: an engine occurrence for the row must be one of
     * the row's alternative dates; an engine occurrence not explained by a row is a failure.
     */
    fun compare(
        lim: M04Golden.Limits,
        ctx: String,
        rows: List<JsonArray>,
        occ: List<FestivalOccurrence>,
        typeOf: (String) -> String,
        t: Tally,
    ) {
        val byId = occ.groupBy { it.id }.mapValues { (_, v) -> v.associateBy { it.date.toString() } }
        val explained = HashSet<Pair<String, String>>()
        for (row in rows) {
            val id = row[0].s()
            val date = row[1].s()
            val type = typeOf(id)
            val engine = byId[id].orEmpty()
            t.count(type) { this.rows++ }
            if (!row[lim.frag(16)].b()) {
                val e = engine[date]
                if (e == null) {
                    t.failures += "${lim.name} $ctx $id $date missing from the engine"
                    t.count(type) { failures++ }
                } else {
                    explained += id to date
                    if (!values(lim, "$ctx $id $date", row, e, t)) t.count(type) { failures++ }
                }
            } else {
                val hits = row[lim.frag(18)].a().map { it.s() }.filter { it in engine }
                explained += hits.map { id to it }
                t.count(type) { fragile++ }
                if (hits.isEmpty()) t.count(type) { absentFragile++ }
            }
        }
        for (e in occ) {
            if ((e.id to e.date.toString()) !in explained) {
                t.failures += "${lim.name} $ctx ${e.id} ${e.date} not in the fixture (nor in a fragile row's alt)"
                t.count(typeOf(e.id)) { failures++ }
            }
        }
    }

    @Suppress("CyclomaticComplexMethod") // one flat list of per-field gates of a festival row
    private fun values(lim: M04Golden.Limits, at: String, row: JsonArray, e: FestivalOccurrence, t: Tally): Boolean {
        val before = t.failures.list().size
        fun fail(what: String) {
            t.failures += "${lim.name} $at $what"
        }
        val label = e.label?.let { "[${it.masa.ordinal},${it.adhika},${it.kshayaMasa?.ordinal ?: "null"}]" }
        if ((label ?: "null") != row[2].toString()) fail("label $label vs ${row[2]}")
        val wantTithi = if (row[3].isNull()) null else row[3].toString()
        if (e.tithi?.toString() != wantTithi) fail("tithi ${e.tithi} vs ${row[3]}")
        val wantKala = if (row[4].isNull()) null else row[4].s()
        if (e.kala?.name?.lowercase() != wantKala) fail("kala ${e.kala} vs ${row[4]}")
        val flags = e.flags.map { it.name }.sorted()
        if (flags != row[15].a().map { it.s() }) fail("flags $flags vs ${row[15]}")
        val moon = e.kala == Kala.MOONRISE
        val point = row[4].isNull() && row[5].let { !it.isNull() }
        fun endpoint(name: String, got: Instant?, ms: JsonElement, src: String) {
            if ((got == null) != ms.isNull()) {
                fail("$name present ${got != null} vs fixture ${!ms.isNull()}")
                return
            }
            if (got == null) return
            val dt = M04Golden.dtS(got, ms.l())
            val limit = when {
                point -> M04Festivals.sankrantiLimit(lim).also { t.sankranti.add(dt, at) }
                src == "L" -> limbLimit(lim).also { t.limb.add(dt, at) }
                else -> lim.sunS.also { t.sun.add(dt, at) }
            }
            if (!(dt <= limit)) fail("$name |dt| $dt s > $limit s${if (moon) " (moonrise)" else ""}")
        }
        endpoint("kala start", e.kalaWindow?.start, row[5], "S")
        endpoint("kala end", e.kalaWindow?.end, row[6], "S")
        endpoint("muhurat start", e.muhurat?.start, row[7], if (row[9].isNull()) "S" else row[9].s())
        endpoint("muhurat end", e.muhurat?.end, row[8], if (row[10].isNull()) "S" else row[10].s())
        endpoint("parana start", e.parana?.start, row[11], if (row[13].isNull()) "S" else row[13].s())
        endpoint("parana end", e.parana?.end, row[12], if (row[14].isNull()) "S" else row[14].s())
        for (f in e.flags) t.flagRows.merge(f.name, 1, Int::plus)
        return t.failures.list().size == before
    }

    class FixtureCity(val name: String, val place: Place, val offsetMin: Int, val rows: List<JsonArray>)

    fun cities(root: JsonObject): List<FixtureCity> {
        val meta = M04Golden.meta(root)
        val metaCities = meta.getValue("cities").a()
        val rows = root.getValue("cities").a()
        kotlin.test.assertEquals(M04Golden.CITIES, rows.size, "festival fixture cities")
        kotlin.test.assertEquals(M04Golden.CITIES, metaCities.size, "festival meta cities")
        return rows.mapIndexed { k, c ->
            val o = c.o()
            val mo = metaCities[k].o()
            kotlin.test.assertEquals(mo.getValue("name").s(), o.getValue("name").s(), "city $k name")
            kotlin.test.assertEquals(mo.getValue("offsetMin").i(), o.getValue("offsetMin").i(), "city $k offset")
            val list = o.getValue("rows").a().map { it.a() }
            kotlin.test.assertEquals(mo.getValue("rows").i(), list.size, "city $k rows vs meta")
            FixtureCity(
                o.getValue("name").s(),
                Place(mo.getValue("lat").d(), mo.getValue("lon").d(), 0.0),
                o.getValue("offsetMin").i(),
                list,
            )
        }
    }
}
