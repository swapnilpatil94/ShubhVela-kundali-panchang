package app.parivaar.panchang.engine.calendar

import app.parivaar.panchang.engine.CalcSettings
import app.parivaar.panchang.engine.Place
import app.parivaar.panchang.engine.astro.Body
import app.parivaar.panchang.engine.astro.Ephemeris
import app.parivaar.panchang.engine.astro.TimeScales
import app.parivaar.panchang.engine.astro.TimeTables
import app.parivaar.panchang.engine.astro.positionOnly
import app.parivaar.panchang.engine.calendar.internal.DayLookup
import app.parivaar.panchang.engine.jyotish.Ayanamsa
import app.parivaar.panchang.engine.jyotish.Rashi
import app.parivaar.panchang.engine.jyotish.RuleStatus
import app.parivaar.panchang.engine.panchang.LimbKind
import app.parivaar.panchang.engine.panchang.requireUtcOffset
import kotlin.math.floor
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

// Family dates (docs/specs/M10.md rule 8, MASTER Part 3 section 7): janma facts of a birth (or passing) and
// the yearly tithi birthday, nakshatra birthday and shraddha as ordinary festival definitions evaluated by
// FestivalEngine. Every value type has a redacted `toString`: birth facts are personal data.

/** The three yearly family dates. */
enum class FamilyDateKind { TITHI_BIRTHDAY, NAKSHATRA_BIRTHDAY, SHRADDHA }

/** Which month a nakshatra birthday is kept in: the solar month of the birth Sun, or the lunar month of birth. */
enum class NakshatraMonthBasis { SOLAR, LUNAR }

/** The family's calendar choices: lunar month system, nakshatra basis and the solar calendar region. */
data class FamilyDatePrefs(
    val monthSystem: MonthSystem,
    val basis: NakshatraMonthBasis,
    val solarRegion: SolarRegion,
) {
    override fun toString(): String = "FamilyDatePrefs(redacted)"
}

/**
 * The calendar facts of one instant: tithi 1..30, amanta and purnimanta labels, nakshatra 0..26 and the
 * sidereal rashi of the Sun. Geocentric: no place. `toString` is redacted.
 */
data class JanmaFacts(
    val tithi: Int,
    val amanta: LunarLabel,
    val purnimanta: LunarLabel,
    val nakshatra: Int,
    val sunRashi: Rashi,
) {
    init {
        require(tithi in LimbKind.TITHI.firstIndex..LimbKind.TITHI.lastIndex) { "tithi out of range" }
        require(nakshatra in 0 until LimbKind.NAKSHATRA.divisions) { "nakshatra out of range" }
    }

    /** The label of [system]. */
    fun label(system: MonthSystem): LunarLabel = if (system == MonthSystem.AMANTA) amanta else purnimanta

    override fun toString(): String = "JanmaFacts(redacted)"
}

/** Janma facts of a civil day whose clock time is unknown (docs/specs/M10.md rule 2). */
sealed interface JanmaResult {
    /** One tithi and one nakshatra prevail over the whole civil day; [facts] are taken at its sunrise. */
    data class Exact(val facts: JanmaFacts) : JanmaResult {
        override fun toString(): String = "Exact(redacted)"
    }

    /**
     * The tithis (1..30) and nakshatras (0..26) that prevail during the civil day, in time order (at least one
     * list has two or more); the user picks one of each. [atSunrise] are the facts at the day's sunrise.
     */
    data class Ambiguous(val tithis: List<Int>, val nakshatras: List<Int>, val atSunrise: JanmaFacts) : JanmaResult {
        init {
            require(tithis.isNotEmpty() && nakshatras.isNotEmpty()) { "empty day" }
            require(tithis.size > 1 || nakshatras.size > 1) { "an unambiguous day is Exact" }
        }

        override fun toString(): String = "Ambiguous(redacted)"
    }
}

/** The facts at the day's sunrise of either result (shraddha with an unknown time of passing uses these). */
fun JanmaResult.factsAtSunrise(): JanmaFacts = when (this) {
    is JanmaResult.Exact -> facts
    is JanmaResult.Ambiguous -> atSunrise
}

/**
 * The defaults of docs/specs/M10.md rule 8 in one table. Every value is VERIFY (TODO(owner): pandit sign-off);
 * changing one changes every family date.
 */
object FamilyDateDefaults {
    /** Tithi birthday: the tithi at sunrise. */
    val TITHI_KALA: Kala = Kala.SUNRISE

    /** Nakshatra birthday: the nakshatra at sunrise. */
    val NAKSHATRA_KALA: Kala = Kala.SUNRISE

    /** Shraddha: the tithi touching aparahna. */
    val SHRADDHA_KALA: Kala = Kala.APARAHNA

    /** Two days touched: the first. */
    val TIE: Tie = Tie.FIRST

    /** No day touched (kshaya tithi): the day containing the tithi's start. */
    val NONE: NoneRule = NoneRule.CONTAINING_START

    /** Two matching nakshatra days in the month: the first. No matching day: no occurrence (no guessed day). */
    val NAKSHATRA_PICK: Pick = Pick.FIRST

    /** Kept in the nija month only; a birth in an adhika month is kept in the nija month of the same name. */
    val ADHIKA: AdhikaPolicy = AdhikaPolicy.NIJA

    /** Rule status of every family definition. */
    val STATUS: RuleStatus = RuleStatus.VERIFY

    /** Source id of every family definition (a constant: never a name or profile id). */
    const val SOURCE_ID: String = "family_defaults_m10"
}

/**
 * Janma facts and yearly family dates (docs/specs/M10.md). Dates come from [FestivalEngine] with the family
 * definitions of [def]; results never hold the place. Holds the caches of its calendars: not thread-safe,
 * like [FestivalEngine].
 */
