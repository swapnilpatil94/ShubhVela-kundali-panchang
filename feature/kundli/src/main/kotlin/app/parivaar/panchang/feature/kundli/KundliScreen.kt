@file:Suppress("TooManyFunctions")

package app.parivaar.panchang.feature.kundli

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.parivaar.panchang.core.designsystem.PpTheme
import app.parivaar.panchang.core.designsystem.chart.ChartA11y
import app.parivaar.panchang.core.designsystem.chart.ChartGeometry
import app.parivaar.panchang.core.designsystem.chart.ChartStyle
import app.parivaar.panchang.core.designsystem.chart.ChartView
import app.parivaar.panchang.core.designsystem.chart.DashaTimeline
import app.parivaar.panchang.core.designsystem.components.EmptyState
import app.parivaar.panchang.core.designsystem.components.LoadingState
import app.parivaar.panchang.core.designsystem.components.PpButton
import app.parivaar.panchang.core.designsystem.components.PpButtonStyle
import app.parivaar.panchang.core.designsystem.family.FamilyAvatar
import app.parivaar.panchang.core.designsystem.family.FamilyAvatars
import app.parivaar.panchang.core.designsystem.family.initialsOf
import app.parivaar.panchang.core.designsystem.surfaces.Lacquer
import app.parivaar.panchang.core.designsystem.tokens.PpShapes
import app.parivaar.panchang.core.designsystem.tokens.PpSpace
import app.parivaar.panchang.core.designsystem.tokens.PpType
import app.parivaar.panchang.core.model.profile.Profile
import app.parivaar.panchang.engine.jyotish.RuleStatus
import app.parivaar.panchang.engine.jyotish.SadeSatiPhase
import app.parivaar.panchang.engine.jyotish.Zodiac
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Stable avatar list for the profile switcher (names on screen only). */
fun avatarsOf(ps: List<Profile>, me: String): List<FamilyAvatar> =
    ps.mapIndexed { i, p ->
        val label = if (p.relation == app.parivaar.panchang.core.model.profile.Relation.SELF) me else p.displayName
        FamilyAvatar(p.id.hex, initialsOf(p.displayName), label, i)
    }

/** Kundli tab (PNG 13): profile switcher, D1/D9 chips with a style menu, five tabs, notes and the footer. No ads. */
@Composable
fun KundliScreen(
    state: KundliState,
    onEvent: (KundliEvent) -> Unit,
    onAddProfile: () -> Unit,
    onEditProfile: (Profile) -> Unit,
    onOpenParivaar: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenRashifal: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val names = remember(ctx) { KundliNames(ctx.resources) }
    val r = PpTheme.ratna
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = PpSpace.screen)) {
        KundliHeader(state, names, onOpenParivaar)
        Spacer(Modifier.height(PpSpace.m))
        if (state.profiles.isEmpty()) {
            EmptyState(stringResource(R.string.kundli_empty_title), stringResource(R.string.kundli_empty_body))
            PpButton(stringResource(R.string.kundli_add_profile), onAddProfile, modifier = Modifier.fillMaxWidth())
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            FamilyAvatars(
                avatarsOf(state.profiles, stringResource(R.string.kundli_me)), state.selectedId?.hex,
                { hex -> state.profiles.firstOrNull { it.id.hex == hex }?.let { onEvent(KundliEvent.Select(it.id)) } },
                Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(PpSpace.s)) {
            state.selected?.let { p -> Pill(stringResource(R.string.kundli_edit), false) { onEditProfile(p) } }
            Pill(stringResource(R.string.kundli_add_profile), false, onAddProfile)
        }
        Spacer(Modifier.height(PpSpace.m))
        KundliBody(state, names, onEvent)
        if (state.bundle != null && state.tab == KundliTab.CHART) {
            Spacer(Modifier.height(PpSpace.m))
            PpButton(
                stringResource(R.string.kundli_rashifal), onOpenRashifal, PpButtonStyle.Outline,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(BOTTOM_INSET))
    }
}

@Composable
private fun KundliHeader(state: KundliState, names: KundliNames, onOpenParivaar: () -> Unit) {
    val r = PpTheme.ratna
    Spacer(Modifier.height(PpSpace.l))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            state.selected?.let { p ->
                Text(
                    stringResource(
                        R.string.kundli_eyebrow, p.displayName.uppercase(), names.date(p.birth.date).uppercase(),
                    ),
                    style = PpType.eyebrow, color = r.text2,
                )
            }
            Text(
                stringResource(R.string.kundli_title), style = PpType.displayL, color = r.text,
                modifier = Modifier.semantics { heading() },
            )
        }
        PpButton(stringResource(R.string.kundli_family), onOpenParivaar, PpButtonStyle.Outline)
    }
}

