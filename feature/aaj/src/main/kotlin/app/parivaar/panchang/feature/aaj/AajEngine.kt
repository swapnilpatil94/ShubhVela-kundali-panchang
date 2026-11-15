package app.parivaar.panchang.feature.aaj

import app.parivaar.panchang.core.model.profile.RecordId
import app.parivaar.panchang.engine.CalcSettings
import app.parivaar.panchang.engine.Place
import app.parivaar.panchang.engine.astro.Body
import app.parivaar.panchang.engine.astro.Ephemeris
import app.parivaar.panchang.engine.sky.BodyLight
import app.parivaar.panchang.engine.sky.SkyGeometry
import app.parivaar.panchang.engine.calendar.CalendarCalculator
import app.parivaar.panchang.engine.calendar.CalendarDay
import app.parivaar.panchang.engine.calendar.EclipseSearch
import app.parivaar.panchang.engine.calendar.FestivalEngine
import app.parivaar.panchang.engine.calendar.FestivalOccurrence
import app.parivaar.panchang.engine.calendar.FestivalRule
import app.parivaar.panchang.engine.calendar.FestivalSet
import app.parivaar.panchang.engine.calendar.LocalEclipse
import app.parivaar.panchang.engine.calendar.Tradition
import app.parivaar.panchang.engine.jyotish.Rashi
import app.parivaar.panchang.engine.panchang.LimbKind
import app.parivaar.panchang.engine.panchang.LimbSpan
import app.parivaar.panchang.engine.panchang.PanchangCalculator
import app.parivaar.panchang.engine.panchang.PanchangDay
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/** Thin adapter over the engine: no math here. Calls are serial (the engine calculators are not thread-safe). */
interface AajEngine {
    fun days(first: LocalDate, count: Int, place: Place, s: CalcSettings, off: (LocalDate) -> Int): List<PanchangDay>
    fun calendar(date: LocalDate, offsetSeconds: Int, place: Place, s: CalcSettings): CalendarDay
    fun festivals(
        date: LocalDate, place: Place, s: CalcSettings, region: String, ekadashi: Tradition, off: (LocalDate) -> Int,
    ): List<FestivalOccurrence>

    /** The eclipse of the civil day visible from [place], or null. */
    fun eclipse(date: LocalDate, offsetSeconds: Int, place: Place, s: CalcSettings): LocalEclipse?

    /** Limb spans over the civil day (used when the Sun has no rise or set). */
    fun limbSpans(date: LocalDate, offsetSeconds: Int, place: Place, s: CalcSettings): Map<LimbKind, List<LimbSpan>>

    /** Lighting geometry of the Moon or a planet for the sky header's hora planet; null when unavailable. */
    fun skyLight(body: Body, at: Instant, place: Place): BodyLight? = null
}

class EngineAajEngine(
    private val panchang: PanchangCalculator,
    private val calendar: CalendarCalculator,
    private val festivals: FestivalEngine,
    private val eclipses: EclipseSearch,
    private val festivalSets: List<FestivalSet>,
    private val ephemeris: Ephemeris? = null,
) : AajEngine {
    @Synchronized
    override fun skyLight(body: Body, at: Instant, place: Place): BodyLight? =
        ephemeris?.let { SkyGeometry.light(it, body, at, place) }

    @Synchronized
    override fun days(first: LocalDate, count: Int, place: Place, s: CalcSettings, off: (LocalDate) -> Int) =
        PpPerf.time("engine.days(run)") { panchang.days(first, count, place, s, off) }

    @Synchronized
    override fun calendar(date: LocalDate, offsetSeconds: Int, place: Place, s: CalcSettings) =
        PpPerf.time("engine.calendar(run)") { calendar.day(date, offsetSeconds, place, s) }

    @Synchronized
    override fun festivals(
        date: LocalDate, place: Place, s: CalcSettings, region: String, ekadashi: Tradition, off: (LocalDate) -> Int,
    ): List<FestivalOccurrence> = PpPerf.time("engine.festivals(run)") { festivalSets.flatMap { set ->
        val defs = set.forRegion(region).filter { d ->
            val r = d.rule
            r !is FestivalRule.Ekadashi || r.tradition == ekadashi
        }
        festivals.occurrences(defs, date, date, place, s, off)
    }.distinctBy { it.id to it.date }.sortedBy { it.id } }

    @Synchronized
    override fun eclipse(date: LocalDate, offsetSeconds: Int, place: Place, s: CalcSettings): LocalEclipse? {
        val (from, to) = bounds(date, offsetSeconds)
        return PpPerf.time("engine.eclipse(run)") {
            eclipses.global(from, to).firstNotNullOfOrNull {
                eclipses.local(it, place, s)?.takeIf { l -> l.visible != null }
            }
        }
    }

    @Synchronized
    override fun limbSpans(
        date: LocalDate, offsetSeconds: Int, place: Place, s: CalcSettings,
    ): Map<LimbKind, List<LimbSpan>> {
        val (from, to) = bounds(date, offsetSeconds)
        return LimbKind.entries.associateWith { panchang.limbs.spans(it, from, to, s) }
    }

    private fun bounds(date: LocalDate, offsetSeconds: Int): Pair<Instant, Instant> {
        val start = Instant.fromEpochSeconds(date.toEpochDays() * SECONDS_PER_DAY - offsetSeconds)
        return start to Instant.fromEpochSeconds(start.epochSeconds + SECONDS_PER_DAY)
    }

    private companion object { const val SECONDS_PER_DAY = 86_400L }
}

/** Natal Moon of the self profile (nakshatra 0..26, rashi), or null without birth data. */
fun interface NatalMoonSource {
    suspend fun natal(profileId: RecordId): Pair<Int, Rashi>?
}