class FamilyDateFinder(eph: Ephemeris, time: TimeTables = TimeTables.DEFAULT) {
    private val festivals = FestivalEngine(eph, time)
    private val lunar = festivals.lunar

    /** The facts at [at] (tithi and nakshatra of the M03 spans containing it; labels of its lunation). */
    fun janma(at: Instant, s: CalcSettings = CalcSettings()): JanmaFacts {
        val tithi = lunar.limbs.spanAt(LimbKind.TITHI, at, s).index
        val l = lunar.lunationAt(at, s)
        return JanmaFacts(
            tithi = tithi,
            amanta = lunar.label(l, tithi, MonthSystem.AMANTA, s),
            purnimanta = lunar.label(l, tithi, MonthSystem.PURNIMANTA, s),
            nakshatra = lunar.limbs.spanAt(LimbKind.NAKSHATRA, at, s).index,
            sunRashi = sunRashi(at, s),
        )
    }

    /**
     * The facts of civil [date] at [utcOffsetSeconds] (range-checked) for an unknown clock time: [JanmaResult.Exact]
     * if one tithi and one nakshatra prevail from local midnight to the next midnight, else
     * [JanmaResult.Ambiguous]. Facts are taken at the day's sunrise at [place] (local noon if the sun day puts
     * it outside the civil day). Throws `PanchangUnavailableException` (NO_SUN_DAY) like M03.
     */
    fun janmaOfDay(
        date: LocalDate,
        utcOffsetSeconds: Int,
        place: Place,
        s: CalcSettings = CalcSettings(),
    ): JanmaResult {
        requireUtcOffset(utcOffsetSeconds)
        val midnight = Instant.fromEpochSeconds(date.toEpochDays() * SECONDS_PER_DAY - utcOffsetSeconds)
        val next = midnight + 1.days
        val tithis = lunar.limbs.spans(LimbKind.TITHI, midnight, next, s).map { it.index }
        val nakshatras = lunar.limbs.spans(LimbKind.NAKSHATRA, midnight, next, s).map { it.index }
        val sunrise = DayLookup.of(lunar.riseSet, place, s) { utcOffsetSeconds }.sunDay(date).sunrise
        val probe = if (sunrise >= midnight && sunrise < next) sunrise else midnight + NOON
        val facts = janma(probe, s)
        return if (tithis.size == 1 && nakshatras.size == 1) {
            JanmaResult.Exact(facts)
        } else {
            JanmaResult.Ambiguous(tithis, nakshatras, facts)
        }
    }

    /**
     * The festival definition of [kind] for [facts] under [p] (docs/specs/M10.md rule 8, [FamilyDateDefaults]).
     * For SHRADDHA, [facts] are those of the passing (time known: [janma] of the instant; unknown:
     * [factsAtSunrise] of [janmaOfDay] at the home place). [id] must be `fam_<digits>`: never a name.
     */
    fun def(kind: FamilyDateKind, facts: JanmaFacts, p: FamilyDatePrefs, id: String): FestivalDef {
        require(FAMILY_ID.matches(id)) { "family date id must be fam_<n>" }
        val d = FamilyDateDefaults
        val masa = facts.label(p.monthSystem).masa
        val rule = when (kind) {
            FamilyDateKind.TITHI_BIRTHDAY ->
                FestivalRule.TithiAt(listOf(facts.tithi), masa, p.monthSystem, d.TITHI_KALA, d.TIE, null, d.NONE)
            FamilyDateKind.SHRADDHA ->
                FestivalRule.TithiAt(listOf(facts.tithi), masa, p.monthSystem, d.SHRADDHA_KALA, d.TIE, null, d.NONE)
            FamilyDateKind.NAKSHATRA_BIRTHDAY -> when (p.basis) {
                NakshatraMonthBasis.SOLAR -> FestivalRule.NakshatraInSolarMonth(
                    facts.nakshatra, facts.sunRashi, p.solarRegion, d.NAKSHATRA_KALA, d.NAKSHATRA_PICK,
                )
                NakshatraMonthBasis.LUNAR -> FestivalRule.NakshatraInLunarMonth(
                    facts.nakshatra, masa, p.monthSystem, d.NAKSHATRA_KALA, d.NAKSHATRA_PICK,
                )
            }
        }
        return FestivalDef(id, NAME_KEY_PREFIX + id, setOf(ALL), rule, d.ADHIKA, emptyList(), d.STATUS, d.SOURCE_ID)
    }

    /** [FestivalEngine.occurrences] of [defs] dated [first]..[last] at [place]; a month with no match has none. */
    fun occurrences(
        defs: List<FestivalDef>,
        first: LocalDate,
        last: LocalDate,
        place: Place,
        s: CalcSettings = CalcSettings(),
        utcOffsetSeconds: (LocalDate) -> Int,
    ): List<FestivalOccurrence> = festivals.occurrences(defs, first, last, place, s, utcOffsetSeconds)

    private fun sunRashi(at: Instant, s: CalcSettings): Rashi {
        val tt = TimeScales.utcToTt(at, lunar.timeTables)
        val lon = Ayanamsa.siderealDeg(lunar.ephemeris.positionOnly(Body.SUN, tt).lonDeg, s.ayanamsa, tt)
        return Rashi.entries[floor(lon / DEG_PER_RASHI).toInt().mod(Rashi.entries.size)]
    }

    private companion object {
        const val SECONDS_PER_DAY = 86_400L
        const val DEG_PER_RASHI = 30.0
        const val ALL = "all"
        val NOON = 12.hours
        val FAMILY_ID = Regex("fam_[0-9]{1,36}")
    }
}
