@file:Suppress("MaxLineLength", "MatchingDeclarationName", "TooManyFunctions") // Compose layout code, wrapped by the formatter later

package app.parivaar.panchang.feature.aaj.ui

import android.content.res.Resources
import android.text.format.DateFormat
import app.parivaar.panchang.feature.aaj.R
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Instant
import app.parivaar.panchang.feature.aaj.DayRef
import app.parivaar.panchang.feature.aaj.dayRef
import app.parivaar.panchang.feature.aaj.rangeRefs
import kotlinx.datetime.LocalDate

/** Local time of the place (fixed offset of the day), 12 or 24 hour. */
class TimeFmt(
    private val offsetSeconds: Int,
    private val shown: LocalDate,
    is24Hour: Boolean,
    private val locale: Locale,
) {
    private val zone = ZoneOffset.ofTotalSeconds(offsetSeconds)
    private val pattern = DateTimeFormatter.ofPattern(
        DateFormat.getBestDateTimePattern(locale, if (is24Hour) "Hm" else "hm"), locale,
    )

    fun time(at: Instant): String = pattern.format(java.time.Instant.ofEpochSecond(at.epochSeconds).atOffset(zone))

    private val shortDate = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMd"), locale)

    /** The time with a day note when its civil date is not the shown date: (next day), (previous day) or (Nov 17). */
    fun stamp(res: Resources, at: Instant): String = withRef(res, at, dayRef(at, offsetSeconds, shown))

    fun refOf(at: Instant): DayRef = dayRef(at, offsetSeconds, shown)

    private fun withRef(res: Resources, at: Instant, ref: DayRef): String {
        val note = when (val r = ref) {
            DayRef.Same -> return time(at)
            DayRef.Next -> res.getString(R.string.aaj_next_day_suffix)
            DayRef.Previous -> res.getString(R.string.aaj_previous_day_suffix)
            is DayRef.Other -> shortDate.format(java.time.LocalDate.ofEpochDay(r.date.toEpochDays().toLong()))
        }
        return res.getString(R.string.aaj_time_with_note, time(at), note)
    }

    fun range(res: Resources, from: Instant, to: Instant): String =
        rangeRefs(from, to, offsetSeconds, shown).let { (a, b) ->
            res.getString(R.string.aaj_range, withRef(res, from, a), withRef(res, to, b))
        }

    /** "Sat 15 Nov". */
    fun dateShort(d: LocalDate): String =
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEdMMM"), locale)
            .format(java.time.LocalDate.ofEpochDay(d.toEpochDays().toLong()))

    fun date(d: LocalDate): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)
            .format(java.time.LocalDate.ofEpochDay(d.toEpochDays().toLong()))
}

/** "Starts in 1:02:03" or "Ends in 0:12:00", whole seconds, never negative. */
fun countdownText(res: Resources, remaining: Duration, ends: Boolean): String {
    val s = remaining.inWholeSeconds.coerceAtLeast(0)
    return res.getString(
        if (ends) R.string.aaj_countdown_ends else R.string.aaj_countdown_starts,
        s / SECONDS_PER_HOUR, s % SECONDS_PER_HOUR / SECONDS_PER_MINUTE, s % SECONDS_PER_MINUTE,
    )
}

private const val SECONDS_PER_HOUR = 3600L
private const val SECONDS_PER_MINUTE = 60L