@Composable
private fun KundliBody(state: KundliState, names: KundliNames, onEvent: (KundliEvent) -> Unit) {
    val b = state.bundle
    when {
        state.loading && b == null -> LoadingState(stringResource(R.string.kundli_loading))
        state.failed || b == null -> EmptyState(
            stringResource(R.string.kundli_error_title), stringResource(R.string.kundli_error_body),
        )
        else -> {
            Notes(state)
            TabRow(state.tab) { onEvent(KundliEvent.Tab(it)) }
            Spacer(Modifier.height(PpSpace.m))
            when (state.tab) {
                KundliTab.CHART -> ChartTabContent(state, b, names, onEvent)
                KundliTab.PLANETS -> PlanetsTab(b, state.noon, names)
                KundliTab.DASHA -> DashaTab(state, b, names, onEvent)
                KundliTab.YOGAS -> YogasTab(b)
                KundliTab.GOCHAR -> GocharTab(b, state.zone, names)
            }
        }
    }
}

@Composable
private fun Pill(text: String, on: Boolean, onClick: () -> Unit) {
    val r = PpTheme.ratna
    Text(
        text,
        style = PpType.label,
        color = if (on) r.onGoldFill else r.text,
        modifier = Modifier
            .heightIn(min = PpSpace.minTouchTarget)
            .padding(vertical = PpSpace.xs)
            .border(1.dp, if (on) r.goldFill else r.line, PpShapes.pill)
            .then(if (on) Modifier.background(r.goldFill, PpShapes.pill) else Modifier)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { selected = on }
            .padding(horizontal = PpSpace.l, vertical = PpSpace.s10),
    )
}

@Composable
private fun TabRow(tab: KundliTab, onTab: (KundliTab) -> Unit) {
    val labels = listOf(
        R.string.kundli_tab_chart, R.string.kundli_tab_planets, R.string.kundli_tab_dasha, R.string.kundli_tab_yogas,
        R.string.kundli_tab_gochar,
    )
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(PpSpace.s)) {
        KundliTab.entries.forEachIndexed { i, t -> Pill(stringResource(labels[i]), t == tab) { onTab(t) } }
    }
}

@Composable
private fun Notes(state: KundliState) {
    if (state.notes.isEmpty()) return
    val r = PpTheme.ratna
    Column(Modifier.padding(bottom = PpSpace.s)) {
        for (n in state.notes.sortedBy { it.ordinal }) {
            Text("• " + stringResource(noteText(n)), style = PpType.meta, color = r.text2)
        }
    }
}

private fun noteText(n: KundliNote): Int = when (n) {
    KundliNote.NOON_CHART -> R.string.kundli_note_noon
    KundliNote.AYANAMSA_FALLBACK -> R.string.kundli_note_ayanamsa
    KundliNote.HOUSE_FALLBACK -> R.string.kundli_note_house
    KundliNote.HISTORICAL_OFFSET -> R.string.kundli_note_historical
    KundliNote.LMT_OFFSET -> R.string.kundli_note_lmt
    KundliNote.MANUAL_OFFSET -> R.string.kundli_note_manual
    KundliNote.DST_CHOSEN -> R.string.kundli_note_dst
}

