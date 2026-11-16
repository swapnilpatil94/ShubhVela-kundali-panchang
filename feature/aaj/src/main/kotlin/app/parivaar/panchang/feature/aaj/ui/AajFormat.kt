@file:Suppress("MaxLineLength", "MatchingDeclarationName", "TooManyFunctions") // Compose layout code, wrapped by the formatter later

package app.parivaar.panchang.feature.aaj.ui

import android.content.res.Resources
import android.text.format.DateFormat
import app.parivaar.panchang.feature.aaj.R
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Instant
import app.parivaar.panchang.feature.aaj.DayRef
import app.parivaar.panchang.feature.aaj.PlaceClock
import kotlinx.datetime.LocalDate

/**
 * Local time of the place, 12 or 24 hour. Each instant uses the place's own offset at that instant ([PlaceClock]),
 * so D-1, D and D+1 around a DST change all read as wall-clock time.
 */
class TimeFmt internal constructor(
    private val clock: PlaceClock,
    private val shown: LocalDate,
    timePattern: String,
    private val locale: Locale,
) {
    constructor(clock: PlaceClock, shown: LocalDate, is24Hour: Boolean, locale: Locale) :
        this(clock, shown, DateFormat.getBestDateTimePattern(locale, if (is24Hour) "Hm" else "hm"), locale)

    /** Fixed-offset formatter (previews and callers without a zone). */
    constructor(offsetSeconds: Int, shown: LocalDate, is24Hour: Boolean, locale: Locale) :
        this(PlaceClock.fixed(offsetSeconds), shown, is24Hour, locale)

    private val pattern = DateTimeFormatter.ofPattern(timePattern, locale)

    fun time(at: Instant): String = pattern.format(java.time.Instant.ofEpochSecond(at.epochSeconds).atZone(clock.zone))

    private val shortDate by lazy { DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMd"), locale) }

    /** The time with a day note when its civil date is not the shown date: (next day), (previous day) or (Nov 17). */
    fun stamp(res: Resources, at: Instant): String = withRef(res, at, refOf(at))

    fun refOf(at: Instant): DayRef = clock.dayRef(at, shown)

    /** The place's UTC offset at [at], in seconds. */
    fun offsetAt(at: Instant): Int = clock.offsetAt(at)

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
        clock.rangeRefs(from, to, shown).let { (a, b) ->
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
