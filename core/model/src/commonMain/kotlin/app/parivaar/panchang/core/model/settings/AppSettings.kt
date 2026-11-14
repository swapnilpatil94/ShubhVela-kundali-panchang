package app.parivaar.panchang.core.model.settings

import app.parivaar.panchang.core.model.profile.RecordId
import app.parivaar.panchang.engine.CalcSettings

data class AppSettings(
    val defaultCalc: CalcSettings = CalcSettings(),
    val selfProfileId: RecordId? = null,
    val localeTag: String? = null,
    val privacyScreen: Boolean = false,
) {
    override fun toString(): String = "AppSettings(redacted)"
}
