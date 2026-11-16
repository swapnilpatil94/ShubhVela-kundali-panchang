package app.parivaar.panchang.engine.calendar

import app.parivaar.panchang.engine.jyotish.Rashi
import app.parivaar.panchang.engine.jyotish.RuleStatus
import app.parivaar.panchang.engine.panchang.DayFlag
import app.parivaar.panchang.engine.panchang.Paksha
import app.parivaar.panchang.engine.panchang.Vaar
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

// The M04 contract (docs/specs/M04.md section 2). The engine returns ids, codes, dates and instants only; every
// data class has a redacted `toString` (instants and dates with a caller's offset locate the observer), and no
// message carries a value.

/** Lunar month; `ordinal` is the ordinal of the [Rashi] the sidereal Sun enters during it (CHAITRA = MESHA). */
enum class Masa {
    CHAITRA, VAISHAKHA, JYESHTHA, ASHADHA, SHRAVANA, BHADRAPADA, ASHVINA, KARTIKA, MARGASHIRSHA, PAUSHA, MAGHA,
    PHALGUNA,
}

/** Month convention: AMANTA ends a month at new moon, PURNIMANTA at full moon. */
enum class MonthSystem { AMANTA, PURNIMANTA }

/** The six seasons, two amanta masas each from CHAITRA (`Ritu[masa.ordinal / 2]`). */
enum class Ritu { VASANTA, GRISHMA, VARSHA, SHARAD, HEMANTA, SHISHIRA }

/** Half-year by the sidereal Sun at sunrise: UTTARAYANA iff its longitude is in [270, 360) or [0, 90). */
enum class Ayana { UTTARAYANA, DAKSHINAYANA }

/** The four regional solar calendars and the sign whose sankranti starts their year. */
enum class SolarRegion(val yearStart: Rashi) {
    TAMIL(Rashi.MESHA),
    MALAYALAM(Rashi.SIMHA),
    BENGALI(Rashi.MESHA),
    ODIA(Rashi.MESHA),
}

/**
 * Time of day a festival rule tests (docs/specs/M04.md section 3.4). SUNRISE and MOONRISE are points (MOONRISE
 * may not exist); AHORATRA is the whole panchang day (nakshatra_in_solar_month only).
 */
enum class Kala { SUNRISE, ARUNODAYA, PRADOSH, NISHITA, MADHYAHNA, APARAHNA, MOONRISE, AHORATRA }

/** How a tithi touching the kala on two or more days picks one. */
enum class Tie { FIRST, SECOND, MAX_COVERAGE }

/**
 * What a tithi touching the kala on no day does: the day containing its start; the first day whose sunrise
 * it contains (udaya), else the day containing its start; or no occurrence.
 */
enum class NoneRule { CONTAINING_START, SUNRISE, SKIP }

/** Which months of an adhika (intercalary) pair a festival is kept in. */
enum class AdhikaPolicy { NIJA, ADHIKA, BOTH }

/** Which day a sankranti is kept on (docs/specs/M04.md section 3.3); CIVIL = civil date at the caller offset. */
enum class SankrantiDay { CIVIL, BEFORE_SUNSET, BEFORE_THREE_FIFTHS, BEFORE_MIDNIGHT_NEXT }

/** Ekadashi observance tradition. */
enum class Tradition { SMARTA, VAISHNAVA }

/** First, last, or nearest-to-the-month's-full-moon matching day of a solar month. */
enum class Pick { FIRST, LAST, NEAREST_FULL_MOON }

/** Facts about how a festival occurrence was found (codes, not display text). */
enum class OccFlag {
    SUN_FALLBACK, VRIDDHI_TITHI, KSHAYA_TITHI, NONE_TOUCHED, TIE_BROKEN, MOONRISE_NONE, DASHAMI_VIDDHA,
    PARANA_UNDEFINED, ADHIKA_MASA, KSHAYA_MASA, BHADRA_SHIFT, BHADRA_UNAVOIDABLE,
}

