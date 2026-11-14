package app.parivaar.panchang.engine.panchang

import app.parivaar.panchang.engine.astro.BackendId
import app.parivaar.panchang.engine.jyotish.Graha
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

// The M03 contract (docs/specs/M03.md section 2). The engine returns ids, codes and instants only; every
// type that can carry a day instant (and so, with the caller's offset, a place) has a redacted `toString`,
// and no message carries a value.

/** Which day the RAHU_KAAL, YAMAGANDA and GULIKA parts divide (docs/specs/M03.md section 3.3). */
enum class TimingStyle {
    /** Sunrise to sunset of the place. */
    ACTUAL_SUNRISE,

    /** A fixed 06:00 to 18:00 local day, as printed almanacs do. */
    PRINTED_6AM,
}

/** Lunar fortnight: tithi 1..15 SHUKLA (waxing), 16..30 KRISHNA (waning). */
enum class Paksha { SHUKLA, KRISHNA }

/** Compass direction (dishashool). */
enum class Direction { EAST, SOUTH, WEST, NORTH }

/** Weekday of the civil date, with its lord. */
enum class Vaar(val lord: Graha) {
    SUNDAY(Graha.SUN),
    MONDAY(Graha.MOON),
    TUESDAY(Graha.MARS),
    WEDNESDAY(Graha.MERCURY),
    THURSDAY(Graha.JUPITER),
    FRIDAY(Graha.VENUS),
    SATURDAY(Graha.SATURN),
    ;

    companion object {
        private const val DAYS_PER_WEEK = 7

        /** 1970-01-01 (epoch day 0) was a Thursday: its index from SUNDAY. */
        private const val EPOCH_WEEKDAY = 4

        /** The weekday of the civil date [d] (proleptic Gregorian). */
        fun of(d: LocalDate): Vaar = entries[(d.toEpochDays() + EPOCH_WEEKDAY).mod(DAYS_PER_WEEK)]
    }
}

/**
 * A panchang limb and the number of equal arcs its function is divided into (docs/specs/M03.md section 3.1).
 * Index convention: TITHI 1..30 and KARANA 1..60; NAKSHATRA, YOGA and MOON_RASHI 0-based (MOON_RASHI 0 = MESHA).
 */
@Suppress("MagicNumber") // each value is the definition of the limb's division
enum class LimbKind(val divisions: Int) {
    TITHI(30),
    KARANA(60),
    NAKSHATRA(27),
    YOGA(27),
    MOON_RASHI(12),
    ;

    /** The smallest index of this kind: 1 for TITHI and KARANA, else 0. */
    val firstIndex: Int get() = if (this == TITHI || this == KARANA) 1 else 0

    /** The largest index of this kind. */
    val lastIndex: Int get() = firstIndex + divisions - 1
}

/** The eleven karanas (MASTER Part 3 section 6). */
enum class Karana {
    KIMSTUGHNA, BAVA, BALAVA, KAULAVA, TAITILA, GARA, VANIJA, VISHTI, SHAKUNI, CHATUSHPADA, NAGA;

    companion object {
        private const val FIRST_MOVABLE = 2
        private const val LAST_MOVABLE = 57
        private const val MOVABLE_COUNT = 7
        private const val SHAKUNI_NUMBER = 58
        private const val CHATUSHPADA_NUMBER = 59
        private const val NAGA_NUMBER = 60
        private val MOVABLE = listOf(BAVA, BALAVA, KAULAVA, TAITILA, GARA, VANIJA, VISHTI)

        /**
         * The karana of karana number [k] in 1..60: 1 KIMSTUGHNA; 2..57 the seven movable karanas in turn;
         * 58 SHAKUNI; 59 CHATUSHPADA; 60 NAGA. Throws [IllegalArgumentException] outside 1..60.
         */
        fun ofNumber(k: Int): Karana = when (k) {
            1 -> KIMSTUGHNA
            in FIRST_MOVABLE..LAST_MOVABLE -> MOVABLE[(k - FIRST_MOVABLE) % MOVABLE_COUNT]
            SHAKUNI_NUMBER -> SHAKUNI
            CHATUSHPADA_NUMBER -> CHATUSHPADA
            NAGA_NUMBER -> NAGA
            else -> throw IllegalArgumentException("karana number out of range")
        }
    }
}

/** The seven choghadiyas, in cycle order, with their lords. Traditional ids, not display text. */
enum class Choghadiya(val lord: Graha) {
    UDVEG(Graha.SUN),
    CHAR(Graha.VENUS),
    LABH(Graha.MERCURY),
    AMRIT(Graha.MOON),
    KAAL(Graha.SATURN),
    SHUBH(Graha.JUPITER),
    ROG(Graha.MARS),
}

