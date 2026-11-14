package app.parivaar.panchang.engine.calendar.internal

import app.parivaar.panchang.engine.calendar.Lunation
import app.parivaar.panchang.engine.calendar.LunarLabel
import app.parivaar.panchang.engine.calendar.Masa
import app.parivaar.panchang.engine.calendar.Sankranti
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/**
 * Shaka and Vikram years of a lunation (docs/specs/M04.md section 3.2); [samvatsara] 1..60. `toString` is
 * redacted like every calendar value.
 */
internal data class EraYears(val shaka: Int, val vikramChaitradi: Int, val vikramKartikadi: Int, val samvatsara: Int) {
    override fun toString(): String = "EraYears(redacted)"
}

/** Masa naming and era arithmetic (docs/specs/M04.md sections 3.1 and 3.2); pure functions, place-free. */
internal object MasaNaming {
    private const val MAX_SANKRANTIS = 2
    private const val LAST_SHUKLA = 15
    private const val SHAKA_OFFSET = 78
    private const val VIKRAM_FROM_SHAKA = 135
    private const val VIKRAM_FROM_AD = 57
    private const val SAMVATSARA_ANCHOR = 11
    private const val SAMVATSARAS = 60
    private const val SECONDS_PER_DAY = 86_400L
    private val YEAR_PROBE = 15.days

    /**
     * The amanta label of a lunation from the sankrantis inside it ([inside], time order) and the first
     * sankranti at or after its new moon ([firstAfterNewMoon]): one -> its masa; none -> adhika, named by
     * [firstAfterNewMoon]; two -> the first's masa, kshaya of the second's. More than two throws
     * [IllegalStateException].
     */
    fun name(inside: List<Sankranti>, firstAfterNewMoon: Sankranti?): LunarLabel = when (inside.size) {
        0 -> LunarLabel(masaEntering(checkNotNull(firstAfterNewMoon) { "no sankranti after the lunation" }), true, null)
        1 -> LunarLabel(masaEntering(inside[0]), false, null)
        MAX_SANKRANTIS -> LunarLabel(masaEntering(inside[0]), false, masaEntering(inside[1]))
        else -> throw IllegalStateException("more than two sankrantis in a lunation")
    }

    /** The masa named by sankranti [s]: the ordinal of the rashi entered. */
    fun masaEntering(s: Sankranti): Masa = Masa.entries[s.into.ordinal]

    /** The amanta label of [l]. */
    fun amanta(l: Lunation): LunarLabel = LunarLabel(l.masa, l.adhika, l.kshayaMasa)

    /**
     * The purnimanta label of tithi [tithi] of [l]: the amanta label for 1..15; for 16..30 the adhika label
     * of an adhika [l], else (masa of [nextNija], nija, no kshaya): [nextNija] is the next non-adhika lunation.
     */
    fun purnimanta(l: Lunation, tithi: Int, nextNija: () -> Lunation): LunarLabel = when {
        tithi <= LAST_SHUKLA -> amanta(l)
        l.adhika -> LunarLabel(l.masa, true, null)
        else -> LunarLabel(nextNija().masa, false, null)
    }

    /** True iff [l] is a non-adhika lunation that carries masa [m] (as its name or its kshaya name). */
    fun isNija(l: Lunation, m: Masa): Boolean = !l.adhika && (l.masa == m || l.kshayaMasa == m)

    /** Era years from the new moons of the latest nija Chaitra ([chaitra]) and Kartika ([kartika]) lunations. */
    fun eraYears(chaitra: Instant, kartika: Instant): EraYears {
        val shaka = utcYear(chaitra + YEAR_PROBE) - SHAKA_OFFSET
        return EraYears(
            shaka = shaka,
            vikramChaitradi = shaka + VIKRAM_FROM_SHAKA,
            vikramKartikadi = utcYear(kartika + YEAR_PROBE) + VIKRAM_FROM_AD,
            samvatsara = samvatsara(shaka),
        )
    }

    /** Samvatsara 1..60 of Shaka year [shaka] (anchor: 1946 -> 38). */
    fun samvatsara(shaka: Int): Int = (shaka + SAMVATSARA_ANCHOR).mod(SAMVATSARAS) + 1

    /** The proleptic Gregorian year of the UTC date of [at]. */
    fun utcYear(at: Instant): Int = LocalDate.fromEpochDays(at.epochSeconds.floorDiv(SECONDS_PER_DAY).toInt()).year
}
