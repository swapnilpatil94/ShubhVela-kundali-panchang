package app.parivaar.panchang.feature.calendar.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import app.parivaar.panchang.feature.aaj.ui.AajNames
import kotlinx.datetime.TimeZone

/** Preview-only helpers: names and formats for Asia/Kolkata in the preview's locale. */
@Composable
internal fun previewNames(): AajNames = LocalContext.current.let { AajNames(it.resources, it.packageName) }

@Composable
internal fun previewFormat(): CalFormat =
    CalFormat(TimeZone.of("Asia/Kolkata"), LocalConfiguration.current.locales[0], false)
