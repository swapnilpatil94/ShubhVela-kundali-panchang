@file:Suppress("MaxLineLength", "LongParameterList", "LongMethod", "MagicNumber", "CyclomaticComplexMethod")

package app.parivaar.panchang.feature.calendar.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.parivaar.panchang.core.designsystem.components.ExplainSheet
import app.parivaar.panchang.core.designsystem.components.PpButton
import app.parivaar.panchang.core.designsystem.components.PpButtonStyle
import app.parivaar.panchang.core.designsystem.components.PpCard
import app.parivaar.panchang.core.designsystem.tokens.PpSpace
import app.parivaar.panchang.core.designsystem.PpTheme
import app.parivaar.panchang.core.designsystem.tokens.PpShapes
import app.parivaar.panchang.core.designsystem.tokens.PpType
import app.parivaar.panchang.engine.calendar.FestivalOccurrence
import app.parivaar.panchang.engine.calendar.LocalEclipse
import app.parivaar.panchang.engine.calendar.LunarLabel
import app.parivaar.panchang.engine.calendar.OccFlag
import app.parivaar.panchang.engine.panchang.LimbKind
import app.parivaar.panchang.engine.panchang.PanchangDay
import app.parivaar.panchang.engine.panchang.WindowKind
import app.parivaar.panchang.feature.aaj.ui.FactRow
import app.parivaar.panchang.feature.aaj.ui.LimbCard
import app.parivaar.panchang.feature.aaj.ui.ProofCopy
import app.parivaar.panchang.feature.aaj.ui.TimeFmt
import app.parivaar.panchang.feature.calendar.DayDetail
import app.parivaar.panchang.feature.calendar.R
import androidx.compose.ui.unit.dp
import java.util.Locale
import app.parivaar.panchang.feature.aaj.R as AajR

private val SHOWN_WINDOWS = listOf(
    WindowKind.RAHU_KAAL, WindowKind.YAMAGANDA, WindowKind.GULIKA, WindowKind.ABHIJIT,
    WindowKind.BRAHMA_MUHURTA, WindowKind.DURMUHURTA,
)

