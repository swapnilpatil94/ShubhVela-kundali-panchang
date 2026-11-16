@file:Suppress("MaxLineLength")

package app.parivaar.panchang.feature.settings

import app.parivaar.panchang.core.data.DataResult
import app.parivaar.panchang.core.data.SettingsRepository
import app.parivaar.panchang.core.model.settings.AppSettings
import app.parivaar.panchang.core.model.settings.ChartStyle
import app.parivaar.panchang.core.model.settings.PresetSource
import app.parivaar.panchang.core.model.settings.ThemeMode
import app.parivaar.panchang.engine.CalcSettings
import app.parivaar.panchang.engine.SunriseConvention
import app.parivaar.panchang.engine.calendar.MonthSystem
import app.parivaar.panchang.engine.calendar.Tradition
import app.parivaar.panchang.engine.jyotish.AyanamsaId
import app.parivaar.panchang.engine.jyotish.DashaYear
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import app.parivaar.panchang.engine.astro.JulianDay
import app.parivaar.panchang.engine.jyotish.Ayanamsa
import app.parivaar.panchang.engine.jyotish.AyanamsaUnavailableException

private class FakeRepo(initial: AppSettings) : SettingsRepository {
    val s = MutableStateFlow(initial)
    var updates = 0
    override val settings: Flow<AppSettings> get() = s

    override suspend fun update(f: (AppSettings) -> AppSettings): DataResult<Unit> {
        updates++
        s.value = f(s.value)
        return DataResult.Ok(Unit)
    }
}

class ConventionsModelTest {
    // Every field differs from its default so an accidental write to another field is visible.
    private val base =
        AppSettings(
            defaultCalc = CalcSettings(ayanamsa = AyanamsaId.RAMAN, sunrise = SunriseConvention.CENTER_GEOMETRIC, dashaYear = DashaYear.SAVANA_360),
            localeTag = "mr",
            privacyScreen = true,
            monthSystem = MonthSystem.PURNIMANTA,
            presetSource = PresetSource.NOT_SURE_DEFAULT,
            ekadashi = Tradition.VAISHNAVA,
            chartStyle = ChartStyle.EAST,
            themeMode = ThemeMode.DARK,
            badaText = true,
            onboardingDone = true,
        )

    @Test fun ayanamsaWritesOnlyDefaultCalcAyanamsa() {
        for (v in AyanamsaId.entries.filter { Ayanamsa.isEnabled(it) }) {
            assertEquals(base.copy(defaultCalc = base.defaultCalc.copy(ayanamsa = v)), ConventionsEdits.withAyanamsa(base, v))
        }
    }

    @Test fun sunriseWritesOnlyDefaultCalcSunrise() {
        for (v in SunriseConvention.entries) {
            assertEquals(base.copy(defaultCalc = base.defaultCalc.copy(sunrise = v)), ConventionsEdits.withSunrise(base, v))
        }
    }

    @Test fun dashaYearWritesOnlyDefaultCalcDashaYear() {
        for (v in DashaYear.entries) {
            assertEquals(base.copy(defaultCalc = base.defaultCalc.copy(dashaYear = v)), ConventionsEdits.withDashaYear(base, v))
        }
    }

    @Test fun ekadashiAndChartWriteOneFieldEach() {
        for (v in Tradition.entries) assertEquals(base.copy(ekadashi = v), ConventionsEdits.withEkadashi(base, v))
        for (v in ChartStyle.entries) assertEquals(base.copy(chartStyle = v), ConventionsEdits.withChartStyle(base, v))
    }

    @Test fun monthSystemMarksPresetAsUserChosen() {
        for (v in MonthSystem.entries) {
            assertEquals(base.copy(monthSystem = v, presetSource = PresetSource.USER), ConventionsEdits.withMonthSystem(base, v))
        }
    }

