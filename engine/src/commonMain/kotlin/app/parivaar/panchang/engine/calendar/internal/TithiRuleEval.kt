package app.parivaar.panchang.engine.calendar.internal

import app.parivaar.panchang.engine.calendar.BhadraStep
import app.parivaar.panchang.engine.calendar.FestivalRule
import app.parivaar.panchang.engine.calendar.Kala
import app.parivaar.panchang.engine.calendar.NoneRule
import app.parivaar.panchang.engine.calendar.OccFlag
import app.parivaar.panchang.engine.calendar.Tie
import app.parivaar.panchang.engine.calendar.TimeRange
import app.parivaar.panchang.engine.panchang.DayFlag
import app.parivaar.panchang.engine.panchang.internal.DayWindows
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** The day chosen for a tithi rule, its kala window and muhurat, and the flags of the choice. */
internal data class TithiChoice(
    val date: LocalDate,
    val kalaWindow: TimeRange?,
    val muhurat: TimeRange?,
    val flags: Set<OccFlag>,
) {
    override fun toString(): String = "TithiChoice(redacted)"
}

/** Day choice of a `tithi_at` rule for one tithi span (docs/specs/M04.md section 3.6, with bhadra steps). */
internal object TithiRuleEval {
    private val ONE = DatePeriod(days = 1)
    private val GHATI = 1_440.seconds

    /** The day of span [x] under [r] in [ctx], or null (SKIP with no day touched). */
    fun choose(x: TimeRange, r: FestivalRule.TithiAt, ctx: EvalContext): TithiChoice? {
        val days = ctx.days
        val from = days.containing(x.start).minus(ONE)
        val to = days.containing(x.end).plus(ONE)
        val all = generateSequence(from) { it.plus(ONE) }.takeWhile { it <= to }.toList()
        val cov = all.associateWith { Kalas.coverage(r.kala, ctx.kala(it, r.kala), x) }
        val touched = all.filter { cov.getValue(it) > 0L }
        val flags = baseFlags(all, touched, r.kala, ctx)
        val chosen = when {
            touched.size == 1 -> touched[0]
            touched.size >= 2 -> {
                flags += OccFlag.TIE_BROKEN
                if (r.kala == Kala.SUNRISE) flags += OccFlag.VRIDDHI_TITHI
                breakTie(touched, cov, r, ctx)
            }
            else -> {
                flags += OccFlag.NONE_TOUCHED
                if (r.kala == Kala.SUNRISE) flags += OccFlag.KSHAYA_TITHI
                untouched(all, x, r.none, days) ?: return null
            }
        }
        val base = choice(chosen, r.kala, x, ctx, flags)
        return if (r.bhadra.isEmpty()) base else bhadra(base, x, r, ctx)
    }

    private fun baseFlags(
        all: List<LocalDate>,
        touched: List<LocalDate>,
        kala: Kala,
        ctx: EvalContext,
    ): MutableSet<OccFlag> {
        val flags = mutableSetOf<OccFlag>()
        if (kala == Kala.MOONRISE && all.any { ctx.kala(it, Kala.MOONRISE) == null }) {
            flags += OccFlag.MOONRISE_NONE
        }
        if (touched.any { DayFlag.SUN_FALLBACK in ctx.days.sunDay(it).flags }) flags += OccFlag.SUN_FALLBACK
        return flags
    }

    /** The none rule: the day containing the start, the first udaya day, or null (SKIP). */
    private fun untouched(all: List<LocalDate>, x: TimeRange, none: NoneRule, days: DayLookup): LocalDate? =
        when (none) {
            NoneRule.CONTAINING_START -> days.containing(x.start)
            NoneRule.SUNRISE -> all.firstOrNull { days.sunDay(it).sunrise in x } ?: days.containing(x.start)
            NoneRule.SKIP -> null
        }

    private fun choice(d: LocalDate, k: Kala, x: TimeRange, ctx: EvalContext, flags: Set<OccFlag>): TithiChoice {
        val w = ctx.kala(d, k)
        val muhurat = if (w == null || Kalas.isPoint(k)) null else Kalas.overlap(w, x)
        return TithiChoice(d, w, muhurat, flags)
    }

