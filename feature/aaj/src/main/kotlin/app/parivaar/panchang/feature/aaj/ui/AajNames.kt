@file:Suppress("MaxLineLength", "MatchingDeclarationName", "TooManyFunctions") // Compose layout code, wrapped by the formatter later

package app.parivaar.panchang.feature.aaj.ui

import android.content.res.Resources
import app.parivaar.panchang.engine.calendar.Ayana
import app.parivaar.panchang.engine.calendar.EclipseKind
import app.parivaar.panchang.engine.calendar.Masa
import app.parivaar.panchang.engine.calendar.Ritu
import app.parivaar.panchang.engine.jyotish.Rashi
import app.parivaar.panchang.engine.panchang.Choghadiya
import app.parivaar.panchang.engine.panchang.Direction
import app.parivaar.panchang.engine.panchang.Karana
import app.parivaar.panchang.engine.panchang.LimbKind
import app.parivaar.panchang.engine.panchang.WindowKind
import app.parivaar.panchang.feature.aaj.R
import app.parivaar.panchang.engine.jyotish.Graha
import app.parivaar.panchang.feature.aaj.UnavailableIds

/** Display names from resources. Pure over [Resources], so read-aloud can reuse it. */
class AajNames(private val res: Resources, private val packageName: String) {
    private fun arr(id: Int): Array<String> = res.getStringArray(id)

    fun tithi(i: Int): String = if (i == LAST_TITHI) res.getString(R.string.aaj_amavasya) else arr(R.array.aaj_tithi_names)[(i - 1) % TITHI_HALF]

    fun paksha(tithi: Int): String =
        res.getString(if (tithi <= TITHI_HALF) R.string.aaj_paksha_shukla else R.string.aaj_paksha_krishna)

    fun nakshatra(i: Int) = arr(R.array.aaj_nakshatra_names)[i]
    fun yoga(i: Int) = arr(R.array.aaj_yoga_names)[i]
    fun karana(number: Int) = arr(R.array.aaj_karana_names)[Karana.ofNumber(number).ordinal]
    fun rashi(r: Rashi) = arr(R.array.aaj_rashi_names)[r.ordinal]
    fun masa(m: Masa) = arr(R.array.aaj_masa_names)[m.ordinal]
    fun ritu(r: Ritu) = arr(R.array.aaj_ritu_names)[r.ordinal]
    fun ayana(a: Ayana) = arr(R.array.aaj_ayana_names)[a.ordinal]
    fun direction(d: Direction) = arr(R.array.aaj_direction_names)[d.ordinal]

    fun graha(code: String?): String =
        Graha.entries.firstOrNull { it.name == code }?.let { arr(R.array.aaj_graha_names)[it.ordinal] }.orEmpty()

    /** Vaar name of [dayOfWeek] (ISO 1 = Monday .. 7 = Sunday). */
    fun vaar(isoDay: Int): String = arr(R.array.aaj_vaar_names)[isoDay % WEEK]

    /** Gem name by `Gem.ordinal`. */
    fun gem(ordinal: Int): String = arr(R.array.aaj_gem_names)[ordinal]

    fun choghadiya(code: String?): String =
        Choghadiya.entries.firstOrNull { it.name == code }?.let { arr(R.array.aaj_choghadiya_names)[it.ordinal] }.orEmpty()

    /** "Amrit (Moon)". */
    fun choghadiyaWithLord(code: String?): String {
        val c = Choghadiya.entries.firstOrNull { it.name == code } ?: return choghadiya(code)
        return res.getString(R.string.aaj_choghadiya_with_lord, choghadiya(code), graha(c.lord.name))
    }

    /** The name of the span [index] of [kind] (limb index conventions of the engine). */
    fun limbSpan(kind: LimbKind, index: Int): String = when (kind) {
        LimbKind.TITHI -> res.getString(R.string.aaj_limb_paksha_name, paksha(index), tithi(index))
        LimbKind.KARANA -> karana(index)
        LimbKind.NAKSHATRA -> nakshatra(index)
        LimbKind.YOGA -> yoga(index)
        LimbKind.MOON_RASHI -> rashi(Rashi.entries[index])
    }

    fun limbTitle(kind: LimbKind): String = res.getString(
        when (kind) {
            LimbKind.TITHI -> R.string.aaj_limb_tithi
            LimbKind.KARANA -> R.string.aaj_limb_karana
            LimbKind.NAKSHATRA -> R.string.aaj_limb_nakshatra
            LimbKind.YOGA -> R.string.aaj_limb_yoga
            LimbKind.MOON_RASHI -> R.string.aaj_limb_moon_rashi
        },
    )

    fun window(kind: WindowKind): String = res.getString(
        when (kind) {
            WindowKind.RAHU_KAAL -> R.string.aaj_window_rahu_kaal
            WindowKind.YAMAGANDA -> R.string.aaj_window_yamaganda
            WindowKind.GULIKA -> R.string.aaj_window_gulika
            WindowKind.ABHIJIT -> R.string.aaj_window_abhijit
            WindowKind.BRAHMA_MUHURTA -> R.string.aaj_window_brahma_muhurta
            WindowKind.DURMUHURTA -> R.string.aaj_window_durmuhurta
            WindowKind.VARJYAM -> R.string.aaj_window_varjyam
            WindowKind.AMRIT_KAAL -> R.string.aaj_window_amrit_kaal
            WindowKind.BHADRA -> R.string.aaj_window_bhadra
            WindowKind.PANCHAK -> R.string.aaj_window_panchak
            WindowKind.CHOGHADIYA -> R.string.aaj_window_choghadiya
            WindowKind.HORA -> R.string.aaj_window_hora
            WindowKind.GOWRI -> R.string.aaj_window_gowri
        },
    )

    /** Name of an id from `AajUi.Content.unavailable`. */
    fun unavailable(id: String): String =
        WindowKind.entries.firstOrNull { it.name == id }?.let(::window) ?: res.getString(
            when (id) {
                UnavailableIds.VIJAYA -> R.string.aaj_window_vijaya
                UnavailableIds.GODHULI -> R.string.aaj_window_godhuli
                UnavailableIds.DO_GHATI -> R.string.aaj_window_do_ghati
                else -> R.string.aaj_window_lagna
            },
        )

    fun eclipse(kind: EclipseKind): String = res.getString(
        when (kind) {
            EclipseKind.SOLAR_PARTIAL -> R.string.aaj_eclipse_solar_partial
            EclipseKind.SOLAR_ANNULAR -> R.string.aaj_eclipse_solar_annular
            EclipseKind.SOLAR_TOTAL -> R.string.aaj_eclipse_solar_total
            EclipseKind.LUNAR_PENUMBRAL -> R.string.aaj_eclipse_lunar_penumbral
            EclipseKind.LUNAR_PARTIAL -> R.string.aaj_eclipse_lunar_partial
            EclipseKind.LUNAR_TOTAL -> R.string.aaj_eclipse_lunar_total
        },
    )

    /** A packaged name `fest_<id>` when a language pack has one, else the id in words. */
    @Suppress("DiscouragedApi") // ids come from rule files, not from code
    fun festival(id: String): String {
        val r = res.getIdentifier("fest_$id", "string", packageName)
        return if (r != 0) res.getString(r) else id.split('_').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
    }

    private companion object {
        const val TITHI_HALF = 15
        const val LAST_TITHI = 30
        const val WEEK = 7
    }
}
