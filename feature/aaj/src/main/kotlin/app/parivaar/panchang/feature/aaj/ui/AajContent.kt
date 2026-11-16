@file:Suppress("MaxLineLength", "MatchingDeclarationName", "TooManyFunctions", "LongParameterList")

package app.parivaar.panchang.feature.aaj.ui

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import app.parivaar.panchang.core.designsystem.PpTheme
import app.parivaar.panchang.core.designsystem.components.ExplainSheet
import app.parivaar.panchang.core.designsystem.components.LocalDockInset
import app.parivaar.panchang.core.designsystem.components.OrbTone
import app.parivaar.panchang.core.designsystem.components.PpButton
import app.parivaar.panchang.core.designsystem.components.PpButtonStyle
import app.parivaar.panchang.core.designsystem.planets.PlanetBody
import app.parivaar.panchang.core.designsystem.planets.ProvidePlanetBudget
import app.parivaar.panchang.core.designsystem.tokens.PpSpace
import app.parivaar.panchang.engine.calendar.EclipseKind
import app.parivaar.panchang.engine.panchang.LimbKind
import app.parivaar.panchang.engine.panchang.LimbSpan
import app.parivaar.panchang.engine.panchang.PanchangDay
import app.parivaar.panchang.engine.panchang.WindowKind
import app.parivaar.panchang.feature.aaj.AajUi
import app.parivaar.panchang.feature.aaj.Header
import app.parivaar.panchang.feature.aaj.HeaderRule
import app.parivaar.panchang.feature.aaj.HoraSky
import app.parivaar.panchang.feature.aaj.R
import app.parivaar.panchang.feature.aaj.travel.TravelUi
import kotlin.time.Instant

internal fun abhiStateOf(h: Header): AbhiState = when (h) {
    is Header.GoodUntil -> AbhiState.GOOD
    is Header.WaitUntil -> AbhiState.WAIT
    else -> AbhiState.NEUTRAL
}

internal fun toneOfChoghadiya(code: String?): OrbTone = when (code) {
    in HeaderRule.GOOD_CODES -> OrbTone.GOOD
    "UDVEG", "ROG", "KAAL" -> OrbTone.AVOID
    else -> OrbTone.NEUTRAL
}

/** The eight day parts as fractions of sunrise..sunset, in order. */
internal fun pathSegments(day: PanchangDay, names: AajNames): List<PathSeg> {
    val total = (day.sunset - day.sunrise).inWholeMilliseconds.toFloat()
    fun frac(t: Instant) = (t - day.sunrise).inWholeMilliseconds / total
    return day.windows.filter { it.kind == WindowKind.CHOGHADIYA && !it.night }.sortedBy { it.start }
        .map { PathSeg(frac(it.start), frac(it.end), toneOfChoghadiya(it.code), names.choghadiya(it.code)) }
}

internal fun abhijitFrac(day: PanchangDay): ClosedFloatingPointRange<Float>? {
    val w = day.windows.firstOrNull { it.kind == WindowKind.ABHIJIT } ?: return null
    val total = (day.sunset - day.sunrise).inWholeMilliseconds.toFloat()
    return ((w.start - day.sunrise).inWholeMilliseconds / total)..((w.end - day.sunrise).inWholeMilliseconds / total)
}

private fun pathDescription(res: Resources, names: AajNames, day: PanchangDay, now: Instant, fmt: TimeFmt, nowFrac: Float?): String {
    val parts = day.windows.filter { it.kind == WindowKind.CHOGHADIYA && !it.night }.sortedBy { it.start }.joinToString(" ") { w ->
        val tone = when (toneOfChoghadiya(w.code)) {
            OrbTone.GOOD -> res.getString(R.string.aaj_arc_good)
            OrbTone.AVOID -> res.getString(R.string.aaj_arc_wait)
            OrbTone.NEUTRAL -> res.getString(R.string.aaj_not_rated)
        }
        res.getString(R.string.aaj_path_cd_part, names.choghadiya(w.code), fmt.time(w.start), fmt.time(w.end), tone)
    }
    val nowText = if (nowFrac != null) res.getString(R.string.aaj_path_cd_now, fmt.time(now)) else ""
    return res.getString(R.string.aaj_path_cd, fmt.time(day.sunrise), fmt.time(day.sunset), (nowText + " " + parts).trim())
}

/** Rahu Kaal of the shown day (or "already passed") and the best window still ahead. */
internal fun nextRows(res: Resources, names: AajNames, fmt: TimeFmt, c: AajUi.Content, now: Instant): List<NextRowUi> {
    val rows = ArrayList<NextRowUi>(2)
    val rahu = c.day.windows.firstOrNull { it.kind == WindowKind.RAHU_KAAL }
    if (rahu != null) {
        val range = if (rahu.end <= now) res.getString(R.string.aaj_next_rahu_passed) else fmt.range(res, rahu.start, rahu.end)
        rows += NextRowUi(OrbTone.AVOID, res.getString(R.string.aaj_next_rahu), range)
    }
    val ahead = c.days.asSequence().flatMap { it.windows.asSequence() }
        .filter { it.kind == WindowKind.CHOGHADIYA && it.end > now && it.code in HeaderRule.GOOD_CODES }.sortedBy { it.start }.toList()
    val best = ahead.firstOrNull { it.code == "AMRIT" } ?: ahead.firstOrNull()
    if (best != null) rows += NextRowUi(OrbTone.GOOD, res.getString(R.string.aaj_next_best, names.choghadiya(best.code)), fmt.range(res, best.start, best.end))
    if (rows.isEmpty()) rows += NextRowUi(OrbTone.NEUTRAL, res.getString(R.string.aaj_next_none), "")
    return rows
}

