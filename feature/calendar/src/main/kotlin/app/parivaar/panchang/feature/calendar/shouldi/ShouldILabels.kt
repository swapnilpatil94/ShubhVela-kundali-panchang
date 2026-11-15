package app.parivaar.panchang.feature.calendar.shouldi

import androidx.annotation.StringRes
import app.parivaar.panchang.feature.calendar.R

/** Intent id to label resource, and why-id to explainer resource (ids only in rules; text only in resources). */
object ShouldILabels {
    private val LABELS: Map<String, Int> = mapOf(
        "vehicle_purchase" to R.string.should_i_vehicle_purchase,
        "vehicle_delivery" to R.string.should_i_vehicle_delivery,
        "property_purchase" to R.string.should_i_property_purchase,
        "property_registration" to R.string.should_i_property_registration,
        "griha_pravesh" to R.string.should_i_griha_pravesh,
        "construction_start" to R.string.should_i_construction_start,
        "new_job_join" to R.string.should_i_new_job_join,
        "job_interview" to R.string.should_i_job_interview,
        "business_start" to R.string.should_i_business_start,
        "shop_opening" to R.string.should_i_shop_opening,
        "contract_signing" to R.string.should_i_contract_signing,
        "investment" to R.string.should_i_investment,
        "gold_purchase" to R.string.should_i_gold_purchase,
        "electronics_purchase" to R.string.should_i_electronics_purchase,
        "clothes_purchase" to R.string.should_i_clothes_purchase,
        "travel_start" to R.string.should_i_travel_start,
        "pilgrimage_start" to R.string.should_i_pilgrimage_start,
        "haircut" to R.string.should_i_haircut,
        "mundan" to R.string.should_i_mundan,
        "naming_ceremony" to R.string.should_i_naming_ceremony,
        "vidyarambha" to R.string.should_i_vidyarambha,
        "exam_start" to R.string.should_i_exam_start,
        "new_course" to R.string.should_i_new_course,
        "engagement_talk" to R.string.should_i_engagement_talk,
        "gift_giving" to R.string.should_i_gift_giving,
        "puja_start" to R.string.should_i_puja_start,
        "vrat_start" to R.string.should_i_vrat_start,
        "planting" to R.string.should_i_planting,
        "moving_house" to R.string.should_i_moving_house,
        "bank_account_open" to R.string.should_i_bank_account_open,
    )

    private val WHY: Map<String, Int> = mapOf(
        ShouldI.WHY_TITHI to R.string.should_i_why_tithi,
        ShouldI.WHY_VAAR to R.string.should_i_why_vaar,
        ShouldI.WHY_NAKSHATRA to R.string.should_i_why_nakshatra,
        ShouldI.WHY_ABHIJIT to R.string.should_i_why_abhijit,
        "why_choghadiya_amrit" to R.string.should_i_why_choghadiya_amrit,
        "why_choghadiya_shubh" to R.string.should_i_why_choghadiya_shubh,
        "why_choghadiya_labh" to R.string.should_i_why_choghadiya_labh,
        "why_avoid_rahu_kaal" to R.string.should_i_why_avoid_rahu_kaal,
        "why_avoid_yamaganda" to R.string.should_i_why_avoid_yamaganda,
        "why_avoid_gulika" to R.string.should_i_why_avoid_gulika,
        "why_avoid_durmuhurta" to R.string.should_i_why_avoid_durmuhurta,
        "why_avoid_varjyam" to R.string.should_i_why_avoid_varjyam,
        "why_avoid_bhadra" to R.string.should_i_why_avoid_bhadra,
    )

    val intentIds: Set<String> get() = LABELS.keys

    @StringRes
    fun label(id: String): Int? = LABELS[id]

    @StringRes
    fun why(id: String): Int? = WHY[id]
}