/** One bhadra (Vishti karana) avoidance step of a tithi rule (docs/specs/M04.md section 3.6), tried in order. */
sealed interface BhadraStep {
    /** Success if the kala [kala] (not AHORATRA) of the chosen day has a bhadra-free part inside the tithi. */
    data class FreeIn(val kala: Kala) : BhadraStep {
        init {
            require(kala != Kala.AHORATRA) { "ahoratra is not a bhadra kala" }
        }

        override fun toString(): String = "FreeIn(redacted)"
    }

    /** Success if the night's bhadra ends before the night midpoint and before the tithi ends. */
    data object AfterBhadraBeforeMidnight : BhadraStep {
        override fun toString(): String = "AfterBhadraBeforeMidnight(redacted)"
    }

    /** Success on the next day if the tithi holds its sunrise and lasts [minGhatis] (1..60) ghatis after it. */
    data class NextDay(val minGhatis: Int) : BhadraStep {
        init {
            require(minGhatis in 1..MAX_GHATIS) { "ghatis out of range" }
        }

        override fun toString(): String = "NextDay(redacted)"
    }
}

/** Eclipse type: the three solar kinds first, then the three lunar kinds. */
enum class EclipseKind { SOLAR_PARTIAL, SOLAR_ANNULAR, SOLAR_TOTAL, LUNAR_PENUMBRAL, LUNAR_PARTIAL, LUNAR_TOTAL }

/** Eclipse contacts: P1..P4 penumbral and U1..U4 umbral (lunar), C1..C4 (local solar). */
enum class Contact { P1, U1, U2, U3, U4, P4, C1, C2, C3, C4 }

/** A time range [start, end]; start == end is a point (the SUNRISE kala, a sankranti). `toString` is redacted. */
data class TimeRange(val start: Instant, val end: Instant) {
    init {
        require(start <= end) { "time range must not end before it starts" }
    }

    override fun toString(): String = "TimeRange(redacted)"
}

/** The sidereal Sun entering [into] at [at]. `toString` is redacted. */
data class Sankranti(val into: Rashi, val at: Instant) {
    override fun toString(): String = "Sankranti(redacted)"
}

/**
 * One lunation [newMoon, nextNewMoon) and its amanta name (docs/specs/M04.md section 3.1). `toString` is redacted.
 *
 * @property sankrantis the sankrantis with newMoon <= at < nextNewMoon, in time order (0, 1 or 2)
 * @property masa the masa of the rashi entered; for an adhika lunation the name of the following month
 * @property adhika no sankranti falls in the lunation
 * @property kshayaMasa the second rashi's masa when two sankrantis fall in it (kshaya), else null
 */
data class Lunation(
    val newMoon: Instant,
    val fullMoon: Instant,
    val nextNewMoon: Instant,
    val sankrantis: List<Sankranti>,
    val masa: Masa,
    val adhika: Boolean,
    val kshayaMasa: Masa?,
) {
    init {
        require(newMoon < fullMoon && fullMoon < nextNewMoon) { "lunation events out of order" }
        require(sankrantis.size <= MAX_SANKRANTIS) { "too many sankrantis in a lunation" }
        require(sankrantis.all { newMoon <= it.at && it.at < nextNewMoon }) { "sankranti outside the lunation" }
        require(sankrantis.zipWithNext().all { (a, b) -> a.at < b.at && b.into.ordinal == next(a.into.ordinal) }) {
            "sankrantis not consecutive"
        }
        require(adhika == sankrantis.isEmpty()) { "adhika iff no sankranti" }
        require(sankrantis.isEmpty() || masa.ordinal == sankrantis.first().into.ordinal) {
            "masa must be named by the first sankranti"
        }
        require(kshayaMasa?.ordinal == sankrantis.getOrNull(1)?.into?.ordinal) {
            "kshaya masa must be named by the second sankranti"
        }
    }

    override fun toString(): String = "Lunation(redacted)"
}

