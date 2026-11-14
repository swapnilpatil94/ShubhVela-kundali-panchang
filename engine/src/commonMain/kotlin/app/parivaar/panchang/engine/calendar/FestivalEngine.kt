package app.parivaar.panchang.engine.calendar

import app.parivaar.panchang.engine.CalcSettings
import app.parivaar.panchang.engine.Place
import app.parivaar.panchang.engine.astro.Body
import app.parivaar.panchang.engine.astro.Ephemeris
import app.parivaar.panchang.engine.astro.RiseSetKind
import app.parivaar.panchang.engine.astro.RiseSetResult
import app.parivaar.panchang.engine.astro.TimeTables
import app.parivaar.panchang.engine.calendar.internal.DayLookup
import app.parivaar.panchang.engine.calendar.internal.EvalContext
import app.parivaar.panchang.engine.calendar.internal.Kalas
import app.parivaar.panchang.engine.calendar.internal.TithiRuleEval
import app.parivaar.panchang.engine.calendar.internal.ekadashiDay
import app.parivaar.panchang.engine.calendar.internal.skipUnavailable
import app.parivaar.panchang.engine.panchang.DayFlag
import app.parivaar.panchang.engine.panchang.Karana
import app.parivaar.panchang.engine.panchang.LimbKind
import app.parivaar.panchang.engine.panchang.Paksha
import app.parivaar.panchang.engine.panchang.Vaar
import app.parivaar.panchang.engine.panchang.internal.DayWindows
import app.parivaar.panchang.engine.panchang.internal.SunDay
import kotlin.math.abs
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Festival occurrences from rule definitions (docs/specs/M04.md sections 3.4-3.7). Dates are civil dates of
 * panchang days (sunrise to next sunrise) at the caller's offsets; results never hold the place. Holds the
 * caches of its calendars: not thread-safe.
 */
class FestivalEngine(eph: Ephemeris, time: TimeTables = TimeTables.DEFAULT) {
    private val calendar = CalendarCalculator(eph, time)
    private val lunar = calendar.lunar
    private val solar = calendar.solar

    /** Occurrences of [defs] dated [first]..[last], sorted by (date, id), one per (id, date). */
    fun occurrences(
        defs: List<FestivalDef>,
        first: LocalDate,
        last: LocalDate,
        place: Place,
        s: CalcSettings = CalcSettings(),
        utcOffsetSeconds: (LocalDate) -> Int,
    ): List<FestivalOccurrence> {
        require(first <= last) { "first must not be after last" }
        val run = Run(place, s, DayLookup.of(lunar.riseSet, place, s, utcOffsetSeconds), first, last)
        val byId = defs.associateBy { it.id }
        return defs.flatMap { def ->
            val rule = def.rule
            if (rule is FestivalRule.Relative) {
                val target = requireNotNull(byId[rule.to]) { "relative rule target missing" }
                require(target.rule !is FestivalRule.Relative) { "relative rule target missing" }
                val reach = DatePeriod(days = abs(rule.days))
                val wide = Run(place, s, run.days, first.minus(reach), last.plus(reach))
                candidates(target, wide).map {
                    val date = it.date.plus(DatePeriod(days = rule.days))
                    FestivalOccurrence(def.id, date, it.label, null, null, null, null, null, it.flags)
                }
            } else {
                candidates(def, run)
            }.filter { it.date in first..last }.distinctBy { it.date }
        }.sortedWith(compareBy<FestivalOccurrence> { it.date }.thenBy { it.id })
    }

    /** [occurrences] for the Gregorian [year]. */
    fun year(
        defs: List<FestivalDef>,
        year: Int,
        place: Place,
        s: CalcSettings = CalcSettings(),
        utcOffsetSeconds: (LocalDate) -> Int,
    ): List<FestivalOccurrence> =
        occurrences(defs, LocalDate(year, 1, 1), LocalDate(year, DECEMBER, LAST_DAY), place, s, utcOffsetSeconds)

