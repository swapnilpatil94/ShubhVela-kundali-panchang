@file:Suppress("MaxLineLength")

package app.parivaar.panchang.feature.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.parivaar.panchang.core.data.DataResult
import app.parivaar.panchang.core.data.SettingsRepository
import app.parivaar.panchang.core.designsystem.PanchangTheme
import app.parivaar.panchang.core.designsystem.components.PpButton
import app.parivaar.panchang.core.designsystem.components.PpButtonStyle
import app.parivaar.panchang.core.designsystem.components.PpCard
import app.parivaar.panchang.core.designsystem.preview.PpPreviews
import app.parivaar.panchang.core.designsystem.tokens.PpSpace
import app.parivaar.panchang.core.model.settings.AppSettings
import app.parivaar.panchang.core.model.settings.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** One language tile: BCP-47 tag plus its native name (never translated). The host supplies the 12-language list. */
data class LanguageChoice(val tag: String, val nativeName: String)

private val themeChoices =
    ThemeMode.entries.map {
        when (it) {
            ThemeMode.SYSTEM -> Choice(it, R.string.settings_theme_system, null)
            ThemeMode.LIGHT -> Choice(it, R.string.settings_theme_light, null)
            ThemeMode.DARK -> Choice(it, R.string.settings_theme_dark, null)
        }
    }

/**
 * Settings, opened from the avatar. Appearance and Language are inline; Conventions and Privacy are separate
 * destinations reached through [onOpenConventions] and [onOpenPrivacy]. No delete-all or export here (M10).
 */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    languages: List<LanguageChoice>,
    appearance: AppearanceModel,
    onOpenConventions: () -> Unit,
    onOpenPrivacy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(PpSpace.l),
        verticalArrangement = Arrangement.spacedBy(PpSpace.l),
    ) {
        ScreenTitle(R.string.settings_title)

        val unreadable by appearance.unreadable.collectAsState(initial = false)
        if (unreadable) UnreadableSettingsCard(appearance::resetUnreadable)

        PpCard(Modifier.fillMaxWidth(), mergeSemantics = false) {
            SectionTitle(R.string.settings_section_appearance)
            val theme = settings.themeMode
            themeChoices.forEach { c ->
                ChoiceGroupRow(c, c.value == theme) { appearance.setThemeMode(c.value) }
            }
            if (SHOW_DYNAMIC_COLOR && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SwitchRow(
                    R.string.settings_dynamic_color, R.string.settings_dynamic_color_hint,
                    settings.dynamicColor, appearance::setDynamicColor,
                )
            }
            SwitchRow(
                R.string.settings_bada_text, R.string.settings_bada_text_hint,
                settings.badaText, appearance::setBadaText,
            )
            SwitchRow(
                R.string.settings_reduce_motion, R.string.settings_reduce_motion_hint,
                settings.reduceMotion, appearance::setReduceMotion,
            )
            SwitchRow(
                R.string.settings_reduce_transparency, R.string.settings_reduce_transparency_hint,
                settings.reduceTransparency, appearance::setReduceTransparency,
            )
        }

        PpCard(Modifier.fillMaxWidth(), mergeSemantics = false) {
            SectionTitle(R.string.settings_section_today)
            SwitchRow(
                R.string.settings_travel_mode, R.string.settings_travel_mode_hint,
                settings.travelMode, appearance::setTravelMode,
            )
            SwitchRow(
                R.string.settings_show_city, R.string.settings_show_city_hint,
                settings.showCityOnWidgets, appearance::setShowCityOnWidgets,
            )
        }

        if (languages.isNotEmpty()) {
            val selected = languages.firstOrNull { it.tag == settings.localeTag }?.tag
            LanguageGroup(languages, selected, appearance::setLocaleTag)
        }

        PpCard(Modifier.fillMaxWidth(), onClick = onOpenConventions) {
            SectionTitle(R.string.settings_section_conventions)
            Hint(R.string.settings_conventions_summary)
        }
        PpCard(Modifier.fillMaxWidth(), onClick = onOpenPrivacy) {
            SectionTitle(R.string.settings_section_privacy)
            Hint(R.string.settings_privacy_summary)
        }
    }
}

/** Shown while the saved settings cannot be read: calm copy, and a reset that runs only after an explicit confirm. */
@Composable
private fun UnreadableSettingsCard(onConfirmReset: () -> Unit) {
    var asking by rememberSaveable { mutableStateOf(false) }
    PpCard(Modifier.fillMaxWidth(), mergeSemantics = false) {
        SectionTitle(R.string.settings_unreadable_title)
        Hint(R.string.settings_unreadable_body)
        PpButton(stringResource(R.string.settings_unreadable_action), { asking = true }, PpButtonStyle.Outline)
    }
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(stringResource(R.string.settings_unreadable_confirm_title)) },
            text = { Text(stringResource(R.string.settings_unreadable_confirm_body)) },
            confirmButton = {
                TextButton({ asking = false; onConfirmReset() }) { Text(stringResource(R.string.settings_unreadable_confirm_yes)) }
            },
            dismissButton = { TextButton({ asking = false }) { Text(stringResource(R.string.settings_unreadable_confirm_no)) } },
        )
    }
}

@Composable
private fun <T> ChoiceGroupRow(c: Choice<T>, selected: Boolean, onClick: () -> Unit) {
    // Reuse the radio-group row look by rendering a one-item group body without its own card.
    RadioRow(stringResource(c.label), null, selected, onClick)
}

@Composable
private fun LanguageGroup(languages: List<LanguageChoice>, selectedTag: String?, onSelect: (String) -> Unit) {
    PpCard(Modifier.fillMaxWidth(), mergeSemantics = false) {
        SectionTitle(R.string.settings_section_language)
        Hint(R.string.settings_language_hint)
        languages.forEach { RadioRow(it.nativeName, null, it.tag == selectedTag) { onSelect(it.tag) } }
    }
}

/** Preview and test stand-in only; the app injects the real repository. */
internal object NoopSettings : SettingsRepository {
    override val settings: Flow<AppSettings> = flowOf(AppSettings())

    override suspend fun update(f: (AppSettings) -> AppSettings): DataResult<Unit> = DataResult.Ok(Unit)
}

@PpPreviews
@Composable
private fun SettingsPreview() =
    PanchangTheme {
        SettingsScreen(
            AppSettings(localeTag = "hi"),
            listOf(
                LanguageChoice("en", "English"),
                LanguageChoice("hi", "हिन्दी"),
                LanguageChoice("mr", "मराठी"),
            ),
            AppearanceModel(NoopSettings, kotlinx.coroutines.MainScope()),
            {},
            {},
        )
    }

/** Ratna Glass ignores wallpaper colours (decision D6); the stored setting and its model write stay. */
private const val SHOW_DYNAMIC_COLOR = false
