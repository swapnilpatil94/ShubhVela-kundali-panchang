@file:Suppress("MaxLineLength", "MatchingDeclarationName")
package app.parivaar.panchang.feature.aaj.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.parivaar.panchang.core.designsystem.components.OrbTone
import app.parivaar.panchang.core.designsystem.components.OrbUi
import app.parivaar.panchang.core.designsystem.tokens.Gem
import app.parivaar.panchang.core.designsystem.tokens.gemFor
import app.parivaar.panchang.engine.panchang.Vaar
import app.parivaar.panchang.feature.aaj.AajModel
import app.parivaar.panchang.feature.aaj.AajUi
import app.parivaar.panchang.feature.aaj.vedicDayAt
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.time.Instant
import app.parivaar.panchang.core.designsystem.R as DsR

/** What the dock needs from Aaj: the orb and the weekday gem. */
data class DockState(val orb: OrbUi, val gem: Gem)

private const val SLOT = "\u0000"
private const val RING_STEPS = 360f
private val PLACEHOLDER_DATE = LocalDate(2000, 1, 1)

/**
 * Derives the dock state from the shared [AajModel] (same instance the Aaj tab uses; no engine call, no recompute).
 * The ring fraction is rounded to 1 degree of sweep, so the model's 1 s tick recomposes the dock only when the ring moves.
 * Before Aaj has loaded, or when it failed, the orb is neutral.
 */
@Composable
fun rememberDockState(model: AajModel): DockState {
    val ui by model.ui.collectAsStateWithLifecycle()
    val now by model.tick.collectAsStateWithLifecycle()
    val content = ui as? AajUi.Content
    val fmt = rememberFmt(content?.day?.utcOffsetSeconds ?: 0, content?.date ?: PLACEHOLDER_DATE, content?.zoneId)
    val good = stringResource(DsR.string.orb_a11y_good, SLOT)
    val wait = stringResource(DsR.string.orb_a11y_wait, SLOT)
    val neutral = stringResource(DsR.string.orb_a11y_neutral)
    val state by remember(fmt, good, wait, neutral) {
        derivedStateOf {
            val texts = OrbTexts(fmt::time, { good.replace(SLOT, it) }, { wait.replace(SLOT, it) }, neutral)
            dockState(ui, now, texts)
        }
    }
    return state
}

internal fun dockState(ui: AajUi, now: Instant, texts: OrbTexts): DockState {
    val c = ui as? AajUi.Content
        ?: return DockState(OrbUi(OrbTone.NEUTRAL, null, null, texts.neutral), gemFor(DayOfWeek.THURSDAY))
    val orb = orbUi(c.header, c.days, now, texts)
    val rounded = orb.fractionLeft?.let { Math.round(it * RING_STEPS) / RING_STEPS }
    return DockState(orb.copy(fractionLeft = rounded), gemFor(vaarAt(c, now).dayOfWeek()))
}

internal fun Vaar.dayOfWeek(): DayOfWeek = DayOfWeek.valueOf(name)

/** Vaar of the sunrise-to-sunrise day containing [now] (before sunrise it is still yesterday's vaar). */
internal fun vaarAt(c: AajUi.Content, now: Instant): Vaar = (vedicDayAt(c.days, now) ?: c.day).vaar
