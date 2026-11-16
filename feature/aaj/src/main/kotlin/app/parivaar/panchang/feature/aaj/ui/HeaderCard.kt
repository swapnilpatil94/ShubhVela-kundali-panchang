@file:Suppress("MaxLineLength", "MatchingDeclarationName", "TooManyFunctions", "LongParameterList", "MagicNumber")

package app.parivaar.panchang.feature.aaj.ui

import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.parivaar.panchang.core.designsystem.PpTheme
import app.parivaar.panchang.core.designsystem.planets.PlanetView
import app.parivaar.panchang.core.designsystem.sky.GemChip
import app.parivaar.panchang.core.designsystem.sky.SkyHeader
import app.parivaar.panchang.core.designsystem.sky.SkyHeaderUi
import app.parivaar.panchang.core.designsystem.sky.SkySource
import app.parivaar.panchang.core.designsystem.sky.SkyText
import app.parivaar.panchang.core.designsystem.tokens.Gem
import app.parivaar.panchang.core.designsystem.tokens.PpSpace
import app.parivaar.panchang.core.designsystem.tokens.PpType
import app.parivaar.panchang.core.designsystem.tokens.gemFor
import app.parivaar.panchang.engine.panchang.WindowKind
import app.parivaar.panchang.feature.aaj.AajUi
import app.parivaar.panchang.feature.aaj.HoraSky
import app.parivaar.panchang.feature.aaj.R
import app.parivaar.panchang.feature.aaj.horaBodyAt
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import app.parivaar.panchang.core.designsystem.R as DsR

/** Time-of-day greeting (no personal name: nothing personal is shown). Hour in the place's local time. */
internal fun greetingRes(localHour: Int): Int = when (localHour) {
    in 4..11 -> R.string.aaj_greet_morning
    in 12..16 -> R.string.aaj_greet_afternoon
    in 17..20 -> R.string.aaj_greet_evening
    else -> R.string.aaj_greet_night
}

internal fun localHour(now: Instant, offsetSeconds: Int): Int =
    Math.floorMod(Math.floorDiv(now.epochSeconds + offsetSeconds, 3600L), 24L).toInt()

/** Everything the header shows, resolved from real engine data. */
internal class HeaderUi(
    val gem: Gem,
    val chip: String,
    val eyebrow: String,
    val title: String,
    val meta: AnnotatedString,
    val horaBody: app.parivaar.panchang.core.designsystem.planets.PlanetBody?,
    val horaName: String?,
)

internal fun headerUi(
    res: Resources,
    names: AajNames,
    fmt: TimeFmt,
    c: AajUi.Content,
    now: Instant,
    place: String?,
    horaLordColor: androidx.compose.ui.graphics.Color,
): HeaderUi {
    // Vaar and gem belong to the sunrise-to-sunrise day containing now, not the civil date.
    val dow: DayOfWeek = vaarAt(c, now).dayOfWeek()
    val gem = gemFor(dow)
    val vaar = names.vaar(dow.ordinal + 1) // kotlinx DayOfWeek.ordinal: Monday = 0, so +1 gives ISO 1..7
    val chip = res.getString(R.string.aaj_gem_chip, vaar, names.gem(gem.ordinal))
    val date = fmt.dateShort(c.date)
    val eyebrow = if (place.isNullOrBlank()) date else res.getString(R.string.aaj_eyebrow_place_date, place, date)
    val title = res.getString(greetingRes(localHour(now, fmt.offsetAt(now))))
    val hora = c.days.asSequence().flatMap { it.windows.asSequence() }
        .firstOrNull { it.kind == WindowKind.HORA && it.start <= now && now < it.end }
    val meta = if (hora != null) {
        val lord = names.graha(hora.code)
        val text = res.getString(R.string.aaj_meta_hora, lord, fmt.time(hora.end))
        val at = text.indexOf(lord)
        buildAnnotatedString {
            append(text)
            if (lord.isNotEmpty() && at >= 0) addStyle(SpanStyle(color = horaLordColor, fontWeight = FontWeight.ExtraBold), at, at + lord.length)
        }
    } else {
        AnnotatedString(fmt.date(c.date))
    }
    return HeaderUi(gem, chip, eyebrow, title, meta, horaBodyAt(c.days, now), hora?.let { names.graha(it.code) })
}

