package app.parivaar.panchang.feature.calendar.guide

import android.content.res.Resources
import androidx.annotation.StringRes
import app.parivaar.panchang.feature.calendar.R

/** The one place that maps a festival rule id to its English name resource (VERIFY spelling, TODO(owner)). */
object FestivalNames {
    private val NAMES: Map<String, Int> = mapOf(
        "chaitra_shukla_pratipada" to R.string.fest_name_chaitra_shukla_pratipada,
        "rama_navami" to R.string.fest_name_rama_navami,
        "hanuman_jayanti" to R.string.fest_name_hanuman_jayanti,
        "akshaya_tritiya" to R.string.fest_name_akshaya_tritiya,
        "guru_purnima" to R.string.fest_name_guru_purnima,
        "nag_panchami" to R.string.fest_name_nag_panchami,
        "raksha_bandhan" to R.string.fest_name_raksha_bandhan,
        "krishna_janmashtami" to R.string.fest_name_krishna_janmashtami,
        "ganesh_chaturthi" to R.string.fest_name_ganesh_chaturthi,
        "anant_chaturdashi" to R.string.fest_name_anant_chaturdashi,
        "mahalaya_amavasya" to R.string.fest_name_mahalaya_amavasya,
        "sharad_navratri_start" to R.string.fest_name_sharad_navratri_start,
        "durga_ashtami" to R.string.fest_name_durga_ashtami,
        "maha_navami" to R.string.fest_name_maha_navami,
        "vijayadashami" to R.string.fest_name_vijayadashami,
        "karwa_chauth" to R.string.fest_name_karwa_chauth,
        "dhanteras" to R.string.fest_name_dhanteras,
        "naraka_chaturdashi" to R.string.fest_name_naraka_chaturdashi,
        "diwali_lakshmi_puja" to R.string.fest_name_diwali_lakshmi_puja,
        "govardhan_puja" to R.string.fest_name_govardhan_puja,
        "gujarati_new_year" to R.string.fest_name_gujarati_new_year,
        "bhai_dooj" to R.string.fest_name_bhai_dooj,
        "vasant_panchami" to R.string.fest_name_vasant_panchami,
        "maha_shivratri" to R.string.fest_name_maha_shivratri,
        "holika_dahan" to R.string.fest_name_holika_dahan,
        "holi" to R.string.fest_name_holi,
        "makar_sankranti" to R.string.fest_name_makar_sankranti,
        "ekadashi_smarta" to R.string.fest_name_ekadashi_smarta,
        "ekadashi_vaishnava" to R.string.fest_name_ekadashi_vaishnava,
        "purnima" to R.string.fest_name_purnima,
        "amavasya" to R.string.fest_name_amavasya,
        "sankashti_chaturthi" to R.string.fest_name_sankashti_chaturthi,
        "pradosh_vrat" to R.string.fest_name_pradosh_vrat,
        "masik_shivratri" to R.string.fest_name_masik_shivratri,
        "sankranti" to R.string.fest_name_sankranti,
        "somvati_amavasya" to R.string.fest_name_somvati_amavasya,
        "angaraki_sankashti" to R.string.fest_name_angaraki_sankashti,
    )

    /** Rule ids that have a name, in file order. */
    val ids: Set<String> get() = NAMES.keys

    @StringRes
    fun nameRes(id: String): Int? = NAMES[id]

    /** The name, or the id in words if an id is unknown (never blank). */
    fun name(res: Resources, id: String): String = NAMES[id]?.let(res::getString)
        ?: id.split('_').joinToString(" ") { w -> w.replaceFirstChar { c -> c.uppercase() } }
}