/**
 * The span of [k] running at [now], searched over all loaded days (D-1 included, as the widget does), so between
 * midnight and sunrise the row shows the limb actually current and its true end. Fallbacks: the span at the shown
 * day's sunrise, else its first span (both may lie ahead of [now]; the row then says "from ... till ...").
 */
internal fun currentLimb(days: List<PanchangDay>, day: PanchangDay, k: LimbKind, now: Instant): LimbSpan? =
    days.asSequence().flatMap { it.limbs[k].orEmpty().asSequence() }.firstOrNull { now in it }
        ?: day.limbs[k].orEmpty().let { spans -> spans.firstOrNull { day.sunrise in it } ?: spans.firstOrNull() }

/** One row per limb, each the span current at [now] (see [currentLimb]). */
internal fun limbRows(res: Resources, names: AajNames, fmt: TimeFmt, days: List<PanchangDay>, day: PanchangDay, now: Instant): List<LimbRowUi> =
    listOf(LimbKind.TITHI, LimbKind.NAKSHATRA, LimbKind.YOGA, LimbKind.KARANA).mapNotNull { k ->
        val span = currentLimb(days, day, k, now) ?: return@mapNotNull null
        val note = buildList {
            if (span.kshaya) add(res.getString(R.string.aaj_badge_kshaya) + ": " + res.getString(R.string.aaj_badge_kshaya_note))
            if (span.adhika) add(res.getString(R.string.aaj_badge_adhika) + ": " + res.getString(R.string.aaj_badge_adhika_note))
        }.joinToString(" ").ifEmpty { null }
        val value = if (span.start > now) {
            res.getString(R.string.aaj_limb_from_till, fmt.stamp(res, span.start), fmt.stamp(res, span.end))
        } else {
            res.getString(R.string.aaj_limb_till, fmt.stamp(res, span.end))
        }
        LimbRowUi(names.limbTitle(k), names.limbSpan(k, span.index), value, note)
    }

private fun eclipseBody(kind: EclipseKind): PlanetBody =
    if (kind.name.startsWith("SOLAR")) PlanetBody.SUN else PlanetBody.MOON

