@file:Suppress("MaxLineLength", "MatchingDeclarationName", "TooManyFunctions") // Compose layout code, wrapped by the formatter later

package app.parivaar.panchang.feature.aaj.ui

import android.content.res.Resources
import app.parivaar.panchang.core.designsystem.components.ProofInfo
import app.parivaar.panchang.engine.SunriseConvention
import app.parivaar.panchang.engine.astro.BackendId
import app.parivaar.panchang.engine.jyotish.AyanamsaId
import app.parivaar.panchang.feature.aaj.R

/** Turns the technical ids in the model's [ProofInfo] into labels (M9 proof panel). */
object ProofCopy {
    fun label(res: Resources, id: ProofInfo): ProofInfo = ProofInfo(
        backend = res.getString(
            when (id.backend) {
                BackendId.PRECISION_DE440.name -> R.string.aaj_proof_backend_precision
                BackendId.BASE_ASTRONOMY_ENGINE.name -> R.string.aaj_proof_backend_base
                else -> R.string.aaj_proof_backend_unknown
            },
        ),
        ayanamsa = res.getString(
            when (id.ayanamsa) {
                AyanamsaId.LAHIRI.name -> R.string.aaj_proof_ayanamsa_lahiri
                AyanamsaId.TRUE_CHITRA.name -> R.string.aaj_proof_ayanamsa_true_chitra
                AyanamsaId.KP_NEW.name -> R.string.aaj_proof_ayanamsa_kp_new
                AyanamsaId.RAMAN.name -> R.string.aaj_proof_ayanamsa_raman
                else -> R.string.aaj_proof_ayanamsa_yukteshwar
            },
        ),
        sunrise = res.getString(
            when (id.sunrise) {
                SunriseConvention.CENTER_REFRACTED.name -> R.string.aaj_proof_sunrise_center_refracted
                SunriseConvention.CENTER_GEOMETRIC.name -> R.string.aaj_proof_sunrise_center_geometric
                else -> R.string.aaj_proof_sunrise_upper_limb
            },
        ),
        altitudeM = id.altitudeM,
    )

    /** The explanation text of the sheet: how it is calculated plus the "about 1 minute" note. */
    fun how(res: Resources, highLatitude: Boolean = false): String =
        listOfNotNull(
            res.getString(R.string.aaj_explain_how),
            res.getString(R.string.aaj_high_latitude_body).takeIf { highLatitude },
            res.getString(R.string.aaj_proof_difference_note),
        ).joinToString("\n\n")
}
