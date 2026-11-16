@file:Suppress("MaxLineLength")

package app.parivaar.panchang.platform.widget

import android.content.Context
import android.text.format.DateFormat
import app.parivaar.panchang.R
import app.parivaar.panchang.engine.panchang.LimbSpan
import app.parivaar.panchang.engine.panchang.WindowKind
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import app.parivaar.panchang.feature.aaj.PlaceClock
import app.parivaar.panchang.feature.calendar.guide.FestivalNames
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** Text for widgets and the tile, from string resources. Nothing here can see a name, profile or birth data. */
internal class WidgetText(
    private val context: Context,
    private val clock: PlaceClock,
    private val shown: LocalDate,
) {
    /** Fixed offset (no place yet). */
    constructor(context: Context, offsetSeconds: Int, shown: LocalDate) : this(context, PlaceClock.fixed(offsetSeconds), shown)

    /** The place's real zone: D-1, D and D+1 each in their own offset (DST-aware). */
    constructor(context: Context, zoneId: String, dayOffsetSeconds: Int, shown: LocalDate) :
        this(context, PlaceClock(zoneId, dayOffsetSeconds, shown), shown)

    private val locale = Locale.getDefault()
    private val fmt =
        if (DateFormat.is24HourFormat(context)) {
            DateTimeFormatter.ofPattern("HH:mm", locale)
        } else {
            DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
        }

    private val dateFmt =
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMd"), locale)

    /** Clock time, with a day label when it falls on another civil date than the shown one. */
    fun time(i: Instant): String =
        timeWithDayRef(
            clock(i), clock.dayRef(i, shown),
            context.getString(R.string.widget_next_day), context.getString(R.string.widget_previous_day),
        ) { d -> context.getString(R.string.widget_other_day, dateFmt.format(java.time.LocalDate.of(d.year, d.monthNumber, d.dayOfMonth))) }

    /** Start and end texts; a shared day label is printed once, at the end. */
    fun range(from: Instant, to: Instant): Pair<String, String> =
        rangeWithDayRef(
            from, to, clock, shown, ::clock,
            context.getString(R.string.widget_next_day), context.getString(R.string.widget_previous_day),
        ) { d -> context.getString(R.string.widget_other_day, dateFmt.format(java.time.LocalDate.of(d.year, d.monthNumber, d.dayOfMonth))) }

    private fun clock(i: Instant): String =
        fmt.format(ZonedDateTime.ofInstant(java.time.Instant.ofEpochSecond(i.epochSeconds), clock.zone))

    fun vaar(ordinal: Int): String = context.resources.getStringArray(R.array.widget_vaar)[ordinal]

    fun tithi(t: LimbSpan): String {
        val i = t.index
        return when (i) {
            PURNIMA -> context.getString(R.string.widget_purnima)
            AMAVASYA -> context.getString(R.string.widget_amavasya)
            else -> {
                val name = context.resources.getStringArray(R.array.widget_tithi_names)[(i - 1) % PAKSHA_DAYS]
                context.getString(if (i < PURNIMA) R.string.widget_tithi_shukla else R.string.widget_tithi_krishna, name)
            }
        }
    }

    fun nakshatra(n: LimbSpan): String = context.resources.getStringArray(R.array.widget_nakshatra_names)[n.index]

    fun window(kind: WindowKind): String =
        context.getString(
            when (kind) {
                WindowKind.RAHU_KAAL -> R.string.widget_win_rahu_kaal
                WindowKind.YAMAGANDA -> R.string.widget_win_yamaganda
                WindowKind.GULIKA -> R.string.widget_win_gulika
                WindowKind.ABHIJIT -> R.string.widget_win_abhijit
                WindowKind.BRAHMA_MUHURTA -> R.string.widget_win_brahma_muhurta
                WindowKind.DURMUHURTA -> R.string.widget_win_durmuhurta
                WindowKind.VARJYAM -> R.string.widget_win_varjyam
                WindowKind.AMRIT_KAAL -> R.string.widget_win_amrit_kaal
                else -> R.string.widget_win_bhadra
            },
        )

    fun choghadiya(code: String?): String? =
        when (code) {
            "SHUBH" -> R.string.widget_cg_shubh
            "AMRIT" -> R.string.widget_cg_amrit
            "CHAR" -> R.string.widget_cg_char
            "LABH" -> R.string.widget_cg_labh
            "ROG" -> R.string.widget_cg_rog
            "KAAL" -> R.string.widget_cg_kaal
            "UDVEG" -> R.string.widget_cg_udveg
            else -> null
        }?.let(context::getString)

    /** Display name for a festival rule id, from the one shared name table (`FestivalNames`). */
    fun festival(id: String): String = FestivalNames.name(context.resources, id)

    private companion object {
        const val PAKSHA_DAYS = 15
        const val PURNIMA = 15
        const val AMAVASYA = 30
    }
}
