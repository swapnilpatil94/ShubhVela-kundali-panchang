package app.parivaar.panchang.feature.bonds

import androidx.annotation.StringRes

/** Engine and content ids mapped to string resources. Unknown ids get a calm generic text, never the raw id. */
internal object BondTexts {
    @StringRes
    fun kind(k: BondKind): Int = when (k) {
        BondKind.LOVE -> R.string.bonds_kind_love
        BondKind.CRUSH -> R.string.bonds_kind_crush
        BondKind.BESTIE -> R.string.bonds_kind_bestie
        BondKind.SIBLING -> R.string.bonds_kind_sibling
        BondKind.PARENT -> R.string.bonds_kind_parent
        BondKind.WORK -> R.string.bonds_kind_work
        BondKind.ROOMMATE -> R.string.bonds_kind_roommate
    }

    private val LABELS = mapOf(
        "bond.label.cosmic_twins" to R.string.bonds_label_cosmic_twins,
        "bond.label.soul_squad" to R.string.bonds_label_soul_squad,
        "bond.label.spicy_combo" to R.string.bonds_label_spicy_combo,
        "bond.label.opposites_attract" to R.string.bonds_label_opposites_attract,
        "bond.label.growth_bond" to R.string.bonds_label_growth_bond,
    )

    /** Every label key `bond_weights.json` may produce (BondWeightsContentTest keeps the two in sync). */
    val labelKeys: Set<String> get() = LABELS.keys

    @StringRes
    fun label(key: String?): Int? = key?.let { LABELS[it] }

    @StringRes
    fun axis(key: String): Int = when (key) {
        "VIBE" -> R.string.bonds_axis_vibe
        "TALK" -> R.string.bonds_axis_talk
        "TRUST" -> R.string.bonds_axis_trust
        "FIGHTS" -> R.string.bonds_axis_fights
        else -> R.string.bonds_axis_growth
    }

    @StringRes
    fun koota(key: String): Int = when (key) {
        "VARNA" -> R.string.bonds_koota_varna
        "VASHYA" -> R.string.bonds_koota_vashya
        "TARA" -> R.string.bonds_koota_tara
        "YONI" -> R.string.bonds_koota_yoni
        "GRAHA_MAITRI" -> R.string.bonds_koota_graha_maitri
        "GANA" -> R.string.bonds_koota_gana
        "BHAKOOT" -> R.string.bonds_koota_bhakoot
        else -> R.string.bonds_koota_nadi
    }

    @StringRes
    fun note(id: String): Int = when {
        id == "koota.bhakoot.dosha" -> R.string.bonds_note_bhakoot_dosha
        id == "koota.nadi.same" -> R.string.bonds_note_nadi_same
        id.endsWith(".exception_possible") -> R.string.bonds_note_exception_possible
        else -> R.string.bonds_note_generic
    }

    @StringRes
    fun manglik(key: String?): Int? = when (key) {
        ManglikNote.BALANCED -> R.string.bonds_manglik_balanced
        ManglikNote.CONSULT -> R.string.bonds_manglik_consult
        else -> null
    }

    @StringRes
    fun mars(level: Int): Int = when (level) {
        0 -> R.string.bonds_mars_0
        1 -> R.string.bonds_mars_1
        else -> R.string.bonds_mars_2
    }

    /** Quarter points as gunas: "28", "27.5", "27.25" (no locale grouping needed below 100). */
    fun gunas(q: Int): String {
        val whole = q / QUARTERS
        return when (q % QUARTERS) {
            0 -> "$whole"
            1 -> "$whole.25"
            2 -> "$whole.5"
            else -> "$whole.75"
        }
    }

    private const val QUARTERS = 4
}
