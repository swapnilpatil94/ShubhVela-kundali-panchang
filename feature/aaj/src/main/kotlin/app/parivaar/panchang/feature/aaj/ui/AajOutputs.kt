@file:Suppress("MaxLineLength")

package app.parivaar.panchang.feature.aaj.ui

import android.content.res.Resources
import app.parivaar.panchang.engine.panchang.LimbKind
import app.parivaar.panchang.feature.aaj.AajUi
import app.parivaar.panchang.feature.aaj.NextWindowRule
import app.parivaar.panchang.feature.aaj.R
import app.parivaar.panchang.feature.aaj.share.ShareCardModel
import app.parivaar.panchang.feature.aaj.speech.AajSpeech
import app.parivaar.panchang.feature.aaj.speech.SpeechParts
import java.util.Locale
import kotlin.time.Instant

/** Tier 1 and tier 2 as sentences for the read-aloud engine. */
internal fun speechScript(res: Resources, names: AajNames, fmt: TimeFmt, c: AajUi.Content, now: Instant): List<String> {
    val next = NextWindowRule.next(now, c.days)?.let { n ->
        val w = n.window
        if (n.active) {
            res.getString(R.string.aaj_speech_now, names.window(w.kind), fmt.stamp(res, w.end))
        } else {
            res.getString(R.string.aaj_speech_next, names.window(w.kind), fmt.stamp(res, w.start), fmt.stamp(res, w.end))
        }
    }
    return AajSpeech.script(
        SpeechParts(
            date = res.getString(R.string.aaj_speech_date, fmt.date(c.date)),
            verdict = headerText(res, names, fmt, c.header),
            next = next,
            windows = shownWindows(c.day).map { res.getString(R.string.aaj_speech_window, names.window(it.kind), fmt.range(res, it.start, it.end)) },
            footer = res.getString(R.string.aaj_footer),
        ),
    )
}

/** The plain share card. The city appears only when the user allows it in Settings. */
internal fun shareModel(res: Resources, names: AajNames, fmt: TimeFmt, c: AajUi.Content, extras: AajExtras, locale: Locale): ShareCardModel {
    val day = c.day
    val limbs = listOf(LimbKind.TITHI, LimbKind.NAKSHATRA, LimbKind.YOGA, LimbKind.KARANA).mapNotNull { k ->
        val span = day.limbs[k]?.firstOrNull { day.sunrise >= it.start && day.sunrise < it.end } ?: return@mapNotNull null
        res.getString(
            R.string.aaj_share_limb, names.limbTitle(k),
            res.getString(R.string.aaj_limb_until, names.limbSpan(k, span.index), fmt.stamp(res, span.end)),
        )
    }
    return ShareCardModel(
        brand = res.getString(R.string.aaj_share_brand),
        date = fmt.date(c.date),
        city = extras.cityName.takeIf { extras.showCityOnShare },
        limbLines = limbs,
        verdict = headerText(res, names, fmt, c.header),
        language = res.getString(R.string.aaj_share_language, locale.getDisplayName(locale)),
        footer = res.getString(R.string.aaj_footer),
    )
}