    private fun breakTie(
        c: List<LocalDate>,
        cov: Map<LocalDate, Long>,
        r: FestivalRule.TithiAt,
        ctx: EvalContext,
    ): LocalDate {
        var pool = c
        val n = r.preferNakshatra
        if (n != null) {
            val with = c.filter { d ->
                val w = ctx.kala(d, r.kala)
                val probe = w ?: return@filter false
                val lo = probe.start - 1.days
                ctx.nakshatraSpans(n, lo, probe.end + 1.days).any { Kalas.touches(r.kala, w, it) }
            }
            if (with.size == 1) return with[0]
            if (with.isNotEmpty()) pool = with
        }
        return when (r.tie) {
            Tie.FIRST -> pool[0]
            Tie.SECOND -> pool[1]
            Tie.MAX_COVERAGE -> pool.maxWith(compareBy<LocalDate> { cov.getValue(it) }.thenByDescending { it })
        }
    }

    /** Bhadra steps after the base day (section 3.6); the first step that succeeds wins. */
    private fun bhadra(base: TithiChoice, x: TimeRange, r: FestivalRule.TithiAt, ctx: EvalContext): TithiChoice {
        val v = ctx.vishti(x.start - 1.days, x.end + 1.days)
        for ((i, step) in r.bhadra.withIndex()) {
            val hit = tryStep(step, base.date, x, v, r.kala, ctx) ?: continue
            val flags = if (i == 0) base.flags else base.flags + OccFlag.BHADRA_SHIFT
            return TithiChoice(hit.first, ctx.kala(hit.first, r.kala), hit.second, flags)
        }
        return base.copy(muhurat = null, flags = base.flags + OccFlag.BHADRA_UNAVOIDABLE)
    }

    private fun tryStep(
        step: BhadraStep,
        d: LocalDate,
        x: TimeRange,
        v: List<TimeRange>,
        kala: Kala,
        ctx: EvalContext,
    ): Pair<LocalDate, TimeRange>? = when (step) {
        is BhadraStep.FreeIn ->
            ctx.kala(d, step.kala)?.let { Kalas.overlap(it, x) }?.let { free(it, v).firstOrNull() }?.let { d to it }
        BhadraStep.AfterBhadraBeforeMidnight -> afterBhadra(d, x, v, ctx)
        is BhadraStep.NextDay -> nextDay(d, x, step.minGhatis, kala, ctx)
    }

    private fun afterBhadra(
        d: LocalDate,
        x: TimeRange,
        v: List<TimeRange>,
        ctx: EvalContext,
    ): Pair<LocalDate, TimeRange>? {
        val sd = ctx.days.sunDay(d)
        val night = TimeRange(sd.sunset, sd.nextSunrise)
        val b = v.firstOrNull { Kalas.overlap(it, night) != null }?.end ?: return null
        val mid = DayWindows.part(sd.sunset, sd.nextSunrise, 2, 1)
        return if (b < mid && b < x.end) d to TimeRange(b, minOf(mid, x.end)) else null
    }

    private fun nextDay(
        d: LocalDate,
        x: TimeRange,
        ghatis: Int,
        kala: Kala,
        ctx: EvalContext,
    ): Pair<LocalDate, TimeRange>? {
        val n = d.plus(ONE)
        val sn = ctx.days.sunDay(n)
        if (sn.sunrise < x.start || x.end - sn.sunrise < GHATI * ghatis) return null
        val m = if (kala == Kala.MADHYAHNA || kala == Kala.APARAHNA) {
            TimeRange(sn.sunrise, minOf(x.end, sn.sunset))
        } else {
            ctx.kala(n, kala)
        }
        return m?.let { n to it }
    }

    /** The maximal pieces of [i] outside every range of [v], in time order (positive length only). */
    fun free(i: TimeRange, v: List<TimeRange>): List<TimeRange> {
        var pieces = listOf(i)
        for (b in v.sortedBy { it.start }) {
            pieces = pieces.flatMap { p ->
                if (Kalas.overlap(p, b) == null) {
                    listOf(p)
                } else {
                    listOfNotNull(
                        if (p.start < b.start) TimeRange(p.start, b.start) else null,
                        if (b.end < p.end) TimeRange(b.end, p.end) else null,
                    )
                }
            }
        }
        return pieces
    }
}
