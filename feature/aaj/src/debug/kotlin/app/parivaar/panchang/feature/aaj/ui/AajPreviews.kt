@file:Suppress("MagicNumber", "MaxLineLength", "MatchingDeclarationName", "TooManyFunctions")

package app.parivaar.panchang.feature.aaj.ui

import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.parivaar.panchang.core.designsystem.PanchangTheme
import app.parivaar.panchang.core.designsystem.components.ProofInfo
import app.parivaar.panchang.core.designsystem.preview.PpPreviews
import app.parivaar.panchang.engine.calendar.Ayana
import app.parivaar.panchang.engine.calendar.CalendarDay
import app.parivaar.panchang.engine.calendar.LunarLabel
import app.parivaar.panchang.engine.calendar.Masa
import app.parivaar.panchang.engine.calendar.Ritu
import app.parivaar.panchang.engine.calendar.Samvat
import app.parivaar.panchang.engine.panchang.DayFlag
import app.parivaar.panchang.engine.panchang.Direction
import app.parivaar.panchang.engine.panchang.LimbKind
import app.parivaar.panchang.engine.panchang.LimbSpan
import app.parivaar.panchang.engine.panchang.PanchangDay
import app.parivaar.panchang.engine.panchang.Shool
import app.parivaar.panchang.engine.panchang.Vaar
import app.parivaar.panchang.engine.panchang.Window
import app.parivaar.panchang.engine.panchang.WindowKind
import app.parivaar.panchang.feature.aaj.AajUi
import app.parivaar.panchang.feature.aaj.HeaderRule
import app.parivaar.panchang.feature.aaj.NextWindowRule
import app.parivaar.panchang.feature.aaj.UnavailableIds
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

// Preview-only fixtures (debug source set, never shipped). Fictional round times, not real panchang data.
private val DATE = LocalDate(2026, 11, 15)
private const val OFFSET = 19_800
private val EPOCH = DATE.toEpochDays() * 86_400L - OFFSET

private fun t(h: Double) = Instant.fromEpochSeconds(EPOCH + (h * 3600).toLong())

private fun w(k: WindowKind, a: Double, b: Double, code: String? = null, night: Boolean = false) =
    Window(k, 1, code, night, t(a), t(b))

private fun day(n: Int, high: Boolean = false): PanchangDay {
    val d = DATE.plus(DatePeriod(days = n))
    val o = 24.0 * n
    val chog = listOf("UDVEG", "CHAR", "LABH", "AMRIT", "KAAL", "SHUBH", "ROG", "UDVEG")
    val windows = chog.mapIndexed { i, c -> w(WindowKind.CHOGHADIYA, o + 6.4 + i * 1.5, o + 7.9 + i * 1.5, c) } +
        listOf(
            w(WindowKind.RAHU_KAAL, o + 16.5, o + 18.0), w(WindowKind.ABHIJIT, o + 12.0, o + 12.8),
            w(WindowKind.BRAHMA_MUHURTA, o + 4.8, o + 5.6),
        )
    val limbs = LimbKind.entries.associateWith { k ->
        listOf(LimbSpan(k, k.firstIndex + 3, t(o - 2.0), t(o + 14.5)), LimbSpan(k, k.firstIndex + 4, t(o + 14.5), t(o + 33.0)))
    }
    return PanchangDay(
        d, OFFSET, Vaar.of(d), t(o + 6.4), t(o + 17.9), t(o + 30.4), if (high) setOf(DayFlag.SUN_FALLBACK) else emptySet(),
        null, limbs, windows.sortedBy { it.start }, setOf(WindowKind.VARJYAM, WindowKind.AMRIT_KAAL), Shool(Direction.EAST, "x"),
    )
}

private val calendar = CalendarDay(
    DATE, 5, LunarLabel(Masa.KARTIKA, false, null), LunarLabel(Masa.MARGASHIRSHA, false, null),
    Samvat(1948, 2083, 2083, 40, 40, 1433, 1202), Ritu.HEMANTA, Ayana.DAKSHINAYANA, emptyMap(), emptySet(),
)

private fun content(now: Instant, high: Boolean = false, festivals: List<app.parivaar.panchang.engine.calendar.FestivalOccurrence>? = emptyList()): AajUi.Content {
    val days = listOf(day(-1), day(0, high), day(1))
    return AajUi.Content(
        DATE, days, calendar, HeaderRule.evaluate(now, days), NextWindowRule.next(now, days), festivals, null,
        days[1].unavailable.mapTo(HashSet()) { it.name } + UnavailableIds.ALWAYS, days[1].flags,
        ProofInfo("BASE_ASTRONOMY_ENGINE", "LAHIRI", "UPPER_LIMB_REFRACTED", 560.0), false,
    )
}

private val actions = AajActions({}, {}, {}, {}, {})

@Composable
private fun Show(ui: AajUi, now: Instant = t(10.0)) = PanchangTheme {
    AajScreenContent(ui, now, "Pune", "AB", actions)
}

@PpPreviews
@Composable
private fun AajGoodNowPreview() = Show(content(t(10.0)))

@PpPreviews
@Composable
private fun AajWaitPreview() = Show(content(t(17.0)), t(17.0))

@PpPreviews
@Composable
private fun AajHighLatitudePreview() = Show(content(t(10.0), high = true))

@PpPreviews
@Composable
private fun AajLoadingPreview() = Show(AajUi.Loading)

@PpPreviews
@Composable
private fun AajNoPlacePreview() = Show(AajUi.NoPlace)

@PpPreviews
@Composable
private fun AajFailedPreview() = Show(AajUi.Failed)

@PpPreviews
@Composable
private fun AajNonexistentPreview() = Show(AajUi.NonexistentDate)

@PpPreviews
@Composable
private fun AajPolarPreview() = Show(
    AajUi.PolarDay(DATE, day(0).limbs, ProofInfo("", "LAHIRI", "UPPER_LIMB_REFRACTED", null)),
)

@PpPreviews
@Composable
private fun AajFullPanchangPreview() = PanchangTheme {
    val c = content(t(10.0))
    androidx.compose.foundation.layout.Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
        FullPanchang(c, rememberNames(), rememberFmt(OFFSET, DATE))
    }
}
