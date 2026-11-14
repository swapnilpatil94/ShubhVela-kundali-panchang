package app.parivaar.panchang.engine.panchang

import app.parivaar.panchang.engine.CalcSettings
import app.parivaar.panchang.engine.astro.MaxError
import app.parivaar.panchang.engine.panchang.M03Golden.a
import app.parivaar.panchang.engine.panchang.M03Golden.i
import app.parivaar.panchang.engine.panchang.M03Golden.l
import app.parivaar.panchang.engine.panchang.M03Golden.s
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Tag
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Instant

/**
 * G-M03 windows: both backends x 2,000 city-days against `panchang_windows.json` (docs/specs/M03.md section 4):
 * same (kind, part, code, night) list and times within 5 s / 10 s on days without F/S; on every day the engine
 * equals the section 3.3 arithmetic on its own sun events (1 ms); PRINTED_6AM vs the fixture (1 ms); the
 * `unavailable` set is exactly the three kinds with no shipped table.
 */
@Tag("golden")
class PanchangWindowsGoldenTest {
    /** A window reduced to its comparison key and instants. */
    private data class W(
        val kind: WindowKind,
        val part: Int,
        val code: String?,
        val night: Boolean,
        val a: Instant,
        val b: Instant,
    ) {
        val key: String get() = "${kind.ordinal}/$night/$part/$code"
    }

    private class Pass(val limits: M03Golden.Limits) {
        val calc = PanchangCalculator(limits.eph(), M03Golden.tables)
        val failures = mutableListOf<String>()
        val dt = MaxError()
        val arithmetic = MaxError()
        val printed = MaxError()
        var days = 0
        var compared = 0
        var windows = 0
        var arithmeticDays = 0
        var unavailableDays = 0
    }

    private fun kindOf(name: String): WindowKind =
        WindowKind.entries.firstOrNull { it.name == name } ?: error("unknown window kind in fixture: $name")

    private fun printedKind(name: String): WindowKind = when (name) {
        "RAHU_KAAL_PRINTED" -> WindowKind.RAHU_KAAL
        "YAMAGANDA_PRINTED" -> WindowKind.YAMAGANDA
        "GULIKA_PRINTED" -> WindowKind.GULIKA
        else -> error("unknown printed window kind in fixture: $name")
    }

    private fun parse(rows: JsonArray, printed: Boolean, allowed: Set<String>): List<W> = rows.map { r ->
        val x = r.a()
        val name = x[0].s()
        require(name in allowed) { "unexpected window kind in fixture: $name" }
        val code = if (x[2] is JsonNull) null else x[2].s()
        val night = x[3].jsonPrimitive.booleanOrNull ?: error("night is not a boolean")
        W(if (printed) printedKind(name) else kindOf(name), x[1].i(), code, night,
            Instant.fromEpochMilliseconds(x[4].l()), Instant.fromEpochMilliseconds(x[5].l()))
    }

    private fun p(a: Instant, b: Instant, n: Int, i: Int): Instant =
        a + ((b - a).inWholeNanoseconds * i / n).nanoseconds