/** A lunar month label. `toString` is redacted. A kshaya label is never adhika; its lost masa follows [masa]. */
data class LunarLabel(val masa: Masa, val adhika: Boolean, val kshayaMasa: Masa?) {
    init {
        require(kshayaMasa == null || !adhika) { "a label cannot be both adhika and kshaya" }
        require(kshayaMasa == null || kshayaMasa.ordinal == next(masa.ordinal)) {
            "kshaya masa must follow the masa"
        }
    }

    override fun toString(): String = "LunarLabel(redacted)"
}

/**
 * The first civil [date] of solar [month] in [region] (docs/specs/M04.md section 3.3), from [sankranti] into
 * [month]; [cutoff] is the instant compared with the sankranti (sunset, 3/5 of daylight, night midpoint), null
 * for CIVIL and SAME_DAY. `toString` is redacted.
 */
data class SolarMonthStart(
    val region: SolarRegion,
    val month: Rashi,
    val date: LocalDate,
    val sankranti: Sankranti,
    val cutoff: Instant?,
) {
    init {
        require(sankranti.into == month) { "sankranti must enter the month" }
    }

    override fun toString(): String = "SolarMonthStart(redacted)"
}

/** Day [day] (1..32) of solar [month] in [region], which started on [monthStart]. `toString` is redacted. */
data class SolarDate(val region: SolarRegion, val month: Rashi, val day: Int, val monthStart: LocalDate) {
    init {
        require(day in 1..MAX_SOLAR_DAY) { "solar day out of range" }
    }

    override fun toString(): String = "SolarDate(redacted)"
}

/**
 * Era years of a day (docs/specs/M04.md section 3.2). `toString` is redacted.
 *
 * @property samvatsara 1..60 in the South Indian count tied to Shaka (1 = Prabhava)
 * @property samvatsaraTamil 1..60, same count, year from the Tamil Mesha month
 */
data class Samvat(
    val shaka: Int,
    val vikramChaitradi: Int,
    val vikramKartikadi: Int,
    val samvatsara: Int,
    val samvatsaraTamil: Int,
    val bangabda: Int,
    val kollam: Int,
) {
    init {
        require(samvatsara in 1..SAMVATSARAS && samvatsaraTamil in 1..SAMVATSARAS) { "samvatsara out of range" }
    }

    override fun toString(): String = "Samvat(redacted)"
}

/**
 * The calendar facts of one panchang day (docs/specs/M04.md sections 3.1-3.3). `toString` is redacted.
 *
 * @property tithiAtSunrise 1..30
 * @property solar one date per region, keyed by its own region
 * @property flags the panchang day's sun flags (M03)
 */
data class CalendarDay(
    val date: LocalDate,
    val tithiAtSunrise: Int,
    val amanta: LunarLabel,
    val purnimanta: LunarLabel,
    val samvat: Samvat,
    val ritu: Ritu,
    val ayana: Ayana,
    val solar: Map<SolarRegion, SolarDate>,
    val flags: Set<DayFlag>,
) {
    init {
        require(tithiAtSunrise in 1..TITHIS) { "tithi out of range" }
        require(solar.all { (k, v) -> k == v.region }) { "solar date keyed by another region" }
    }

    override fun toString(): String = "CalendarDay(redacted)"
}