    @Test fun modelWritesOneAtomicUpdatePerChoice() {
        val repo = FakeRepo(base)
        val m = ConventionsModel(repo, CoroutineScope(Dispatchers.Unconfined))
        m.setAyanamsa(AyanamsaId.LAHIRI)
        m.setChartStyle(ChartStyle.SOUTH)
        assertEquals(2, repo.updates)
        assertEquals(
            base.copy(defaultCalc = base.defaultCalc.copy(ayanamsa = AyanamsaId.LAHIRI), chartStyle = ChartStyle.SOUTH),
            repo.s.value,
        )
    }

    @Test fun appearanceWritesOneFieldEach() {
        val repo = FakeRepo(base)
        val m = AppearanceModel(repo, CoroutineScope(Dispatchers.Unconfined))
        m.setThemeMode(ThemeMode.LIGHT)
        assertEquals(base.copy(themeMode = ThemeMode.LIGHT), repo.s.value)
        m.setDynamicColor(true)
        m.setBadaText(false)
        m.setReduceMotion(true)
        m.setReduceTransparency(true)
        m.setLocaleTag("hi")
        assertEquals(
            base.copy(
                themeMode = ThemeMode.LIGHT, dynamicColor = true, badaText = false, reduceMotion = true,
                reduceTransparency = true, localeTag = "hi",
            ),
            repo.s.value,
        )
    }

    @Test fun everyEnumEntryHasALabelAndExplanation() {
        assertEquals(AyanamsaId.entries.filter { Ayanamsa.isEnabled(it) }, ayanamsaChoices.map { it.value })
        assertEquals(SunriseConvention.entries, sunriseChoices.map { it.value })
        assertEquals(DashaYear.entries, dashaChoices.map { it.value })
        assertEquals(MonthSystem.entries, monthChoices.map { it.value })
        assertEquals(Tradition.entries, ekadashiChoices.map { it.value })
        assertEquals(ChartStyle.entries, chartChoices.map { it.value })
        assert((ayanamsaChoices + sunriseChoices + dashaChoices + monthChoices + ekadashiChoices + chartChoices).all { it.desc != null })
    }

    @Test fun offeredAyanamsasAreExactlyTheEngineEnabledOnesAndCompute() {
        val enabled = AyanamsaId.entries.filter { Ayanamsa.isEnabled(it) }
        assertEquals(enabled.toSet(), ayanamsaChoices.map { it.value }.toSet())
        val tt = JulianDay(2451545.0)
        for (id in ayanamsaChoices.map { it.value }) Ayanamsa.trueDeg(id, tt) // must not throw
        for (id in AyanamsaId.entries - enabled.toSet()) {
            assertFailsWith<AyanamsaUnavailableException> { Ayanamsa.trueDeg(id, tt) }
        }
    }

    @Test fun disabledAyanamsaIsNeverWritten() {
        for (id in AyanamsaId.entries.filter { !Ayanamsa.isEnabled(it) }) {
            assertEquals(base, ConventionsEdits.withAyanamsa(base, id))
        }
    }
}

class UnreadableSettingsTest {
    private class Repo : SettingsRepository {
        val flag = MutableStateFlow(true)
        var resets = 0
        override val settings: Flow<AppSettings> = MutableStateFlow(AppSettings())
        override val unreadable: Flow<Boolean> get() = flag
        override suspend fun update(f: (AppSettings) -> AppSettings): DataResult<Unit> = DataResult.Ok(Unit)
        override suspend fun resetUnreadable(): DataResult<Unit> { resets++; flag.value = false; return DataResult.Ok(Unit) }
    }

    @Test fun flagIsExposedAndResetRunsOnlyWhenCalled() = kotlinx.coroutines.runBlocking {
        val repo = Repo()
        val m = AppearanceModel(repo, CoroutineScope(Dispatchers.Unconfined))
        assertEquals(true, m.unreadable.first())
        m.setThemeMode(ThemeMode.DARK) // an ordinary edit never resets
        assertEquals(0, repo.resets)
        m.resetUnreadable()
        assertEquals(1, repo.resets)
        assertEquals(false, m.unreadable.first())
    }
}