/** The Today list. Order: header, Abhi card, Surya path, next list, sky event (only on event days), panchang list, actions. */
@Suppress("CyclomaticComplexMethod", "LongMethod")
@Composable
internal fun DayContent(
    c: AajUi.Content,
    now: Instant,
    sky: HoraSky?,
    subtitle: String?,
    avatarInitials: String?,
    actions: AajActions,
    extras: AajExtras,
) {
    val names = rememberNames()
    val fmt = rememberFmt(c.day.utcOffsetSeconds, c.date, c.zoneId)
    val res = LocalContext.current.resources
    var explain by rememberSaveable { mutableStateOf(false) }
    var full by rememberSaveable { mutableStateOf(false) }
    var timings by rememberSaveable { mutableStateOf(false) }
    val day = c.day
    val locale = LocalConfiguration.current.locales[0]
    val minute = now.epochSeconds / SECONDS_PER_MINUTE
    val header = rememberHeaderUi(c, now, names, fmt, subtitle ?: extras.cityName)
    val state = abhiStateOf(c.header)
    val abhiDetail = remember(c, minute, names) {
        currentChoghadiya(c.days, now)?.let { w ->
            val left = ((w.end - now).inWholeSeconds / SECONDS_PER_MINUTE).toInt().coerceAtLeast(0)
            res.getString(R.string.aaj_abhi_detail, names.choghadiya(w.code), left)
        }
    }
    val segs = remember(day, names) { pathSegments(day, names) }
    val arc = remember(day, minute) { arcOf(day, now) }
    val rows = remember(c, minute, names, fmt) { nextRows(res, names, fmt, c, now) }
    val limbList = remember(c, minute, names, fmt) { limbRows(res, names, fmt, c.days, day, now) }
    val pathCd = remember(day, minute, names, fmt, arc.nowFraction) { pathDescription(res, names, day, now, fmt, arc.nowFraction) }
    val headline = remember(c.header, names, fmt) { headerText(res, names, fmt, c.header) }
    val bottom = LocalDockInset.current + PpSpace.l
    ProvidePlanetBudget {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                contentPadding = PaddingValues(bottom = bottom),
                verticalArrangement = Arrangement.spacedBy(PpSpace.m),
            ) {
                item { AajHeader(header, sky) }
                if (c.travel && extras.travel !is TravelUi.InPlace) item { InfoCard(stringResource(R.string.aaj_travel_banner), "", Modifier.padding(horizontal = PpSpace.screen)) }
                item {
                    Column(Modifier.padding(horizontal = PpSpace.screen), verticalArrangement = Arrangement.spacedBy(PpSpace.s)) {
                        AbhiCard(
                            state = state,
                            devaLine = when (state) {
                                AbhiState.GOOD -> stringResource(R.string.abhi_good_hi)
                                AbhiState.WAIT -> stringResource(R.string.abhi_wait_hi)
                                AbhiState.NEUTRAL -> null
                            },
                            line = headline,
                            detail = abhiDetail,
                            onClick = { explain = true },
                        )
                        Text(
                            stringResource(R.string.aaj_footer), style = MaterialTheme.typography.bodySmall,
                            color = PpTheme.ratna.text3, modifier = Modifier.padding(horizontal = PpSpace.xs),
                        )
                    }
                }
                item {
                    SuryaPath(
                        segments = segs, nowFrac = arc.nowFraction, sunriseText = stringResource(R.string.aaj_path_sunrise, fmt.time(day.sunrise)),
                        sunsetText = stringResource(R.string.aaj_path_sunset, fmt.time(day.sunset)), nowLabel = stringResource(R.string.aaj_path_now),
                        abhijit = abhijitFrac(day), a11y = pathCd,
                        onSegment = { explain = true }, modifier = Modifier.padding(horizontal = PpSpace.screen),
                    )
                }
                item {
                    NextList(
                        rows, stringResource(if (timings) R.string.aaj_next_hide_all else R.string.aaj_next_see_all), { timings = !timings },
                        Modifier.padding(horizontal = PpSpace.screen),
                    )
                }
                if (timings) item { TimingsSection(c, now, names, fmt, Modifier.padding(horizontal = PpSpace.screen)) }
                if (c.highLatitude.isNotEmpty()) {
                    item { InfoCard(stringResource(R.string.aaj_high_latitude_title), stringResource(R.string.aaj_high_latitude_body), Modifier.padding(horizontal = PpSpace.screen)) }
                }
                c.eclipse?.let { e ->
                    item {
                        val title = names.eclipse(e.kind)
                        val sub = e.visible?.let { stringResource(R.string.aaj_sky_sub, fmt.range(res, it.start, it.end)) } ?: stringResource(R.string.aaj_sky_sub_plain)
                        SkyEventCard(
                            stringResource(R.string.aaj_sky_eyebrow), title, sub, eclipseBody(e.kind),
                            a11y = stringResource(R.string.aaj_sky_cd, title, sub), onClick = { full = true },
                            modifier = Modifier.padding(horizontal = PpSpace.screen),
                        )
                    }
                }
                item {
                    Column(Modifier.padding(horizontal = PpSpace.screen), verticalArrangement = Arrangement.spacedBy(PpSpace.s)) {
                        val cal = c.calendar
                        val tithi = day.limbs[LimbKind.TITHI].orEmpty().firstOrNull { day.sunrise in it }?.index
                        SectionEyebrow(
                            if (cal != null && tithi != null) {
                                stringResource(R.string.aaj_panchang_eyebrow, names.masa(cal.amanta.masa), names.paksha(tithi), cal.samvat.vikramChaitradi)
                            } else {
                                stringResource(R.string.aaj_panchang_eyebrow_plain)
                            },
                        )
                        PanchangList(limbList, onOpen = { full = !full })
                    }
                }
                item {
                    Column(Modifier.padding(horizontal = PpSpace.screen), verticalArrangement = Arrangement.spacedBy(PpSpace.m)) {
                        if (extras.travel != TravelUi.Idle) TravelSection(extras.travel, extras)
                        ActionsRow(
                            extras,
                            script = { speechScript(res, names, fmt, c, now) },
                            card = { shareModel(res, names, fmt, c, extras, locale) },
                        )
                        PpButton(
                            stringResource(R.string.aaj_section_full) + " · " +
                                stringResource(if (full) R.string.aaj_collapse else R.string.aaj_expand),
                            { full = !full }, PpButtonStyle.Outline,
                        )
                    }
                }
                if (full) item { FullPanchang(c, names, fmt, Modifier.padding(horizontal = PpSpace.screen)) }
            }
            AajChrome(avatarInitials, actions.onSearch, actions.onAvatar, Modifier.align(Alignment.TopEnd))
        }
    }
    if (explain) {
        ExplainSheet(
            title = stringResource(R.string.aaj_explain_title),
            explanation = stringResource(R.string.aaj_explain_body),
            howCalculated = ProofCopy.how(res, c.highLatitude.isNotEmpty()),
            traditionNote = stringResource(R.string.aaj_explain_tradition),
            proof = ProofCopy.label(res, c.proof),
            onChangeConventions = { explain = false; actions.onOpenConventions() },
            onDismiss = { explain = false },
        )
    }
}

private const val SECONDS_PER_MINUTE = 60L