/** A festival rule of the DSL (docs/specs/M04.md section 3.5). Every variant's `toString` is redacted. */
sealed interface FestivalRule {
    /**
     * Tithi [tithi] (1..30, at least one, no repeat) touching [kala] (not AHORATRA); [masa] null = any masa.
     * [preferNakshatra] (0..26) set = tie `{"prefer_nakshatra": n, "else": tie}`. [bhadra] steps, if any, run
     * after the day is chosen.
     */
    data class TithiAt(
        val tithi: List<Int>,
        val masa: Masa?,
        val system: MonthSystem,
        val kala: Kala,
        val tie: Tie,
        val preferNakshatra: Int?,
        val none: NoneRule,
        val bhadra: List<BhadraStep> = emptyList(),
    ) : FestivalRule {
        init {
            require(kala != Kala.AHORATRA) { "ahoratra is only for solar month rules" }
            require(tithi.isNotEmpty() && tithi.all { it in 1..TITHIS }) { "tithi out of range" }
            require(tithi.distinct().size == tithi.size) { "tithi repeated" }
            require(preferNakshatra == null || preferNakshatra in 0 until NAKSHATRAS) { "nakshatra out of range" }
        }

        override fun toString(): String = "TithiAt(redacted)"
    }

    /** Ekadashi by [tradition] (section 3.7); [masa] null = any, [paksha] null = both. */
    data class Ekadashi(val tradition: Tradition, val masa: Masa?, val system: MonthSystem, val paksha: Paksha?) :
        FestivalRule {
        override fun toString(): String = "Ekadashi(redacted)"
    }

    /** Sankranti into [into] (null = any) kept on [day]. */
    data class SankrantiRule(val into: Rashi?, val day: SankrantiDay) : FestivalRule {
        override fun toString(): String = "SankrantiRule(redacted)"
    }

    /** A fixed Gregorian [month]/[day]; the day must exist in a leap year (29 February only in leap years). */
    data class FixedGregorian(val month: Int, val day: Int) : FestivalRule {
        init {
            require(month in 1..MONTHS) { "month out of range" }
            require(day in 1..LEAP_MONTH_DAYS[month - 1]) { "day out of range" }
        }

        override fun toString(): String = "FixedGregorian(redacted)"
    }

    /**
     * The [pick] day of solar [month] of [region] on which [nakshatra] (0..26) touches [kala]; with
     * [precededBy] (0..26) set, only days whose latest earlier [precededBy] day is inside the month.
     */
    data class NakshatraInSolarMonth(
        val nakshatra: Int,
        val month: Rashi,
        val region: SolarRegion,
        val kala: Kala,
        val pick: Pick,
        val precededBy: Int? = null,
    ) : FestivalRule {
        init {
            require(nakshatra in 0 until NAKSHATRAS) { "nakshatra out of range" }
            require(precededBy == null || precededBy in 0 until NAKSHATRAS) { "nakshatra out of range" }
        }

        override fun toString(): String = "NakshatraInSolarMonth(redacted)"
    }

    /**
     * The [pick] day of lunar month [masa] in [system] on which [nakshatra] (0..26) touches [kala]
     * (docs/specs/M10.md rule 8). A day belongs to the month whose label (per [system]) holds at its sunrise;
     * the def's adhika policy and the kshaya rule of [TithiAt] select the months. JSON
     * `nakshatra_in_lunar_month`. [pick] is FIRST or LAST (NEAREST_FULL_MOON is for solar months only).
     */
    data class NakshatraInLunarMonth(
        val nakshatra: Int,
        val masa: Masa,
        val system: MonthSystem,
        val kala: Kala,
        val pick: Pick,
    ) : FestivalRule {
        init {
            require(nakshatra in 0 until NAKSHATRAS) { "nakshatra out of range" }
            require(pick != Pick.NEAREST_FULL_MOON) { "nearest full moon is for solar months only" }
        }

        override fun toString(): String = "NakshatraInLunarMonth(redacted)"
    }

    /** [inner], kept only when its date falls on [weekday]. */
    data class WeekdayTithi(val weekday: Vaar, val inner: TithiAt) : FestivalRule {
        override fun toString(): String = "WeekdayTithi(redacted)"
    }

