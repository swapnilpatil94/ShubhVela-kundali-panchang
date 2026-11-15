package app.parivaar.panchang.feature.onboarding

import androidx.annotation.StringRes
import java.util.Locale

data class LanguageOption(val tag: String, @StringRes val nameRes: Int)

/** The 12 app languages in the order shown on step 1. Names are native and untranslatable. */
object LanguageOptions {
    val all: List<LanguageOption> = listOf(
        LanguageOption("en", R.string.lang_en),
        LanguageOption("hi", R.string.lang_hi),
        LanguageOption("bn", R.string.lang_bn),
        LanguageOption("mr", R.string.lang_mr),
        LanguageOption("te", R.string.lang_te),
        LanguageOption("ta", R.string.lang_ta),
        LanguageOption("gu", R.string.lang_gu),
        LanguageOption("kn", R.string.lang_kn),
        LanguageOption("ml", R.string.lang_ml),
        LanguageOption("or", R.string.lang_or),
        LanguageOption("pa", R.string.lang_pa),
        LanguageOption("as", R.string.lang_as),
    )

    const val FALLBACK = "en"

    /** The device's primary language if offered, else English. Only the language subtag is compared. */
    fun preselect(deviceTags: List<String>): String {
        val primary = deviceTags.firstOrNull()?.let { Locale.forLanguageTag(it).language } ?: return FALLBACK
        return all.firstOrNull { it.tag == primary }?.tag ?: FALLBACK
    }
}