    /** Section 3.3 on the day's own sun events (default ACTUAL_SUNRISE, Abhijit off on Wednesday). */
    private fun expected(day: PanchangDay): List<W> {
        val v = day.vaar
        val out = mutableListOf<W>()
        fun add(kind: WindowKind, part: Int, code: String?, night: Boolean, a: Instant, b: Instant, n: Int) =
            out.add(W(kind, part, code, night, p(a, b, n, part - 1), p(a, b, n, part)))
        add(WindowKind.RAHU_KAAL, WeekdayTables.rahuPart(v), null, false, day.sunrise, day.sunset, EIGHT)
        add(WindowKind.YAMAGANDA, WeekdayTables.yamagandaPart(v), null, false, day.sunrise, day.sunset, EIGHT)
        add(WindowKind.GULIKA, WeekdayTables.gulikaPart(v), null, false, day.sunrise, day.sunset, EIGHT)
        if (v != Vaar.WEDNESDAY) add(WindowKind.ABHIJIT, ABHIJIT, null, false, day.sunrise, day.sunset, MUHURTAS)
        add(WindowKind.BRAHMA_MUHURTA, BRAHMA, null, true, day.sunset, day.nextSunrise, MUHURTAS)
        for (s in M03Golden.tables.durmuhurta.getValue(v)) {
            val (a, b) = if (s.night) day.sunset to day.nextSunrise else day.sunrise to day.sunset
            add(WindowKind.DURMUHURTA, s.muhurta, null, s.night, a, b, MUHURTAS)
        }
        for (k in 1..EIGHT) {
            val dayId = WeekdayTables.dayChoghadiya(v)[k - 1].name
            val nightId = M03Golden.tables.choghadiya.night.getValue(v)[k - 1]
            add(WindowKind.CHOGHADIYA, k, dayId, false, day.sunrise, day.sunset, EIGHT)
            add(WindowKind.CHOGHADIYA, k, nightId, true, day.sunset, day.nextSunrise, EIGHT)
        }
        val h0 = WeekdayTables.HORA_ORDER.indexOf(v.lord)
        for (h in 1..HORAS) {
            val night = h > HORAS / 2
            val (a, b) = if (night) day.sunset to day.nextSunrise else day.sunrise to day.sunset
            val k = if (night) h - HORAS / 2 else h
            out.add(
                W(WindowKind.HORA, k, WeekdayTables.HORA_ORDER[(h0 + h - 1) % HORA_COUNT].name, night,
                    p(a, b, HORAS / 2, k - 1), p(a, b, HORAS / 2, k)),
            )
        }
        return out.sortedBy { it.key }
    }

    /** Pairs [want] and [got] by key (both sorted); returns failures and records time errors in [stat]. */
    private fun match(
        label: String,
        want: List<W>,
        got: List<W>,
        limitS: Double,
        stat: MaxError,
        out: MutableList<String>,
    ) {
        if (want.map { it.key } != got.map { it.key }) {
            out += "$label window lists differ: expected ${want.map { it.key }} got ${got.map { it.key }}"
            return
        }
        for ((k, w) in want.withIndex()) {
            for ((x, y) in listOf(w.a to got[k].a, w.b to got[k].b)) {
                val dt = abs((x - y).inWholeNanoseconds) / NS
                stat.add(dt, "$label ${w.key}")
                if (!(dt <= limitS)) out += "$label ${w.key} |dt| $dt s > $limitS s"
            }
        }
    }

    private fun checkDay(pass: Pass, c: M03Golden.City, row: JsonArray, kinds: Set<String>, printedKinds: Set<String>) {
        pass.days++
        val flags = row[1].s()
        val label = "${pass.limits.name} ${c.name} ${row[0].s()}"
        val date = LocalDate.parse(row[0].s())
        if ('U' in flags) {
            pass.unavailableDays++
            val code = runCatching { pass.calc.day(date, c.offsetSeconds, c.place) }
                .exceptionOrNull().let { (it as? PanchangUnavailableException)?.code }
            if (code != PanchangError.NO_SUN_DAY) pass.failures += "$label expected NO_SUN_DAY, got $code"
            if (row[2].a().isNotEmpty()) pass.failures += "$label unavailable day has fixture windows"
            return
        }
        val fixture = row[2].a()
        val main = parse(JsonArray(fixture.filter { it.a()[0].s() in kinds }), false, kinds)
        val printed = parse(JsonArray(fixture.filter { it.a()[0].s() in printedKinds }), true, printedKinds)
        assertEquals(fixture.size, main.size + printed.size, "$label unknown row kind")
        val day = pass.calc.day(date, c.offsetSeconds, c.place)
        val got = day.windows.filter { it.kind in FIXTURE_KINDS }
            .map { W(it.kind, it.part, it.code, it.night, it.start, it.end) }.sortedBy { it.key }
        pass.windows += got.size
        if (day.unavailable != UNAVAILABLE) {
            pass.failures += "$label unavailable ${day.unavailable}, expected $UNAVAILABLE"
        }
        if ('F' !in flags && 'S' !in flags) {
            pass.compared++
            match(label, main.sortedBy { it.key }, got, pass.limits.windowS, pass.dt, pass.failures)
        }
        pass.arithmeticDays++
        match("$label arithmetic", expected(day), got, ARITH_S, pass.arithmetic, pass.failures)
        printed(pass, c, date, printed, label)
    }