@Composable
private fun ChartTabContent(state: KundliState, b: KundliBundle, names: KundliNames, onEvent: (KundliEvent) -> Unit) {
    var menu by rememberSaveable { mutableStateOf(false) }
    val styleLabels = mapOf(
        ChartStyle.NORTH to stringResource(R.string.kundli_style_north),
        ChartStyle.SOUTH to stringResource(R.string.kundli_style_south),
        ChartStyle.EAST to stringResource(R.string.kundli_style_east),
    )
    val used = ChartGeometry.effective(state.style, !state.noon)
    Row(horizontalArrangement = Arrangement.spacedBy(PpSpace.s)) {
        Pill(
            stringResource(R.string.kundli_chip_d1), state.chart == ChartTab.D1,
        ) { onEvent(KundliEvent.Chart(ChartTab.D1)) }
        Pill(
            stringResource(R.string.kundli_chip_d9), state.chart == ChartTab.D9,
        ) { onEvent(KundliEvent.Chart(ChartTab.D9)) }
        Box {
            Pill(styleLabels.getValue(used) + " ▾", false) { menu = true }
            DropdownMenu(menu, { menu = false }) {
                for (s in ChartGeometry.available(!state.noon)) {
                    DropdownMenuItem({ Text(styleLabels.getValue(s)) }, { menu = false; onEvent(KundliEvent.Style(s)) })
                }
            }
        }
    }
    Spacer(Modifier.height(PpSpace.m))
    val model = if (state.chart == ChartTab.D1) {
        ChartModels.d1(b.facts, state.noon, names::glyph)
    } else {
        ChartModels.d9(b.d9, state.noon, names::glyph) { b.facts.of(it).retrograde }
    }
    val summary = stringResource(
        R.string.kundli_chart_summary,
        stringResource(if (state.chart == ChartTab.D1) R.string.kundli_chip_d1 else R.string.kundli_chip_d9),
        if (state.noon) stringResource(R.string.kundli_lagna_unknown) else names.rashi(model.cells[0].rashi),
    )
    Lacquer {
        ChartView(model, used, { ChartA11y.describe(it, names, model.lagnaKnown) }, summary = summary)
    }
    Spacer(Modifier.height(PpSpace.m))
    CurrentPeriod(b, names, state.noon) { onEvent(KundliEvent.Tab(KundliTab.DASHA)) }
}

@Composable
private fun CurrentPeriod(b: KundliBundle, names: KundliNames, noon: Boolean, onOpen: () -> Unit) {
    val r = PpTheme.ratna
    val chain = b.currentDasha
    if (chain.size < 2) return
    Lacquer(onClick = onOpen) {
        Text(stringResource(R.string.kundli_current_period), style = PpType.eyebrow, color = r.text3)
        Text(
            chain.take(HEADER_LEVELS).joinToString(" › ") { names.graha(it.lords.last().ordinal) },
            style = PpType.displayS, color = r.text,
        )
        val antar = chain[1]
        val zone = TimeZone.currentSystemDefault()
        Text(
            stringResource(
                R.string.kundli_antar_till, names.graha(antar.lords.last().ordinal),
                names.date(antar.end.toLocalDateTime(zone).date),
            ),
            style = PpType.meta, color = r.text2,
        )
        if (noon) Text(stringResource(R.string.kundli_dasha_approx), style = PpType.meta, color = r.kumkum)
    }
}

@Composable
private fun PlanetsTab(b: KundliBundle, noon: Boolean, names: KundliNames) {
    val r = PpTheme.ratna
    Lacquer(mergeSemantics = false) {
        if (!noon) {
            val asc = b.facts.ascDeg
            PlanetRow(
                stringResource(R.string.kundli_lagna_row),
                names.rashi(Zodiac.rashiOf(asc).ordinal),
                ChartModels.dms(Zodiac.degInRashi(asc)),
                names.nakshatra(Zodiac.nakshatraOf(asc)),
                Zodiac.padaOf(asc),
                names.graha(Zodiac.nakshatraLord(Zodiac.nakshatraOf(asc)).ordinal),
                false,
            )
        }
        for (g in b.facts.grahas) {
            PlanetRow(
                names.graha(g.graha.ordinal),
                names.rashi(g.rashi.ordinal),
                ChartModels.dms(g.degInRashi),
                names.nakshatra(g.nakshatra),
                g.pada, names.graha(Zodiac.nakshatraLord(g.nakshatra).ordinal), g.retrograde,
            )
        }
    }
    Spacer(Modifier.height(PpSpace.m))
    val a = b.avakhada
    Lacquer {
        Text(stringResource(R.string.kundli_avakhada), style = PpType.eyebrow, color = r.text3)
        Text(
            stringResource(
                R.string.kundli_avakhada_line, names.rashi(a.moonRashi.ordinal), names.nakshatra(a.nakshatra),
                a.pada.toString(), names.graha(a.nakshatraLord.ordinal),
            ),
            style = PpType.body, color = r.text,
        )
        Text(
            stringResource(
                R.string.kundli_avakhada_attrs, AvakhadaText.gana(a.gana), AvakhadaText.nadi(a.nadi),
                AvakhadaText.yoni(a.yoni),
            ),
            style = PpType.meta, color = r.text2,
        )
        Text(stringResource(R.string.kundli_under_review), style = PpType.meta, color = r.text3)
    }
}