/** Kinds of time window of a panchang day (docs/specs/M03.md section 3.3). */
enum class WindowKind {
    RAHU_KAAL, YAMAGANDA, GULIKA, ABHIJIT, BRAHMA_MUHURTA, DURMUHURTA, VARJYAM, AMRIT_KAAL, BHADRA, PANCHAK,
    CHOGHADIYA, HORA, GOWRI,
}

/** Facts about how a day's sun events were found (docs/specs/M03.md section 3.2). */
enum class DayFlag {
    /** At least one of sunrise, sunset, nextSunrise comes from the high-latitude fallback. */
    SUN_FALLBACK,

    /** The civil date holds a second sunrise after the day's sunset (rise times crossing local midnight). */
    EXTRA_SUNRISE,
}

/** Why no panchang day exists. */
enum class PanchangError {
    /** No sunrise < sunset < nextSunrise could be found for the civil date. */
    NO_SUN_DAY,
}

/** No panchang day for the request; the message is the [code] only, never a date, instant or place. */
class PanchangUnavailableException(val code: PanchangError) : IllegalStateException(code.name)

/**
 * One limb span [start, end) (docs/specs/M03.md sections 3.1 and 3.2). `toString` is redacted.
 *
 * @property index TITHI 1..30, KARANA 1..60, NAKSHATRA and YOGA 0..26, MOON_RASHI 0..11
 * @property kshaya the span contains neither the day's sunrise nor its next sunrise
 * @property adhika the span contains both
 */
data class LimbSpan(
    val kind: LimbKind,
    val index: Int,
    val start: Instant,
    val end: Instant,
    val kshaya: Boolean = false,
    val adhika: Boolean = false,
) {
    init {
        require(index in kind.firstIndex..kind.lastIndex) { "limb index out of range" }
        require(start < end) { "limb span must end after it starts" }
        require(!(kshaya && adhika)) { "a limb span cannot be both kshaya and adhika" }
    }

    /** True iff [start] <= [at] < [end]. */
    operator fun contains(at: Instant): Boolean = start <= at && at < end

    override fun toString(): String = "LimbSpan(redacted)"
}

/**
 * One time window [start, end] (docs/specs/M03.md section 3.3). `toString` is redacted.
 *
 * @property part day part, muhurta, hora (1..24), karana number or nakshatra, by [kind]; 0 for PANCHAK
 * @property code an id (choghadiya, Gowri id or hora lord), or null
 * @property night the window comes from the night (sunset, nextSunrise)
 */
data class Window(
    val kind: WindowKind,
    val part: Int,
    val code: String?,
    val night: Boolean,
    val start: Instant,
    val end: Instant,
) {
    init {
        require(part >= 0) { "window part must not be negative" }
        require(start < end) { "window must end after it starts" }
    }

    override fun toString(): String = "Window(redacted)"
}

/** Dishashool: the direction and its tradition id (an id, not display text, not a remedy offer). */
data class Shool(val direction: Direction, val remedyId: String)

/** A durmuhurta slot: muhurta 1..15 of the day (sunrise to sunset) or of the night (sunset to next sunrise). */
data class DurmuhurtaSlot(val night: Boolean, val muhurta: Int) {
    init {
        require(muhurta in 1..MUHURTAS) { "muhurta out of range" }
    }

    private companion object {
        const val MUHURTAS = 15
    }
}

/**
 * Eight ids per weekday for the day and the night halves (choghadiya, Gowri). Immutable copies of the input.
 * [night] covers all seven weekdays; [day] is empty (choghadiya: the day rule is in `WeekdayTables`) or
 * covers all seven. Every list has eight ids matching `[A-Z_]+`.
 */
class EightPartTable(day: Map<Vaar, List<String>>, night: Map<Vaar, List<String>>) {
    val day: Map<Vaar, List<String>> = copyOf(day)
    val night: Map<Vaar, List<String>> = copyOf(night)

    init {
        require(this.night.keys.size == Vaar.entries.size) { "night table must cover every weekday" }
        require(this.day.isEmpty() || this.day.keys.size == Vaar.entries.size) {
            "day table must be empty or cover every weekday"
        }
        val rows = this.day.values + this.night.values
        require(rows.all { it.size == PARTS }) { "every row has eight parts" }
        require(rows.all { row -> row.all { ID.matches(it) } }) { "malformed id" }
    }

