package app.parivaar.panchang.feature.calendar.common

import android.text.format.DateFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** Local-time formatting for the user's place zone (never a fixed zone), DST aware. Pure over its inputs. */
class CalFormat(zone: TimeZone, private val locale: Locale, is24Hour: Boolean) {
    private val zoneId: ZoneId = ZoneId.of(zone.id)
    private val timePattern = DateTimeFormatter.ofPattern(
        DateFormat.getBestDateTimePattern(locale, if (is24Hour) "Hm" else "hm"), locale,
    ).withZone(zoneId)
    private val dateTimePattern = DateTimeFormatter.ofPattern(
        DateFormat.getBestDateTimePattern(locale, if (is24Hour) "MMMdHm" else "MMMdhm"), locale,
    ).withZone(zoneId)
    private val datePattern = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)

    fun time(at: Instant): String = timePattern.format(java.time.Instant.ofEpochSecond(at.epochSeconds))

    fun dateTime(at: Instant): String = dateTimePattern.format(java.time.Instant.ofEpochSecond(at.epochSeconds))

    fun date(d: LocalDate): String = datePattern.format(java.time.LocalDate.ofEpochDay(d.toEpochDays().toLong()))

    /** The civil date of [at] at the place. */
    fun dateOf(at: Instant): LocalDate = java.time.Instant.ofEpochSecond(at.epochSeconds).atZone(zoneId).toLocalDate()
        .let { LocalDate(it.year, it.monthValue, it.dayOfMonth) }

    fun number(d: Double, digits: Int): String = java.text.NumberFormat.getInstance(locale)
        .apply { maximumFractionDigits = digits; minimumFractionDigits = digits }.format(d)
}