    /** One evaluation range: lunations and sankrantis over [midnight(first) - 40 d, midnight(last + 1) + 40 d]. */
    private inner class Run(
        val place: Place,
        val s: CalcSettings,
        val days: DayLookup,
        first: LocalDate,
        last: LocalDate,
    ) {
        val from: Instant = days.midnight(first) - MARGIN
        val to: Instant = days.midnight(last.plus(DatePeriod(days = 1))) + MARGIN
        val lunations: List<Lunation> by lazy { lunar.lunations(from, to, s) }
        val sankrantis: List<Sankranti> by lazy { lunar.sankrantis(from, to, s) }
        private val tithiSpans = HashMap<Instant, List<TimeRange>>()
        val ctx = EvalContext(days, ::moonrise, ::vishti, ::nakshatra)

        /** Tithi spans 1..30 of [l] (index - 1). */
        fun tithis(l: Lunation): List<TimeRange> = tithiSpans.getOrPut(l.newMoon) {
            lunar.limbs.spans(LimbKind.TITHI, l.newMoon, l.nextNewMoon, s).map { TimeRange(it.start, it.end) }
        }

        private fun moonrise(sd: SunDay): Instant? {
            val hours = ((sd.nextSunrise - sd.sunrise).inWholeSeconds + HOUR_S - 1) / HOUR_S
            val r = lunar.riseSet.find(Body.MOON, place, sd.sunrise, s, hours.toInt())
            return (r as? RiseSetResult.Events)?.list
                ?.firstOrNull { it.kind == RiseSetKind.RISE && it.at >= sd.sunrise && it.at < sd.nextSunrise }?.at
        }

        private fun vishti(a: Instant, b: Instant): List<TimeRange> = lunar.limbs.spans(LimbKind.KARANA, a, b, s)
            .filter { Karana.ofNumber(it.index) == Karana.VISHTI }.map { TimeRange(it.start, it.end) }

        private fun nakshatra(n: Int, a: Instant, b: Instant): List<TimeRange> =
            lunar.limbs.spans(LimbKind.NAKSHATRA, a, b, s).filter { it.index == n }.map { TimeRange(it.start, it.end) }
    }

    private fun candidates(def: FestivalDef, run: Run): List<FestivalOccurrence> = when (val r = def.rule) {
        is FestivalRule.TithiAt -> tithiAt(def, r, run)
        is FestivalRule.WeekdayTithi -> tithiAt(def, r.inner, run).filter { Vaar.of(it.date) == r.weekday }
        is FestivalRule.Ekadashi -> ekadashi(def, r, run)
        is FestivalRule.SankrantiRule -> sankranti(def, r, run)
        is FestivalRule.FixedGregorian ->
            fixed(def, r, DayLookup.utcDate(run.from).year..DayLookup.utcDate(run.to).year)
        is FestivalRule.NakshatraInSolarMonth -> nakshatraInMonth(def, r, run)
        is FestivalRule.Relative -> error("relative rules are expanded by occurrences")
    }

    private fun sankranti(def: FestivalDef, r: FestivalRule.SankrantiRule, run: Run): List<FestivalOccurrence> =
        run.sankrantis.filter { r.into == null || it.into == r.into }.mapNotNull { sk ->
            skipUnavailable {
                val kept = run.days.sankrantiDate(sk, r.day)
                val point = TimeRange(sk.at, sk.at)
                val sun = r.day != SankrantiDay.CIVIL && DayFlag.SUN_FALLBACK in run.days.sunDay(kept.date).flags
                FestivalOccurrence(def.id, kept.date, null, null, null, point, null, null, sunFlags(sun))
            }
        }

    private fun tithiAt(def: FestivalDef, r: FestivalRule.TithiAt, run: Run): List<FestivalOccurrence> =
        run.lunations.flatMap { l ->
            r.tithi.mapNotNull { t ->
                val lab = lunar.label(l, t, r.system, run.s)
                val labelFlags = labelFlags(def, r.masa, lab) ?: return@mapNotNull null
                val x = run.tithis(l)[t - 1]
                val c = skipUnavailable { TithiRuleEval.choose(x, r, run.ctx) } ?: return@mapNotNull null
                FestivalOccurrence(def.id, c.date, lab, t, r.kala, c.kalaWindow, c.muhurat, null, c.flags + labelFlags)
            }
        }

