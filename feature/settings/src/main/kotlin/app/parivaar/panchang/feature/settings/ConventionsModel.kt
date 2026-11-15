package app.parivaar.panchang.feature.settings

import app.parivaar.panchang.core.data.SettingsRepository
import app.parivaar.panchang.core.model.settings.AppSettings
import app.parivaar.panchang.core.model.settings.ChartStyle
import app.parivaar.panchang.core.model.settings.PresetSource
import app.parivaar.panchang.core.model.settings.ThemeMode
import app.parivaar.panchang.engine.SunriseConvention
import app.parivaar.panchang.engine.calendar.MonthSystem
import app.parivaar.panchang.engine.calendar.Tradition
import app.parivaar.panchang.engine.jyotish.Ayanamsa
import app.parivaar.panchang.engine.jyotish.AyanamsaId
import app.parivaar.panchang.engine.jyotish.DashaYear
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Pure edits (M07 section 5 and D12). Calculation conventions change `defaultCalc` only: profiles keep their own
 * `calc`. Each edit touches exactly one field; the one exception is [withMonthSystem], where an explicit user pick
 * also marks the preset as chosen by the user so "Default, change anytime" disappears.
 */
object ConventionsEdits {
    /** A disabled ayanamsa (the engine throws for it) is ignored, so a bad value can never be stored. */
    fun withAyanamsa(s: AppSettings, v: AyanamsaId) =
        if (Ayanamsa.isEnabled(v)) s.copy(defaultCalc = s.defaultCalc.copy(ayanamsa = v)) else s

    fun withSunrise(s: AppSettings, v: SunriseConvention) = s.copy(defaultCalc = s.defaultCalc.copy(sunrise = v))

    fun withDashaYear(s: AppSettings, v: DashaYear) = s.copy(defaultCalc = s.defaultCalc.copy(dashaYear = v))

    fun withMonthSystem(s: AppSettings, v: MonthSystem) = s.copy(monthSystem = v, presetSource = PresetSource.USER)

    fun withEkadashi(s: AppSettings, v: Tradition) = s.copy(ekadashi = v)

    fun withChartStyle(s: AppSettings, v: ChartStyle) = s.copy(chartStyle = v)
}

/** Writes Conventions choices to the settings repository. Each call is one atomic `update`. */
class ConventionsModel(private val repo: SettingsRepository, private val scope: CoroutineScope) {
    fun setAyanamsa(v: AyanamsaId) = write { ConventionsEdits.withAyanamsa(it, v) }

    fun setSunrise(v: SunriseConvention) = write { ConventionsEdits.withSunrise(it, v) }

    fun setDashaYear(v: DashaYear) = write { ConventionsEdits.withDashaYear(it, v) }

    fun setMonthSystem(v: MonthSystem) = write { ConventionsEdits.withMonthSystem(it, v) }

    fun setEkadashi(v: Tradition) = write { ConventionsEdits.withEkadashi(it, v) }

    fun setChartStyle(v: ChartStyle) = write { ConventionsEdits.withChartStyle(it, v) }

    private fun write(f: (AppSettings) -> AppSettings) {
        scope.launch { repo.update(f) }
    }
}

/** Writes Appearance and Language choices (Settings screen). One field per call. */
class AppearanceModel(private val repo: SettingsRepository, private val scope: CoroutineScope) {
    fun setThemeMode(v: ThemeMode) = write { it.copy(themeMode = v) }

    fun setDynamicColor(v: Boolean) = write { it.copy(dynamicColor = v) }

    fun setBadaText(v: Boolean) = write { it.copy(badaText = v) }

    fun setReduceMotion(v: Boolean) = write { it.copy(reduceMotion = v) }

    fun setTravelMode(v: Boolean) = write { it.copy(travelMode = v) }

    fun setShowCityOnWidgets(v: Boolean) = write { it.copy(showCityOnWidgets = v) }

    /** Persists the tag only; the host applies the locale (`LocaleApplier`). */
    fun setLocaleTag(tag: String) = write { it.copy(localeTag = tag) }

    private fun write(f: (AppSettings) -> AppSettings) {
        scope.launch { repo.update(f) }
    }
}