/**
 * Night: the sky header (gem-tinted placeholder until the owner's images arrive) with the hora planet and the gem chip.
 * Chandan (light): no sky (a dark sky on ivory would end in a hard edge); eyebrow, display title and the same chip on the page.
 */
@Composable
internal fun AajHeader(
    ui: HeaderUi,
    sky: HoraSky?,
    modifier: Modifier = Modifier,
) {
    val r = PpTheme.ratna
    if (r.isDark) {
        val planet: (@Composable () -> Unit)? = ui.horaBody?.let { body ->
            {
                val s = sky?.takeIf { it.body == body }
                if (s != null) {
                    PlanetView(
                        s.body, s.light, 112.dp, animated = true,
                        contentDescription = stringResource(R.string.aaj_planet_cd_hora, ui.horaName.orEmpty()),
                    )
                }
            }
        }
        SkyHeader(
            SkyHeaderUi(ui.gem, ui.eyebrow, ui.title, ui.meta), modifier,
            source = SkySource.Placeholder(ui.gem), planet = planet,
        ) { GemChip(ui.gem, ui.chip, Modifier.padding(top = PpSpace.s10)) }
    } else {
        Column(modifier.fillMaxWidth().statusBarsPadding().padding(start = PpSpace.screen, end = PpSpace.screen, top = 56.dp, bottom = PpSpace.s)) {
            Text(ui.eyebrow.uppercase(), style = PpType.eyebrow.copy(fontWeight = FontWeight.ExtraBold), color = r.gold)
            Text(ui.title, Modifier.padding(top = PpSpace.xs).semantics { heading() }, style = PpType.displayL, color = r.text)
            Text(ui.meta, Modifier.padding(top = PpSpace.xs), style = PpType.meta.copy(fontWeight = FontWeight.SemiBold), color = r.text2)
            GemChip(ui.gem, ui.chip, Modifier.padding(top = PpSpace.s10))
        }
    }
}

/** Search and profile entry points over the header (48 dp targets). The scroll content passes under them. */
@Composable
internal fun AajChrome(avatarInitials: String?, onSearch: () -> Unit, onAvatar: () -> Unit, modifier: Modifier = Modifier) {
    val r = PpTheme.ratna
    val tint = if (r.isDark) r.onSky else r.text
    val menuLabel = stringResource(DsR.string.cd_profile_menu)
    Row(modifier.statusBarsPadding().padding(horizontal = PpSpace.s), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PpSpace.xs)) {
        IconButton(onSearch) {
            Icon(painterResource(DsR.drawable.ic_search), contentDescription = stringResource(DsR.string.cd_search), tint = tint)
        }
        Box(
            Modifier.size(PpSpace.minTouchTarget).background(r.lacquerTop, CircleShape)
                .clickable(onClickLabel = menuLabel, role = Role.Button, onClick = onAvatar)
                .semantics { contentDescription = menuLabel; role = Role.Button },
            contentAlignment = Alignment.Center,
        ) {
            if (avatarInitials.isNullOrBlank()) {
                Icon(painterResource(DsR.drawable.ic_settings), contentDescription = null, tint = tint)
            } else {
                Text(avatarInitials, style = PpType.label, color = tint, maxLines = 1)
            }
        }
    }
}

/**
 * Hora-lord span colour. On the sky (night) the gem lightened just enough for 7:1 over the worst capped sky
 * ([SkyText.horaLordArgb]); on Chandan (ivory, no sky) the meta colour is kept so the span never loses contrast.
 */
internal fun horaLordColor(gem: Gem, onSky: Boolean): androidx.compose.ui.graphics.Color =
    if (onSky) androidx.compose.ui.graphics.Color(SkyText.horaLordArgb(gem)) else androidx.compose.ui.graphics.Color.Unspecified

@Composable
internal fun rememberHeaderUi(c: AajUi.Content, now: Instant, names: AajNames, fmt: TimeFmt, place: String?): HeaderUi {
    val res = LocalContext.current.resources
    val minute = now.epochSeconds / MINUTE
    val gem = gemFor(vaarAt(c, now).dayOfWeek())
    val dark = PpTheme.ratna.isDark
    return remember(c, minute, names, fmt, place, dark) { headerUi(res, names, fmt, c, now, place, horaLordColor(gem, dark)) }
}

private const val MINUTE = 60L
