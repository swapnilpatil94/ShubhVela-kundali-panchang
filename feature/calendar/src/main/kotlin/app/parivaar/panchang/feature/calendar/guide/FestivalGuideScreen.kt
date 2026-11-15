@file:Suppress("MaxLineLength", "MatchingDeclarationName", "MagicNumber") // Compose layout code and preview data

package app.parivaar.panchang.feature.calendar.guide

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.parivaar.panchang.core.designsystem.PanchangTheme
import app.parivaar.panchang.core.designsystem.PpTheme
import app.parivaar.panchang.core.designsystem.RatnaMoodScope
import app.parivaar.panchang.core.designsystem.surfaces.Lacquer
import app.parivaar.panchang.core.designsystem.tokens.PpType
import app.parivaar.panchang.core.designsystem.tokens.RatnaMood
import app.parivaar.panchang.core.designsystem.preview.PpPreviews
import app.parivaar.panchang.engine.calendar.FestivalDef
import app.parivaar.panchang.engine.calendar.FestivalOccurrence
import app.parivaar.panchang.engine.calendar.FestivalRule
import app.parivaar.panchang.engine.calendar.Kala
import app.parivaar.panchang.engine.calendar.TimeRange
import app.parivaar.panchang.engine.jyotish.RuleStatus
import app.parivaar.panchang.engine.panchang.LimbKind
import app.parivaar.panchang.feature.aaj.ui.AajNames
import app.parivaar.panchang.feature.calendar.R
import app.parivaar.panchang.feature.calendar.common.CalFormat
import app.parivaar.panchang.feature.calendar.common.ScreenFrame
import app.parivaar.panchang.feature.calendar.common.previewFormat
import app.parivaar.panchang.feature.calendar.common.previewNames
import kotlinx.datetime.LocalDate

/** One upcoming date of a festival, with its kala window when the engine gave one. */
data class GuideDate(val date: LocalDate, val window: TimeRange?)

/** Everything the guide page shows; ids only (names come from resources). */
data class FestivalGuideUi(
    val id: String,
    val dates: List<GuideDate>,
    val tithis: List<Int>,
    val kala: Kala?,
    val status: RuleStatus,
    val sourceId: String,
)

/** Pure builder: the next three dates on or after [today] from this year's and next year's occurrences. */
object FestivalGuide {
    private const val SHOWN = 3

    fun build(def: FestivalDef, thisYear: List<FestivalOccurrence>, nextYear: List<FestivalOccurrence>, today: LocalDate): FestivalGuideUi {
        val dates = (thisYear + nextYear).filter { it.id == def.id && it.date >= today }
            .sortedBy { it.date }.distinctBy { it.date }.take(SHOWN).map { GuideDate(it.date, it.kalaWindow) }
        val rule = def.rule as? FestivalRule.TithiAt
        return FestivalGuideUi(def.id, dates, rule?.tithi.orEmpty(), rule?.kala, def.status, def.sourceId)
    }
}

private fun kalaRes(k: Kala): Int = when (k) {
    Kala.SUNRISE -> R.string.guide_kala_sunrise
    Kala.ARUNODAYA -> R.string.guide_kala_arunodaya
    Kala.PRADOSH -> R.string.guide_kala_pradosh
    Kala.NISHITA -> R.string.guide_kala_nishita
    Kala.MADHYAHNA -> R.string.guide_kala_madhyahna
    Kala.APARAHNA -> R.string.guide_kala_aparahna
    Kala.MOONRISE -> R.string.guide_kala_moonrise
    Kala.AHORATRA -> R.string.guide_kala_ahoratra
}

/**
 * Stateless festival guide page (Dest.FestivalGuide), in the Utsav mood (D5). Body text arrives with the M11 content
 * packs; steps (FestivalSteps) wait for that data, so only the engine-bound dates and rule are shown. One hero card.
 */
@Composable
fun FestivalGuideScreen(ui: FestivalGuideUi, names: AajNames, fmt: CalFormat, onBack: () -> Unit) {
    RatnaMoodScope(RatnaMood.UTSAV) { GuideBody(ui, names, fmt, onBack) }
}

@Composable
private fun GuideBody(ui: FestivalGuideUi, names: AajNames, fmt: CalFormat, onBack: () -> Unit) {
    val nameRes = FestivalNames.nameRes(ui.id)
    val r = PpTheme.ratna
    ScreenFrame(
        title = if (nameRes != null) stringResource(nameRes) else ui.id,
        backLabel = stringResource(R.string.guide_back),
        onBack = onBack,
        eyebrow = stringResource(R.string.guide_eyebrow_utsav),
    ) {
        Text(stringResource(R.string.guide_next_dates), style = PpType.eyebrow, color = r.text2)
        if (ui.dates.isEmpty()) {
            Text(stringResource(R.string.guide_no_dates), style = PpType.body, color = r.text)
        }
        ui.dates.forEachIndexed { i, d ->
            Lacquer(Modifier.fillMaxWidth(), hero = i == 0) {
                Text(fmt.date(d.date), style = if (i == 0) PpType.displayS else PpType.body, color = r.text)
                d.window?.takeIf { it.start != it.end }?.let {
                    Text(
                        stringResource(R.string.guide_range, fmt.time(it.start), fmt.time(it.end)),
                        style = PpType.body, color = r.text2,
                    )
                }
            }
        }
        Lacquer(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.guide_rule_title), style = PpType.displayS, color = r.text)
            if (ui.tithis.isEmpty() && ui.kala == null) Text(stringResource(R.string.guide_rule_other), style = PpType.body, color = r.text2)
            if (ui.tithis.isNotEmpty()) {
                val list = ui.tithis.joinToString(", ") { names.limbSpan(LimbKind.TITHI, it) }
                Text(stringResource(R.string.guide_rule_tithi, list), style = PpType.body, color = r.text2)
            }
            ui.kala?.let { Text(stringResource(R.string.guide_rule_kala, stringResource(kalaRes(it))), style = PpType.body, color = r.text2) }
            Text(
                stringResource(
                    if (ui.status == RuleStatus.APPROVED) R.string.guide_status_approved else R.string.guide_status_verify,
                ),
                style = PpType.body, color = r.text2,
            )
            Text(stringResource(R.string.guide_source, ui.sourceId), style = PpType.meta, color = r.text3)
        }
        Text(stringResource(R.string.guide_more_coming), style = PpType.body, color = r.text3)
    }
}

@PpPreviews
@Composable
private fun FestivalGuidePreview() = PanchangTheme {
    val ui = FestivalGuideUi(
        "maha_shivratri", listOf(GuideDate(LocalDate(2027, 3, 6), null)), listOf(29), Kala.NISHITA,
        RuleStatus.VERIFY, "src.traditional.unverified",
    )
    FestivalGuideScreen(
        ui, previewNames(),
        previewFormat(), {},
    )
}