    private companion object {
        const val PARTS = 8
        val ID = Regex("[A-Z_]+")

        fun copyOf(m: Map<Vaar, List<String>>): Map<Vaar, List<String>> = m.mapValues { it.value.toList() }
    }
}

/**
 * Varjyam and amrit kaal ghati offsets from the start of each nakshatra (docs/specs/M03.md section 3.3):
 * 27 rows each, in nakshatra order; each offset is finite and in [0, 56]. Immutable copies of the input.
 */
class VarjyamTable(varjyamGhati: List<List<Double>>, amritGhati: List<List<Double>>) {
    val varjyamGhati: List<List<Double>> = varjyamGhati.map { it.toList() }
    val amritGhati: List<List<Double>> = amritGhati.map { it.toList() }

    init {
        require(this.varjyamGhati.size == NAKSHATRAS && this.amritGhati.size == NAKSHATRAS) {
            "a varjyam table has one row per nakshatra"
        }
        require((this.varjyamGhati + this.amritGhati).all { row -> row.all { it in 0.0..MAX_GHATI } }) {
            "ghati offset out of range"
        }
    }

    private companion object {
        const val NAKSHATRAS = 27
        const val MAX_GHATI = 56.0
    }
}

/**
 * Rule tables for the day windows (parsed by `PanchangRules`); immutable. [durmuhurta] covers every weekday;
 * an absent [gowri] or [varjyam] makes those windows unavailable.
 */
class PanchangTables(
    durmuhurta: Map<Vaar, List<DurmuhurtaSlot>>,
    val choghadiya: EightPartTable,
    val gowri: EightPartTable?,
    val varjyam: VarjyamTable?,
) {
    val durmuhurta: Map<Vaar, List<DurmuhurtaSlot>> = durmuhurta.mapValues { it.value.toList() }

    init {
        require(this.durmuhurta.keys.size == Vaar.entries.size) { "durmuhurta table must cover every weekday" }
    }
}

/**
 * One panchang day [sunrise, nextSunrise) of a civil date (docs/specs/M03.md section 3.2). Day instants with
 * the offset locate the observer, so `toString` is redacted.
 *
 * @property utcOffsetSeconds the caller's offset in force at local noon, in [-43,200, 50,400]
 * @property vaar weekday of [date]
 * @property backend the ephemeris backend that served sunrise, when known
 * @property limbs every span of each kind overlapping [sunrise, nextSunrise), in time order
 * @property windows sorted by (start, kind ordinal, part)
 * @property unavailable window kinds that could not be computed (missing tables)
 */
data class PanchangDay(
    val date: LocalDate,
    val utcOffsetSeconds: Int,
    val vaar: Vaar,
    val sunrise: Instant,
    val sunset: Instant,
    val nextSunrise: Instant,
    val flags: Set<DayFlag>,
    val backend: BackendId?,
    val limbs: Map<LimbKind, List<LimbSpan>>,
    val windows: List<Window>,
    val unavailable: Set<WindowKind>,
    val shool: Shool,
) {
    init {
        requireUtcOffset(utcOffsetSeconds)
        require(vaar == Vaar.of(date)) { "vaar must be the weekday of the civil date" }
        require(sunrise < sunset && sunset < nextSunrise) { "sun events out of order" }
    }

    /** The span of [k] that contains sunrise (it names the day). */
    fun atSunrise(k: LimbKind): LimbSpan =
        checkNotNull(limbs[k]?.firstOrNull { sunrise in it }) { "no span of this kind contains sunrise" }

    override fun toString(): String = "PanchangDay(redacted)"
}

/** Paksha of tithi 1..30: 1..15 SHUKLA, 16..30 KRISHNA. Throws [IllegalArgumentException] outside 1..30. */
fun pakshaOf(tithi: Int): Paksha {
    require(tithi in LimbKind.TITHI.firstIndex..LimbKind.TITHI.lastIndex) { "tithi out of range" }
    return if (tithi <= TITHIS_PER_PAKSHA) Paksha.SHUKLA else Paksha.KRISHNA
}

private const val TITHIS_PER_PAKSHA = 15

/** Smallest accepted UTC offset, seconds (UTC-12). */
internal const val MIN_UTC_OFFSET_SECONDS = -43_200

/** Largest accepted UTC offset, seconds (UTC+14). */
internal const val MAX_UTC_OFFSET_SECONDS = 50_400

/** Throws [IllegalArgumentException] (message without the value) unless [seconds] is an accepted UTC offset. */
internal fun requireUtcOffset(seconds: Int) {
    require(seconds in MIN_UTC_OFFSET_SECONDS..MAX_UTC_OFFSET_SECONDS) { "UTC offset out of range" }
}
