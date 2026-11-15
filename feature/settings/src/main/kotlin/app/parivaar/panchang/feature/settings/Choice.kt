@file:Suppress("MaxLineLength")

package app.parivaar.panchang.feature.settings

import androidx.annotation.StringRes
import app.parivaar.panchang.core.model.settings.CalendarView
import app.parivaar.panchang.core.model.settings.ChartStyle
import app.parivaar.panchang.engine.SunriseConvention
import app.parivaar.panchang.engine.calendar.MonthSystem
import app.parivaar.panchang.engine.calendar.Tradition
import app.parivaar.panchang.engine.jyotish.AyanamsaId
import app.parivaar.panchang.engine.jyotish.DashaYear

internal data class Choice<T>(val value: T, @StringRes val label: Int, @StringRes val desc: Int?)

/**
 * Only engine-enabled ayanamsas are offered (the engine throws for the others until verified definitions are
 * supplied). A unit test asserts this list equals `Ayanamsa.isEnabled` for every id and that each one computes.
 */
internal val ayanamsaChoices =
    listOf(
        Choice(AyanamsaId.LAHIRI, R.string.conventions_ayanamsa_lahiri, R.string.conventions_ayanamsa_lahiri_desc),
        Choice(
            AyanamsaId.TRUE_CHITRA, R.string.conventions_ayanamsa_true_chitra, R.string.conventions_ayanamsa_true_chitra_desc,
        ),
    )

internal val sunriseChoices =
    SunriseConvention.entries.map {
        when (it) {
            SunriseConvention.UPPER_LIMB_REFRACTED ->
                Choice(it, R.string.conventions_sunrise_upper_limb, R.string.conventions_sunrise_upper_limb_desc)
            SunriseConvention.CENTER_REFRACTED ->
                Choice(it, R.string.conventions_sunrise_center_refracted, R.string.conventions_sunrise_center_refracted_desc)
            SunriseConvention.CENTER_GEOMETRIC ->
                Choice(it, R.string.conventions_sunrise_center_geometric, R.string.conventions_sunrise_center_geometric_desc)
        }
    }

internal val monthChoices =
    MonthSystem.entries.map {
        when (it) {
            MonthSystem.AMANTA -> Choice(it, R.string.conventions_month_amanta, R.string.conventions_month_amanta_desc)
            MonthSystem.PURNIMANTA ->
                Choice(it, R.string.conventions_month_purnimanta, R.string.conventions_month_purnimanta_desc)
        }
    }

internal val dashaChoices =
    DashaYear.entries.map {
        when (it) {
            DashaYear.JULIAN_365_25 -> Choice(it, R.string.conventions_dasha_julian, R.string.conventions_dasha_julian_desc)
            DashaYear.GREGORIAN_365_2425 ->
                Choice(it, R.string.conventions_dasha_gregorian, R.string.conventions_dasha_gregorian_desc)
            DashaYear.SAVANA_360 -> Choice(it, R.string.conventions_dasha_savana, R.string.conventions_dasha_savana_desc)
        }
    }

internal val ekadashiChoices =
    Tradition.entries.map {
        when (it) {
            Tradition.SMARTA -> Choice(it, R.string.conventions_ekadashi_smarta, R.string.conventions_ekadashi_smarta_desc)
            Tradition.VAISHNAVA ->
                Choice(it, R.string.conventions_ekadashi_vaishnava, R.string.conventions_ekadashi_vaishnava_desc)
        }
    }

internal val chartChoices =
    ChartStyle.entries.map {
        when (it) {
            ChartStyle.NORTH -> Choice(it, R.string.conventions_chart_north, R.string.conventions_chart_north_desc)
            ChartStyle.SOUTH -> Choice(it, R.string.conventions_chart_south, R.string.conventions_chart_south_desc)
            ChartStyle.EAST -> Choice(it, R.string.conventions_chart_east, R.string.conventions_chart_east_desc)
        }
    }

/** Calendar views; the Marathi and Gujarati ones are offered only while the regional gate is open. */
internal fun calendarViewChoices(regionalEnabled: Boolean): List<Choice<CalendarView>> =
    buildList {
        add(Choice(CalendarView.STANDARD, R.string.conventions_calview_standard, R.string.conventions_calview_standard_desc))
        if (regionalEnabled) {
            add(Choice(CalendarView.MARATHI, R.string.conventions_calview_marathi, R.string.conventions_calview_marathi_desc))
            add(Choice(CalendarView.GUJARATI, R.string.conventions_calview_gujarati, R.string.conventions_calview_gujarati_desc))
        }
    }
