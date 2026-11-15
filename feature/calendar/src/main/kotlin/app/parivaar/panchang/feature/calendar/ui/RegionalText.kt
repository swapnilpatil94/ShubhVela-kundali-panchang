package app.parivaar.panchang.feature.calendar.ui

import android.content.res.Resources
import app.parivaar.panchang.core.model.settings.CalendarView
import app.parivaar.panchang.engine.panchang.Paksha
import app.parivaar.panchang.feature.calendar.R
import app.parivaar.panchang.feature.calendar.RegionalLabel
import java.text.NumberFormat
import java.util.Locale

/** Marathi and Gujarati text of a regional day label. Local numerals only when the UI language matches. */
object RegionalText {
    fun number(n: Int, view: CalendarView, uiLocale: Locale): String {
        val lang = if (view == CalendarView.GUJARATI) "gu" else "mr"
        if (uiLocale.language != lang) return n.toString()
        return NumberFormat.getInstance(Locale.forLanguageTag(lang)).format(n)
    }

    fun paksha(res: Resources, view: CalendarView, p: Paksha): String = res.getString(
        when {
            view == CalendarView.GUJARATI && p == Paksha.SHUKLA -> R.string.cal_regional_paksha_gu_shukla
            view == CalendarView.GUJARATI -> R.string.cal_regional_paksha_gu_krishna
            p == Paksha.SHUKLA -> R.string.cal_regional_paksha_mr_shukla
            else -> R.string.cal_regional_paksha_mr_krishna
        },
    )

    /** "शु. 5" or "સુદ 5": paksha then the tithi within it. */
    fun cell(res: Resources, view: CalendarView, uiLocale: Locale, r: RegionalLabel): String =
        res.getString(R.string.cal_regional_cell, paksha(res, view, r.paksha), number(r.tithiInPaksha, view, uiLocale))
}