/** Everything about one day: limbs, sunrise and sunset, masa and samvat, festivals, eclipse, and how it is calculated. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaySheet(
    detail: DayDetail,
    names: ResCalendarNames,
    locale: Locale,
    onShouldI: () -> Unit,
    onGuide: (String) -> Unit,
    onConventions: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val res = context.resources
    val fmt = remember(detail, locale) { TimeFmt(detail.offsetSeconds, detail.date, DateFormat.is24HourFormat(context), locale) }
    var explain by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), shape = PpShapes.sheetTop, containerColor = PpTheme.ratna.ink3, tonalElevation = 0.dp) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = PpSpace.l),
            verticalArrangement = Arrangement.spacedBy(PpSpace.m),
        ) {
            Text(fmt.date(detail.date), style = PpType.displayM, modifier = Modifier.semantics { heading() })
            val day = detail.day
            if (day == null) {
                Text(stringResource(R.string.cal_day_no_sun), style = MaterialTheme.typography.bodyLarge)
            } else {
                listOf(LimbKind.TITHI, LimbKind.NAKSHATRA, LimbKind.YOGA, LimbKind.KARANA).forEach { k ->
                    LimbCard(k, day.limbs[k].orEmpty(), day, names.aaj, fmt)
                }
            }
            Facts(detail, day, names, fmt)
            if (day != null) Timings(day, names, fmt)
            Festivals(detail, names, fmt, onGuide)
            detail.eclipses.forEach { Eclipse(it, names, fmt) }
            PpButton(stringResource(R.string.cal_day_should_i), onShouldI, PpButtonStyle.Tonal)
            TextButton(onClick = { explain = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.cal_day_how)) }
            Text(stringResource(R.string.cal_footer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(PpSpace.xxl))
        }
    }
    if (explain) {
        ExplainSheet(
            title = stringResource(R.string.cal_explain_title),
            explanation = stringResource(R.string.cal_explain_body),
            howCalculated = ProofCopy.how(res, detail.day?.flags?.isNotEmpty() == true),
            traditionNote = stringResource(R.string.cal_explain_tradition),
            proof = ProofCopy.label(res, detail.proof),
            onChangeConventions = { explain = false; onConventions() },
            onDismiss = { explain = false },
        )
    }
}

@Composable
private fun masaText(l: LunarLabel, names: ResCalendarNames): String {
    val base = names.masa(l.masa)
    return when {
        l.adhika -> stringResource(AajR.string.aaj_masa_adhika, base)
        l.kshayaMasa != null -> stringResource(AajR.string.aaj_masa_kshaya, base)
        else -> base
    }
}

@Composable
private fun Facts(d: DayDetail, day: PanchangDay?, names: ResCalendarNames, fmt: TimeFmt) {
    PpCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(PpSpace.s)) {
            if (day != null) {
                FactRow(stringResource(AajR.string.aaj_sunrise), fmt.time(day.sunrise))
                FactRow(stringResource(AajR.string.aaj_sunset), fmt.time(day.sunset))
            }
            FactRow(stringResource(AajR.string.aaj_masa_amanta), masaText(d.cal.amanta, names))
            FactRow(stringResource(AajR.string.aaj_masa_purnimanta), masaText(d.cal.purnimanta, names))
            FactRow(stringResource(AajR.string.aaj_samvat_vikram), d.cal.samvat.vikramChaitradi.toString())
            FactRow(stringResource(AajR.string.aaj_samvat_shaka), d.cal.samvat.shaka.toString())
            FactRow(stringResource(AajR.string.aaj_ritu), names.aaj.ritu(d.cal.ritu))
            FactRow(stringResource(R.string.cal_day_ayana), names.aaj.ayana(d.cal.ayana))
        }
    }
}

@Composable
private fun Timings(day: PanchangDay, names: ResCalendarNames, fmt: TimeFmt) {
    val res = LocalContext.current.resources
    PpCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(PpSpace.s)) {
            Text(stringResource(R.string.cal_day_timings), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            SHOWN_WINDOWS.forEach { kind ->
                val w = day.windows.firstOrNull { it.kind == kind }
                when {
                    kind in day.unavailable -> FactRow(names.aaj.window(kind), stringResource(AajR.string.aaj_not_available))
                    w != null -> FactRow(names.aaj.window(kind), fmt.range(res, w.start, w.end))
                }
            }
        }
    }
}

@Composable
private fun Festivals(d: DayDetail, names: ResCalendarNames, fmt: TimeFmt, onGuide: (String) -> Unit) {
    val res = LocalContext.current.resources
    PpCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(PpSpace.s)) {
            Text(stringResource(R.string.cal_day_festivals), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            if (d.festivals.isEmpty()) Text(stringResource(R.string.cal_day_no_festival), style = MaterialTheme.typography.bodyLarge)
            d.festivals.forEach { o -> Festival(o, names, fmt, res, onGuide) }
        }
    }
}

@Composable
private fun Festival(o: FestivalOccurrence, names: ResCalendarNames, fmt: TimeFmt, res: android.content.res.Resources, onGuide: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(PpSpace.xs)) {
        Text(names.festivalName(o.id), style = MaterialTheme.typography.titleSmall)
        o.kalaWindow?.let { Text(stringResource(R.string.cal_day_kala, fmt.range(res, it.start, it.end)), style = MaterialTheme.typography.bodyMedium) }
        o.muhurat?.let { Text(stringResource(R.string.cal_day_muhurat, fmt.range(res, it.start, it.end)), style = MaterialTheme.typography.bodyMedium) }
        o.parana?.let { Text(stringResource(R.string.cal_day_parana, fmt.range(res, it.start, it.end)), style = MaterialTheme.typography.bodyMedium) }
        // Badges are words, never colour alone.
        o.flags.mapNotNull {
            when (it) {
                OccFlag.VRIDDHI_TITHI -> R.string.cal_flag_vriddhi
                OccFlag.KSHAYA_TITHI -> R.string.cal_flag_kshaya
                OccFlag.ADHIKA_MASA -> R.string.cal_flag_adhika
                else -> null
            }
        }.forEach { Text(stringResource(it), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        TextButton(onClick = { onGuide(o.id) }) { Text(stringResource(R.string.cal_day_guide)) }
    }
}

@Composable
private fun Eclipse(e: LocalEclipse, names: ResCalendarNames, fmt: TimeFmt) {
    val res = LocalContext.current.resources
    PpCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(PpSpace.xs)) {
            Text(names.aaj.eclipse(e.kind), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            e.visible?.let { Text(stringResource(R.string.cal_day_eclipse_visible, fmt.range(res, it.start, it.end)), style = MaterialTheme.typography.bodyMedium) }
            e.sutak?.let { Text(stringResource(R.string.cal_day_sutak, fmt.range(res, it.start, it.end)), style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