@Composable
private fun PlanetRow(name: String, rashi: String, deg: String, nak: String, pada: Int, lord: String, retro: Boolean) {
    val r = PpTheme.ratna
    val retroText = stringResource(R.string.kundli_retro_short)
    Column(Modifier.fillMaxWidth().padding(vertical = PpSpace.xs).semantics(mergeDescendants = true) {}) {
        Text(
            if (retro) "$name ($retroText)" else name, style = PpType.body.copy(fontWeight = FontWeight.Bold),
            color = r.text,
        )
        Text(
            stringResource(R.string.kundli_planet_line, rashi, deg, nak, pada.toString(), lord), style = PpType.meta,
            color = r.text2,
        )
    }
}

@Composable
private fun DashaTab(state: KundliState, b: KundliBundle, names: KundliNames, onEvent: (KundliEvent) -> Unit) {
    val rows = remember(b, state.expanded, state.children, state.zone) {
        DashaRows.build(b.maha, state.children, state.expanded, kotlin.time.Clock.System.now(), state.zone)
    }
    if (state.noon) Text(
        stringResource(R.string.kundli_dasha_approx), style = PpType.meta, color = PpTheme.ratna.kumkum,
    )
    Lacquer(mergeSemantics = false) {
        DashaTimeline(rows, { names.graha(it) }, { onEvent(KundliEvent.ToggleDasha(it)) }, formatDate = names::date)
    }
}

@Composable
private fun Footer() {
    Text(
        stringResource(R.string.kundli_footer), style = PpType.meta, color = PpTheme.ratna.text3,
        modifier = Modifier.padding(top = PpSpace.m),
    )
}

@Composable
private fun YogasTab(b: KundliBundle) {
    val r = PpTheme.ratna
    Lacquer(mergeSemantics = false) {
        for (y in b.yogas) {
            Column(Modifier.fillMaxWidth().padding(vertical = PpSpace.xs).semantics(mergeDescendants = true) {}) {
                Text(
                    stringResource(YogaText.name(y.ruleId)), style = PpType.body.copy(fontWeight = FontWeight.Bold),
                    color = r.text,
                )
                val statusRes = if (y.present) R.string.kundli_yoga_present else R.string.kundli_yoga_absent
                val status = stringResource(statusRes)
                val extra = buildList {
                    if (y.ruleId.startsWith("MANGLIK")) add(stringResource(R.string.kundli_yoga_variant, y.variant))
                    if (y.status == RuleStatus.VERIFY) add(stringResource(R.string.kundli_under_review))
                }
                Text(
                    (listOf(status) + extra).joinToString(" · "), style = PpType.meta,
                    color = if (y.present) r.gold else r.text2,
                )
            }
        }
    }
    Footer()
}