    /** [days] (-3..3, not 0) after each occurrence of the non-relative def [to] of the same file. */
    data class Relative(val to: String, val days: Int) : FestivalRule {
        init {
            require(FESTIVAL_ID.matches(to)) { "malformed festival id" }
            require(days in -MAX_RELATIVE_DAYS..MAX_RELATIVE_DAYS && days != 0) { "relative days out of range" }
        }

        override fun toString(): String = "Relative(redacted)"
    }
}

/**
 * One festival definition. `toString` is redacted.
 *
 * @property id `[a-z0-9_]{1,40}`
 * @property nameKey `"term:fest_" + id`
 * @property regions at least one of `all, north, west, mr, gu, south, ta, ml, bn, or, te, kn`
 * @property vratRules ids of the same form as [id]
 * @property sourceId not blank
 */
data class FestivalDef(
    val id: String,
    val nameKey: String,
    val regions: Set<String>,
    val rule: FestivalRule,
    val adhika: AdhikaPolicy,
    val vratRules: List<String>,
    val status: RuleStatus,
    val sourceId: String,
) {
    init {
        require(FESTIVAL_ID.matches(id)) { "malformed festival id" }
        require(nameKey == NAME_KEY_PREFIX + id) { "name key must match the id" }
        require(regions.isNotEmpty() && FESTIVAL_REGIONS.containsAll(regions)) { "unknown region" }
        require(vratRules.all { FESTIVAL_ID.matches(it) }) { "malformed vrat rule id" }
        require(sourceId.isNotBlank()) { "blank source id" }
    }

    override fun toString(): String = "FestivalDef(redacted)"
}

/**
 * The festival definitions of one rule [file] as parsed by `FestivalRules`: ids unique, every relative rule
 * points at a non-relative def of the same file.
 */
class FestivalSet(val file: String, defs: List<FestivalDef>) {
    val defs: List<FestivalDef> = defs.toList()

    init {
        require(this.defs.map { it.id }.toSet().size == this.defs.size) { "duplicate festival id" }
        val byId = this.defs.associateBy { it.id }
        require(
            this.defs.all { d ->
                val r = d.rule
                r !is FestivalRule.Relative || byId[r.to]?.rule.let { it != null && it !is FestivalRule.Relative }
            },
        ) { "relative rule target missing" }
    }

    /** The definitions whose regions contain [r] or `all`, in file order. */
    fun forRegion(r: String): List<FestivalDef> = defs.filter { r in it.regions || ALL_REGIONS in it.regions }
}

/**
 * One festival occurrence on the civil [date] of its panchang day (docs/specs/M04.md section 3.6).
 * `toString` is redacted.
 *
 * @property label lunar label of the tithi span (null for solar and Gregorian rules)
 * @property tithi 1..30, or null
 * @property kalaWindow the kala of [date] ([p, p] for a point kala; for Ekadashi the kala of the fast day)
 * @property muhurat kala window intersected with the tithi span, when that is a non-empty interval
 * @property parana Ekadashi fast-breaking window, or null
 */
data class FestivalOccurrence(
    val id: String,
    val date: LocalDate,
    val label: LunarLabel?,
    val tithi: Int?,
    val kala: Kala?,
    val kalaWindow: TimeRange?,
    val muhurat: TimeRange?,
    val parana: TimeRange?,
    val flags: Set<OccFlag>,
) {
    init {
        require(FESTIVAL_ID.matches(id)) { "malformed festival id" }
        require(tithi == null || tithi in 1..TITHIS) { "tithi out of range" }
    }

    override fun toString(): String = "FestivalOccurrence(redacted)"
}

/**
 * A geocentric eclipse (docs/specs/M04.md section 3.9). `toString` is redacted.
 *
 * @property gammaDeg minimum angle (Moon, antisolar point) for lunar, (Moon, Sun) for solar; finite, >= 0
 * @property lunarKind, umbralMag, penumbralMag lunar only (null for solar)
 * @property contacts lunar: P1, U1..U4, P4 that exist
 */
