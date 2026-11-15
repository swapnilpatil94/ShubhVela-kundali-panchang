@file:Suppress("MaxLineLength", "MatchingDeclarationName", "TooManyFunctions") // Compose layout code, wrapped by the formatter later

package app.parivaar.panchang.feature.aaj.ui

import android.content.res.Resources
import app.parivaar.panchang.core.designsystem.components.ArcSegment
import app.parivaar.panchang.core.designsystem.components.TimeQuality
import app.parivaar.panchang.engine.panchang.PanchangDay
import app.parivaar.panchang.engine.panchang.Window
import app.parivaar.panchang.engine.panchang.WindowKind
import app.parivaar.panchang.feature.aaj.Header
import app.parivaar.panchang.feature.aaj.HeaderRule
import app.parivaar.panchang.feature.aaj.NextPhrase
import app.parivaar.panchang.feature.aaj.NextWindowRule
import app.parivaar.panchang.feature.aaj.nextPhrase
import app.parivaar.panchang.feature.aaj.R
import kotlin.time.Instant

/** The verdict sentence of tier 1 (also read aloud). */
fun headerText(res: Resources, names: AajNames, fmt: TimeFmt, h: Header): String = when (h) {
    is Header.GoodUntil -> res.getString(R.string.aaj_header_good, fmt.stamp(res, h.until))
    is Header.WaitUntil ->
        if (h.nextGood != null) {
            res.getString(R.string.aaj_header_wait, names.window(h.kind), fmt.stamp(res, h.end), fmt.stamp(res, h.nextGood))
        } else {
            res.getString(R.string.aaj_header_wait_no_next, names.window(h.kind), fmt.stamp(res, h.end))
        }
    is Header.NextGood -> res.getString(R.string.aaj_header_next_good, fmt.stamp(res, h.start), fmt.stamp(res, h.end))
    is Header.TomorrowFrom ->
        if (h.start != null) {
            when (nextPhrase(fmt.refOf(h.start))) {
                NextPhrase.LATER_TODAY -> res.getString(R.string.aaj_header_resume, fmt.time(h.start))
                NextPhrase.TOMORROW -> res.getString(R.string.aaj_header_tomorrow, fmt.time(h.start))
                NextPhrase.DATED -> res.getString(R.string.aaj_header_tomorrow, fmt.stamp(res, h.start))
            }
        } else {
            res.getString(R.string.aaj_header_tomorrow_none)
        }
    Header.Unavailable -> res.getString(R.string.aaj_header_unavailable)
}

fun qualityOf(kind: WindowKind): TimeQuality =
    if (kind in HeaderRule.INTERVALS) TimeQuality.INAUSPICIOUS else TimeQuality.AUSPICIOUS

fun qualityOfChoghadiya(code: String?): TimeQuality = when (code) {
    in HeaderRule.GOOD_CODES -> TimeQuality.AUSPICIOUS
    "UDVEG", "ROG", "KAAL" -> TimeQuality.INAUSPICIOUS
    else -> TimeQuality.NEUTRAL
}

/** Arc data over sunrise..sunset of [day]: good (green) and waiting (amber) stretches and the now marker. */
data class ArcData(val segments: List<ArcSegment>, val nowFraction: Float?)

fun arcOf(day: PanchangDay, now: Instant): ArcData {
    val total = (day.sunset - day.sunrise).inWholeMilliseconds.toFloat()
    fun frac(t: Instant) = ((t - day.sunrise).inWholeMilliseconds / total)
    fun seg(w: Window, q: TimeQuality) = ArcSegment(frac(w.start), frac(w.end), q)
    val good = day.windows.filter {
        (it.kind == WindowKind.CHOGHADIYA && it.code in HeaderRule.GOOD_CODES) || it.kind == WindowKind.ABHIJIT
    }.map { seg(it, TimeQuality.AUSPICIOUS) }
    val wait = day.windows.filter { it.kind in HeaderRule.INTERVALS }.map { seg(it, TimeQuality.INAUSPICIOUS) }
    val nowF = frac(now).takeIf { now >= day.sunrise && now <= day.sunset }
    return ArcData((good + wait).filter { it.to > 0f && it.from < 1f }, nowF)
}

/** Windows of [day] that Tier 2 lists, by start time. */
fun shownWindows(day: PanchangDay): List<Window> =
    day.windows.filter { it.kind in NextWindowRule.SHOWN }.sortedBy { it.start }