    private fun ekadashi(def: FestivalDef, r: FestivalRule.Ekadashi, run: Run): List<FestivalOccurrence> {
        val pakshas = r.paksha?.let { listOf(it) } ?: Paksha.entries
        return run.lunations.flatMap { l ->
            pakshas.mapNotNull { p ->
                val t = if (p == Paksha.SHUKLA) SHUKLA_EKADASHI else KRISHNA_EKADASHI
                val lab = lunar.label(l, t, r.system, run.s)
                val labelFlags = labelFlags(def, r.masa, lab) ?: return@mapNotNull null
                val spans = run.tithis(l)
                skipUnavailable {
                    val (fast, parana, flags) = ekadashiDay(spans[t - 1], spans[t], r.tradition,
                        { run.days.sunDay(it).sunrise }, run.days::containing)
                    val kala = if (r.tradition == Tradition.SMARTA) Kala.SUNRISE else Kala.ARUNODAYA
                    val sun = listOf(fast, fast.plus(ONE)).any { DayFlag.SUN_FALLBACK in run.days.sunDay(it).flags }
                    val all = flags + labelFlags + sunFlags(sun)
                    FestivalOccurrence(def.id, fast, lab, t, kala, run.ctx.kala(fast, kala), null, parana, all)
                }
            }
        }
    }

    private fun nakshatraInMonth(
        def: FestivalDef,
        r: FestivalRule.NakshatraInSolarMonth,
        run: Run,
    ): List<FestivalOccurrence> {
        val firstDate = DayLookup.utcDate(run.from)
        val lastDate = DayLookup.utcDate(run.to)
        val starts = solar.starts(r.region, firstDate, lastDate, run.sankrantis, run.days, skip = true)
        return starts.zipWithNext().filter { it.first.month == r.month }.mapNotNull { (start, next) ->
            skipUnavailable { inMonth(def, r, run, start, next) }
        }
    }

    private fun inMonth(
        def: FestivalDef,
        r: FestivalRule.NakshatraInSolarMonth,
        run: Run,
        start: SolarMonthStart,
        next: SolarMonthStart,
    ): FestivalOccurrence? {
        val monthDays = generateSequence(start.date) { it.plus(ONE) }.takeWhile { it < next.date }.toList()
        val cands = monthDays.filter { touchedBy(r.nakshatra, it, r.kala, run) != null }
            .filter { d -> r.precededBy == null || precededInMonth(r.precededBy, d, start.date, r.kala, run) }
        val pick = when (r.pick) {
            Pick.FIRST -> cands.firstOrNull()
            Pick.LAST -> cands.lastOrNull()
            Pick.NEAREST_FULL_MOON -> nearestFullMoon(cands, monthDays, r, run)
        } ?: return null
        val window = run.ctx.kala(pick, r.kala)
        val muhurat = touchedBy(r.nakshatra, pick, r.kala, run)?.takeIf { it.start < it.end }
        val sun = DayFlag.SUN_FALLBACK in run.days.sunDay(pick).flags
        return FestivalOccurrence(def.id, pick, null, null, r.kala, window, muhurat, null, sunFlags(sun))
    }

    /** I_d: the part of a span of nakshatra [n] inside the kala of [d] ([p, p] for a point), or null. */
    private fun touchedBy(n: Int, d: LocalDate, k: Kala, run: Run): TimeRange? {
        val w = run.ctx.kala(d, k) ?: return null
        val span = run.ctx.nakshatraSpans(n, w.start - 1.days, w.end + 1.days).firstOrNull { Kalas.touches(k, w, it) }
        return when {
            span == null -> null
            Kalas.isPoint(k) -> w
            else -> Kalas.overlap(w, span)
        }
    }

