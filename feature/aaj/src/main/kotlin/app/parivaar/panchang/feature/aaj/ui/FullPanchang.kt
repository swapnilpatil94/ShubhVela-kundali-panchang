@file:Suppress("MaxLineLength", "MatchingDeclarationName", "TooManyFunctions") // Compose layout code, wrapped by the formatter later

package app.parivaar.panchang.feature.aaj.ui

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.parivaar.panchang.core.designsystem.PpTheme
import app.parivaar.panchang.core.designsystem.components.PpCard
import app.parivaar.panchang.core.designsystem.tokens.PpSpace
import app.parivaar.panchang.engine.calendar.LunarLabel
import app.parivaar.panchang.engine.panchang.LimbKind
import app.parivaar.panchang.feature.aaj.AajUi
import app.parivaar.panchang.feature.aaj.R

private fun masaText(res: Resources, names: AajNames, l: LunarLabel): String {
    val base = names.masa(l.masa)
    return when {
        l.adhika -> res.getString(R.string.aaj_masa_adhika, base)
        l.kshayaMasa != null -> res.getString(R.string.aaj_masa_kshaya, base)
        else -> base
    }
}

@Composable
private fun SectionTitle(text: String) = Text(
    text, style = MaterialTheme.typography.titleMedium, color = PpTheme.colors.primaryText,
    modifier = Modifier.semantics { heading() },
)

/** Tier 3: limbs with end times, sun and moon, calendar facts, festivals and an eclipse notice. */
@Composable
internal fun FullPanchang(c: AajUi.Content, names: AajNames, fmt: TimeFmt, modifier: Modifier = Modifier) {
    val res = androidx.compose.ui.platform.LocalContext.current.resources
    val day = c.day
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(PpSpace.m)) {
        LimbKind.entries.forEach { k -> LimbCard(k, day.limbs[k].orEmpty(), day, names, fmt) }
        PpCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(PpSpace.s)) {
                SectionTitle(stringResource(R.string.aaj_sun_moon))
                FactRow(stringResource(R.string.aaj_sunrise), fmt.time(day.sunrise))
                FactRow(stringResource(R.string.aaj_sunset), fmt.time(day.sunset))
                // The tithi at sunrise comes from the day's own limbs, so it needs no calendar.
                val t = day.limbs[LimbKind.TITHI]?.firstOrNull { day.sunrise in it }?.index
                if (t != null) {
                    FactRow(
                        stringResource(R.string.aaj_moon_phase),
                        res.getString(R.string.aaj_limb_paksha_name, names.paksha(t), names.tithi(t)),
                    )
                }
            }
        }
        c.calendar?.let { cal ->
            PpCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(PpSpace.s)) {
                    SectionTitle(stringResource(R.string.aaj_calendar))
                    FactRow(stringResource(R.string.aaj_masa_amanta), masaText(res, names, cal.amanta))
                    FactRow(stringResource(R.string.aaj_masa_purnimanta), masaText(res, names, cal.purnimanta))
                    FactRow(stringResource(R.string.aaj_samvat_vikram), cal.samvat.vikramChaitradi.toString())
                    FactRow(stringResource(R.string.aaj_samvat_shaka), cal.samvat.shaka.toString())
                    FactRow(stringResource(R.string.aaj_ritu), names.ritu(cal.ritu))
                    FactRow(stringResource(R.string.aaj_ayana), names.ayana(cal.ayana))
                    FactRow(stringResource(R.string.aaj_shool), names.direction(day.shool.direction))
                }
            }
        }
        FestivalsCard(c, names, fmt)
        c.eclipse?.let { e ->
            PpCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(PpSpace.s)) {
                    SectionTitle(stringResource(R.string.aaj_eclipse_title))
                    val visible = e.visible
                    if (visible != null) {
                        Text(
                            res.getString(
                                R.string.aaj_eclipse_body, names.eclipse(e.kind), fmt.range(res, visible.start, visible.end),
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
        Text(
            stringResource(R.string.aaj_tradition_footer), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FestivalsCard(c: AajUi.Content, names: AajNames, fmt: TimeFmt) {
    val res = androidx.compose.ui.platform.LocalContext.current.resources
    PpCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(PpSpace.s)) {
            SectionTitle(stringResource(R.string.aaj_festivals))
            val list = c.festivals
            when {
                list == null -> Text(stringResource(R.string.aaj_festivals_loading), style = MaterialTheme.typography.bodyLarge)
                list.isEmpty() -> Text(stringResource(R.string.aaj_festivals_none), style = MaterialTheme.typography.bodyLarge)
                else -> list.forEach { f ->
                    Text(names.festival(f.id), style = MaterialTheme.typography.bodyLarge)
                    f.parana?.let {
                        Text(
                            res.getString(R.string.aaj_parana, fmt.range(res, it.start, it.end)),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
