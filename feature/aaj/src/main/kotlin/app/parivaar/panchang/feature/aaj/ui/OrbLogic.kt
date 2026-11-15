@file:Suppress("MaxLineLength", "MatchingDeclarationName")
package app.parivaar.panchang.feature.aaj.ui

import app.parivaar.panchang.core.designsystem.components.OrbTone
import app.parivaar.panchang.core.designsystem.components.OrbUi
import app.parivaar.panchang.engine.panchang.PanchangDay
import app.parivaar.panchang.engine.panchang.Window
import app.parivaar.panchang.engine.panchang.WindowKind
import app.parivaar.panchang.feature.aaj.Header
import kotlin.time.Instant

/** Localised pieces the pure orb logic needs. [time] formats an instant in the place's time zone. */
class OrbTexts(
    val time: (Instant) -> String,
    val good: (String) -> String,
    val wait: (String) -> String,
    val neutral: String,
)

/**
 * Orb state (M07R section 6, decisions D10 and D14). Pure: reads the existing Aaj header and days, makes no engine call.
 * Tone, first match: WaitUntil header -> AVOID until its end; GoodUntil header -> GOOD until its end; else the current
 * choghadiya C: Amrit/Shubh/Labh GOOD, Char NEUTRAL, Rog/Kaal/Udveg AVOID (until C.end). The ring always shows C
 * (half-open [start, end)); with no C the orb is NEUTRAL with a null fraction (lit diya), so the dock never contradicts the header card.
 */
fun orbUi(header: Header, days: List<PanchangDay>, now: Instant, texts: OrbTexts): OrbUi {
    val c = currentChoghadiya(days, now)
    val fraction = c?.let { ((it.end - now) / (it.end - it.start)).toFloat().coerceIn(0f, 1f) }
    val (tone, until) = toneAndUntil(header, c)
    val u = until?.let(texts.time)
    val a11y = when {
        tone == OrbTone.GOOD && u != null -> texts.good(u)
        tone == OrbTone.AVOID && u != null -> texts.wait(u)
        else -> texts.neutral
    }
    return OrbUi(tone, fraction, u, a11y)
}

private fun toneAndUntil(header: Header, c: Window?): Pair<OrbTone, Instant?> = when {
    header is Header.WaitUntil -> OrbTone.AVOID to header.end
    header is Header.GoodUntil -> OrbTone.GOOD to header.until
    c == null -> OrbTone.NEUTRAL to null
    c.code in GOOD -> OrbTone.GOOD to c.end
    c.code in AVOID -> OrbTone.AVOID to c.end
    else -> OrbTone.NEUTRAL to c.end
}

private val GOOD = setOf("AMRIT", "SHUBH", "LABH")
private val AVOID = setOf("ROG", "KAAL", "UDVEG")

/** The choghadiya part running at [now] across the given days (day sunrise to sunset, night sunset to next sunrise). */
internal fun currentChoghadiya(days: List<PanchangDay>, now: Instant): Window? =
    days.asSequence().flatMap { it.windows.asSequence() }
        .firstOrNull { it.kind == WindowKind.CHOGHADIYA && it.start <= now && now < it.end }
