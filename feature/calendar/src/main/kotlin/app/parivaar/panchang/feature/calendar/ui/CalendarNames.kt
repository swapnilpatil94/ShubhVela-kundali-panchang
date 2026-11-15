@file:Suppress("MatchingDeclarationName")

package app.parivaar.panchang.feature.calendar.ui

import android.content.res.Resources
import app.parivaar.panchang.engine.panchang.LimbKind
import app.parivaar.panchang.feature.aaj.ui.AajNames
import app.parivaar.panchang.feature.calendar.CalendarNames
import app.parivaar.panchang.feature.calendar.Marker
import app.parivaar.panchang.feature.calendar.R
import app.parivaar.panchang.feature.calendar.guide.FestivalNames
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.datetime.LocalDate

/** Spoken and visible names of a day cell, all from resources. */
class ResCalendarNames(private val res: Resources, packageName: String, locale: Locale) : CalendarNames {
    internal val aaj = AajNames(res, packageName)
    private val full = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)

    override fun fullDate(date: LocalDate): String = full.format(java.time.LocalDate.ofEpochDay(date.toEpochDays()))

    override fun tithiName(tithi: Int): String = aaj.limbSpan(LimbKind.TITHI, tithi)

    override fun festivalName(id: String): String = FestivalNames.name(res, id)

    override fun markerName(marker: Marker): String = res.getString(
        when (marker) {
            Marker.EKADASHI -> R.string.cal_marker_ekadashi
            Marker.PURNIMA -> R.string.cal_marker_purnima
            Marker.AMAVASYA -> R.string.cal_marker_amavasya
            Marker.SANKRANTI -> R.string.cal_marker_sankranti
            Marker.ECLIPSE -> R.string.cal_marker_eclipse
        },
    )

    fun markerGlyph(marker: Marker): String = res.getString(
        when (marker) {
            Marker.EKADASHI -> R.string.cal_marker_glyph_ekadashi
            Marker.PURNIMA -> R.string.cal_marker_glyph_purnima
            Marker.AMAVASYA -> R.string.cal_marker_glyph_amavasya
            Marker.SANKRANTI -> R.string.cal_marker_glyph_sankranti
            Marker.ECLIPSE -> R.string.cal_marker_glyph_eclipse
        },
    )

    override fun todayWord(): String = res.getString(R.string.cal_word_today)

    override fun kshayaWord(): String = res.getString(R.string.cal_word_kshaya)

    override fun vriddhiWord(): String = res.getString(R.string.cal_word_vriddhi)

    fun masa(m: app.parivaar.panchang.engine.calendar.Masa): String = aaj.masa(m)
}