@Composable
private fun GocharTab(b: KundliBundle, zone: TimeZone, names: KundliNames) {
    val r = PpTheme.ratna
    Lacquer {
        Text(stringResource(R.string.kundli_sade_sati), style = PpType.eyebrow, color = r.text3)
        Text(stringResource(SadeSatiText.of(b.sadeSati)), style = PpType.displayS, color = r.text)
        for (s in b.sadeSatiTimeline) {
            Text(
                stringResource(
                    R.string.kundli_sade_sati_step, names.date(s.at.toLocalDateTime(zone).date),
                    stringResource(SadeSatiText.of(s.phase)),
                ),
                style = PpType.meta, color = r.text2,
            )
        }
    }
    Spacer(Modifier.height(PpSpace.m))
    Lacquer(mergeSemantics = false) {
        Text(stringResource(R.string.kundli_transits), style = PpType.eyebrow, color = r.text3)
        for (t in b.transits) {
            Text(
                stringResource(
                    R.string.kundli_transit_line, names.graha(t.graha.ordinal), names.rashi(t.rashi.ordinal),
                    t.houseFromMoon.toString(), if (t.retrograde) stringResource(R.string.kundli_retro_short) else "",
                ).trim(),
                style = PpType.meta, color = r.text,
            )
        }
    }
    Footer()
}

/** Sade Sati phase names (no fear language). */
object SadeSatiText {
    fun of(p: SadeSatiPhase): Int = when (p) {
        SadeSatiPhase.NONE -> R.string.kundli_ss_none
        SadeSatiPhase.RISING -> R.string.kundli_ss_rising
        SadeSatiPhase.PEAK -> R.string.kundli_ss_peak
        SadeSatiPhase.SETTING -> R.string.kundli_ss_setting
        SadeSatiPhase.DHAIYA_4TH -> R.string.kundli_ss_dhaiya4
        SadeSatiPhase.DHAIYA_8TH -> R.string.kundli_ss_dhaiya8
    }
}

/** Yoga/dosha names keyed by rule id (rule 6); an unknown id shows a generic label, never the raw id. */
object YogaText {
    private val names = mapOf(
        "MANGLIK_LAGNA" to R.string.yoga_manglik_lagna,
        "MANGLIK_MOON" to R.string.yoga_manglik_moon,
        "MANGLIK_VENUS" to R.string.yoga_manglik_venus,
        "KAAL_SARP" to R.string.yoga_kaal_sarp,
        "KEMADRUMA" to R.string.yoga_kemadruma,
        "GAJAKESARI" to R.string.yoga_gajakesari,
        "BUDHADITYA" to R.string.yoga_budhaditya,
        "CHANDRA_MANGALA" to R.string.yoga_chandra_mangala,
        "RUCHAKA" to R.string.yoga_ruchaka,
        "BHADRA" to R.string.yoga_bhadra,
        "HAMSA" to R.string.yoga_hamsa,
        "MALAVYA" to R.string.yoga_malavya,
        "SHASHA" to R.string.yoga_shasha,
        "DHARMA_KARMADHIPATI" to R.string.yoga_dharma_karmadhipati,
        "DHANA_2_11" to R.string.yoga_dhana_2_11,
    )

    fun name(id: String): Int = names[id] ?: R.string.yoga_unknown
}

/** Avakhada attribute ids to resource text. */
object AvakhadaText {
    @Composable fun gana(id: String): String = stringResource(when (id) {
        "DEVA" -> R.string.av_gana_deva
        "MANUSHYA" -> R.string.av_gana_manushya
        else -> R.string.av_gana_rakshasa
    })

    @Composable fun nadi(id: String): String = stringResource(when (id) {
        "ADI" -> R.string.av_nadi_adi
        "MADHYA" -> R.string.av_nadi_madhya
        else -> R.string.av_nadi_antya
    })

    @Composable fun yoni(id: String): String {
        val ids = listOf(
            "BUFFALO", "CAT", "COW", "DEER", "DOG", "ELEPHANT", "HORSE", "LION", "MONGOOSE", "MONKEY", "RAT", "SERPENT",
            "SHEEP", "TIGER",
        )
        val arr = androidx.compose.ui.res.stringArrayResource(R.array.kundli_yoni_names)
        return arr.getOrNull(ids.indexOf(id)) ?: stringResource(R.string.yoga_unknown)
    }
}


/** Room for the dock below the last card. */
internal val BOTTOM_INSET = 96.dp

/** Dasha levels in the current-period header ("Guru › Budh › Shukra"). */
private const val HEADER_LEVELS = 3