    private fun precededInMonth(h: Int, d: LocalDate, start: LocalDate, k: Kala, run: Run): Boolean {
        val latest = (1..PRECEDED_SEARCH_DAYS).asSequence().map { d.minus(DatePeriod(days = it)) }
            .firstOrNull { touchedBy(h, it, k, run) != null }
        return latest != null && latest >= start
    }

    private fun nearestFullMoon(
        cands: List<LocalDate>,
        monthDays: List<LocalDate>,
        r: FestivalRule.NakshatraInSolarMonth,
        run: Run,
    ): LocalDate? {
        if (cands.isEmpty()) return null
        val begin = run.days.sunDay(monthDays.first()).sunrise
        val end = run.days.sunDay(monthDays.last()).nextSunrise
        val moons = lunar.fullMoons(begin - 2.days, end + 2.days)
        val inMonth = moons.filter { run.days.containing(it) in monthDays.first()..monthDays.last() }
        val mid = DayWindows.part(begin, end, 2, 1)
        val fm = inMonth.ifEmpty {
            val near = lunar.fullMoons(mid - FULL_MOON_REACH, mid + FULL_MOON_REACH)
            listOfNotNull(near.minByOrNull { abs((it - mid).inWholeNanoseconds) })
        }
        fun score(d: LocalDate): Long {
            val i = checkNotNull(touchedBy(r.nakshatra, d, r.kala, run))
            return fm.minOf { f ->
                when {
                    f < i.start -> (i.start - f).inWholeNanoseconds
                    f > i.end -> (f - i.end).inWholeNanoseconds
                    else -> 0L
                }
            }
        }
        return cands.minWith(compareBy<LocalDate> { score(it) }.thenBy { it })
    }

    private companion object {
        const val DECEMBER = 12
        const val LAST_DAY = 31
        const val FEBRUARY = 2
        const val MARCH = 3
        const val LEAP_DAY = 29
        const val SHUKLA_EKADASHI = 11
        const val KRISHNA_EKADASHI = 26
        const val HOUR_S = 3_600L
        const val PRECEDED_SEARCH_DAYS = 30
        val MARGIN = 40.days
        val FULL_MOON_REACH = 16.days
        val ONE = DatePeriod(days = 1)

        /** Label filter of section 3.6: masa (or kshaya masa) and the adhika policy; null = skip. */
        fun labelFlags(def: FestivalDef, masa: Masa?, lab: LunarLabel): Set<OccFlag>? {
            val viaKshaya = masa != null && masa != lab.masa && masa == lab.kshayaMasa
            val masaOk = masa == null || masa == lab.masa || viaKshaya
            val allowed = when (def.adhika) {
                AdhikaPolicy.NIJA -> !lab.adhika
                AdhikaPolicy.ADHIKA -> lab.adhika
                AdhikaPolicy.BOTH -> true
            }
            return if (!masaOk || !allowed) {
                null
            } else {
                buildSet {
                    if (viaKshaya) add(OccFlag.KSHAYA_MASA)
                    if (lab.adhika) add(OccFlag.ADHIKA_MASA)
                }
            }
        }

        fun sunFlags(sun: Boolean): Set<OccFlag> = if (sun) setOf(OccFlag.SUN_FALLBACK) else emptySet()

        /** Valid dates only: 29 February in leap years. */
        fun fixed(def: FestivalDef, r: FestivalRule.FixedGregorian, years: IntRange): List<FestivalOccurrence> =
            years.mapNotNull { y ->
                if (r.month == FEBRUARY && r.day == LEAP_DAY && !isLeap(y)) return@mapNotNull null
                FestivalOccurrence(def.id, LocalDate(y, r.month, r.day), null, null, null, null, null, null, emptySet())
            }

        fun isLeap(y: Int): Boolean =
            LocalDate(y, MARCH, 1).toEpochDays() - LocalDate(y, FEBRUARY, 1).toEpochDays() == LEAP_DAY.toLong()
    }
}