    private fun printed(pass: Pass, c: M03Golden.City, date: LocalDate, want: List<W>, label: String) {
        val day = pass.calc.day(date, c.offsetSeconds, c.place, CalcSettings(timingStyle = TimingStyle.PRINTED_6AM))
        val kinds = setOf(WindowKind.RAHU_KAAL, WindowKind.YAMAGANDA, WindowKind.GULIKA)
        val got = day.windows.filter { it.kind in kinds }
            .map { W(it.kind, it.part, it.code, it.night, it.start, it.end) }.sortedBy { it.key }
        if (got.size != kinds.size) pass.failures += "$label PRINTED_6AM expected 3 windows, got ${got.size}"
        match("$label PRINTED_6AM", want.sortedBy { it.key }, got, ARITH_S, pass.printed, pass.failures)
    }

    private fun run(limits: M03Golden.Limits) {
        val root = M03Golden.fixture("panchang_windows.json")
        val meta = M03Golden.meta(root)
        assertEquals(M03Golden.WINDOW_CITY_DAYS, meta.getValue("windowCityDays").i(), "meta.windowCityDays")
        val kinds = meta.getValue("windowKinds").a().map { it.s() }.toSet()
        val printedKinds = meta.getValue("printedKinds").a().map { it.s() }.toSet()
        assertEquals(WINDOW_KINDS, kinds, "fixture window kinds")
        assertEquals(setOf("RAHU_KAAL_PRINTED", "YAMAGANDA_PRINTED", "GULIKA_PRINTED"), printedKinds, "printed kinds")
        val pass = Pass(limits)
        val cities = M03Golden.cities(root, "windowDays")
        for (c in cities) {
            assertEquals(M03Golden.WINDOW_DAYS_PER_CITY, c.row.getValue("days").a().size, "window days of ${c.name}")
            for (r in c.row.getValue("days").a()) checkDay(pass, c, r.a(), kinds, printedKinds)
        }
        assertEquals(M03Golden.WINDOW_CITY_DAYS, pass.days, "window city-days iterated")
        M03Golden.report(
            "M03 windows ${limits.name} (${M03Golden.WINDOW_CITY_DAYS} city-days)",
            listOf(
                "days compared to oracle (non F/S)=${pass.compared} max_s=${pass.dt.value} (${pass.dt.where}); " +
                    "limit ${limits.windowS} s",
                "arithmetic check days=${pass.arithmeticDays} max_s=${pass.arithmetic.value} " +
                    "(${pass.arithmetic.where}); limit $ARITH_S s",
                "PRINTED_6AM max_s=${pass.printed.value} (${pass.printed.where}); limit $ARITH_S s",
                "unavailable days=${pass.unavailableDays}; engine windows seen=${pass.windows}",
            ),
        )
        M03Golden.assertNoFailures("M03 windows ${limits.name}", pass.failures)
    }

    @Test
    fun precisionBackendMatchesOracle() = run(M03Golden.PRECISION)

    @Test
    fun baseBackendMatchesOracle() = run(M03Golden.BASE)

    private companion object {
        const val NS = 1e9
        const val ARITH_S = 0.001
        const val EIGHT = 8
        const val MUHURTAS = 15
        const val ABHIJIT = 8
        const val BRAHMA = 14
        const val HORAS = 24
        const val HORA_COUNT = 7
        val FIXTURE_KINDS = setOf(
            WindowKind.RAHU_KAAL, WindowKind.YAMAGANDA, WindowKind.GULIKA, WindowKind.ABHIJIT,
            WindowKind.BRAHMA_MUHURTA, WindowKind.DURMUHURTA, WindowKind.CHOGHADIYA, WindowKind.HORA,
        )
        val WINDOW_KINDS = FIXTURE_KINDS.map { it.name }.toSet()
        val UNAVAILABLE = setOf(WindowKind.VARJYAM, WindowKind.AMRIT_KAAL, WindowKind.GOWRI)
    }
}