data class GlobalEclipse(
    val solar: Boolean,
    val greatest: Instant,
    val gammaDeg: Double,
    val lunarKind: EclipseKind?,
    val umbralMag: Double?,
    val penumbralMag: Double?,
    val contacts: Map<Contact, Instant>,
) {
    init {
        require(gammaDeg.isFinite() && gammaDeg >= 0.0) { "gamma out of range" }
        if (solar) {
            require(lunarKind == null && umbralMag == null && penumbralMag == null) {
                "lunar fields on a solar eclipse"
            }
        } else {
            require(lunarKind != null && !lunarKind.isSolar()) { "lunar eclipse needs a lunar kind" }
            require(umbralMag?.isFinite() == true && penumbralMag?.isFinite() == true) { "magnitude not finite" }
            require(contacts.keys.all { it in LUNAR_CONTACTS }) { "solar contact on a lunar eclipse" }
        }
    }

    override fun toString(): String = "GlobalEclipse(redacted)"
}

/**
 * An eclipse seen from a place; never holds the place. `toString` is redacted.
 *
 * @property kind solar kinds for a solar [global]; for lunar, [global]'s lunar kind
 * @property magnitude finite: local solar magnitude, or the lunar umbral (penumbral for PENUMBRAL) magnitude
 * @property contacts solar: C1..C4 that exist; lunar: as [global]
 * @property visible first..last instant of the interval with the body at or above the horizon, or null
 * @property sutak only when [visible] is set and the kind is not LUNAR_PENUMBRAL
 */
data class LocalEclipse(
    val global: GlobalEclipse,
    val kind: EclipseKind,
    val magnitude: Double,
    val maxAt: Instant,
    val contacts: Map<Contact, Instant>,
    val visible: TimeRange?,
    val sutak: TimeRange?,
) {
    init {
        require(kind.isSolar() == global.solar) { "kind does not match the eclipse" }
        require(global.solar || kind == global.lunarKind) { "kind does not match the eclipse" }
        require(magnitude.isFinite()) { "magnitude not finite" }
        require(contacts.keys.all { (it in LUNAR_CONTACTS) != global.solar }) { "contact does not match the eclipse" }
        require(sutak == null || (visible != null && kind != EclipseKind.LUNAR_PENUMBRAL)) {
            "sutak needs a visible non-penumbral eclipse"
        }
    }

    override fun toString(): String = "LocalEclipse(redacted)"
}

/** True for the three solar kinds. */
internal fun EclipseKind.isSolar(): Boolean = ordinal <= EclipseKind.SOLAR_TOTAL.ordinal

/** Festival and vrat rule id pattern (docs/specs/M04.md section 3.5). */
internal val FESTIVAL_ID = Regex("[a-z0-9_]{1,40}")

/** Region codes of the festival DSL (docs/specs/M04.md section 3.5). */
internal val FESTIVAL_REGIONS: Set<String> =
    setOf(ALL_REGIONS, "north", "west", "mr", "gu", "south", "ta", "ml", "bn", "or", "te", "kn")

/** Prefix of a festival's name key. */
internal const val NAME_KEY_PREFIX = "term:fest_"

private const val ALL_REGIONS = "all"
private const val MAX_SANKRANTIS = 2
private const val MAX_SOLAR_DAY = 32
private const val SAMVATSARAS = 60
private const val TITHIS = 30
private const val NAKSHATRAS = 27
private const val MONTHS = 12
private const val MAX_GHATIS = 60
private const val MAX_RELATIVE_DAYS = 3
private val LEAP_MONTH_DAYS = intArrayOf(31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
private val LUNAR_CONTACTS = setOf(Contact.P1, Contact.U1, Contact.U2, Contact.U3, Contact.U4, Contact.P4)

private fun next(rashiOrMasaOrdinal: Int): Int = (rashiOrMasaOrdinal + 1) % MONTHS
