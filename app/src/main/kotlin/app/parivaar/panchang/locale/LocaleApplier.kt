package app.parivaar.panchang.locale

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * Applies the in-app language. API 33+ uses the system per-app locale; below that the BCP-47 tag lives in plain
 * prefs `pp_ui`/`locale` (a UI preference, not personal data), read in `attachBaseContext` via [wrap].
 */
object LocaleApplier {
    private const val PREFS = "pp_ui"
    private const val KEY = "locale"

    /** Call from `attachBaseContext`. A no-op on API 33+ where the platform applies the locale itself. */
    fun wrap(base: Context): Context {
        val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) null else storedTag(base)
        if (tag == null) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList(locale))
        return base.createConfigurationContext(config)
    }

    /** Persists and applies [tag]; below API 33 the activity is recreated so [wrap] takes effect. */
    fun apply(activity: Activity, tag: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tag)
        } else {
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, tag).apply()
            activity.recreate()
        }
    }

    fun storedTag(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)?.takeIf { it.isNotBlank() }
}
