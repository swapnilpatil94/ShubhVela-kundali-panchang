package app.parivaar.panchang.engine.calendar

import app.parivaar.panchang.engine.CalcSettings
import app.parivaar.panchang.engine.Place
import app.parivaar.panchang.engine.astro.Body
import app.parivaar.panchang.engine.astro.Ephemeris
import app.parivaar.panchang.engine.astro.TimeTables
import app.parivaar.panchang.engine.calendar.internal.ShadowGeometry
import app.parivaar.panchang.engine.calendar.internal.ShadowGeometry.Companion.contact
import app.parivaar.panchang.engine.calendar.internal.ShadowGeometry.Companion.gap
import app.parivaar.panchang.engine.calendar.internal.ShadowGeometry.Companion.minimum
import kotlin.math.PI
import kotlin.math.abs
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Solar and lunar eclipses with local circumstances and sutak (docs/specs/M04.md section 3.9).
 *
 * Candidates are the new and full moons of a [LunarCalendar] with |beta_M| <= 2 degrees. Greatest eclipse is
 * the minimum of gamma on the syzygy +/- 6 h (local solar: global greatest +/- 4 h), 600 s grid then
 * golden-section search to 0.1 s; contacts are Brent roots to 1 ms walking 600 s steps out from greatest.
 * Results never hold the place. Holds caches that never change a result: not thread-safe.
 */
class EclipseSearch(eph: Ephemeris, time: TimeTables = TimeTables.DEFAULT) {
    private val calendar = LunarCalendar(eph, time)
    private val shadow = ShadowGeometry(eph, time)

    /** Eclipses with greatest in [from, to), sorted by (greatest, solar). */
    fun global(from: Instant, to: Instant): List<GlobalEclipse> {
        require(from <= to) { "from must not be after to" }
        if (from == to) return emptyList()
        val lo = from - SYZYGY_MARGIN
        val hi = to + SYZYGY_MARGIN
        val found = ArrayList<GlobalEclipse>()
        calendar.fullMoons(lo, hi).mapNotNullTo(found, ::lunarAt)
        calendar.newMoons(lo, hi).mapNotNullTo(found, ::solarAt)
        return found.filter { it.greatest >= from && it.greatest < to }
            .sortedWith(compareBy<GlobalEclipse> { it.greatest }.thenBy { it.solar })
    }

    /** [e] seen from [place]; null for a solar eclipse that does not reach the place. */
    fun local(e: GlobalEclipse, place: Place, s: CalcSettings = CalcSettings()): LocalEclipse? =
        if (e.solar) localSolar(e, place, s) else localLunar(e, place, s)

    private fun lunarAt(fullMoon: Instant): GlobalEclipse? {
        if (abs(shadow.moonLatDeg(fullMoon)) > BETA_MAX_DEG) return null
        val greatest = minimum(fullMoon, SYZYGY_HALF_WINDOW_SECONDS, shadow::lunarGamma)
        val g = shadow.lunar(greatest)
        return if (g.gamma < g.rhoP + g.sM) lunarEclipse(greatest, g) else null
    }

    private fun lunarEclipse(greatest: Instant, g: ShadowGeometry.Lunar): GlobalEclipse {
        val umbral = (g.rhoU + g.sM - g.gamma) / (2.0 * g.sM)
        val penumbral = (g.rhoP + g.sM - g.gamma) / (2.0 * g.sM)
        val kind = when {
            umbral >= 1.0 -> EclipseKind.LUNAR_TOTAL
            umbral > 0.0 -> EclipseKind.LUNAR_PARTIAL
            else -> EclipseKind.LUNAR_PENUMBRAL
        }
        val contacts = LinkedHashMap<Contact, Instant>()
        fun pair(first: Contact, last: Contact, f: (ShadowGeometry.Lunar) -> Double) {
            val h: (Instant) -> Double = { f(shadow.lunar(it)) }
            contact(greatest, -1, CONTACT_REACH_SECONDS, h)?.let { contacts[first] = it }
            contact(greatest, 1, CONTACT_REACH_SECONDS, h)?.let { contacts[last] = it }
        }
        pair(Contact.P1, Contact.P4) { it.gamma - (it.rhoP + it.sM) }
        pair(Contact.U1, Contact.U4) { it.gamma - (it.rhoU + it.sM) }
        pair(Contact.U2, Contact.U3) { it.gamma - (it.rhoU - it.sM) }
        val ordered = LUNAR_ORDER.filter { it in contacts }.associateWith { contacts.getValue(it) }
        return GlobalEclipse(false, greatest, g.gamma * RAD_TO_DEG, kind, umbral, penumbral, ordered)
    }

    private fun solarAt(newMoon: Instant): GlobalEclipse? =
        if (abs(shadow.moonLatDeg(newMoon)) > BETA_MAX_DEG) null else solarNear(newMoon)

    private fun solarNear(newMoon: Instant): GlobalEclipse? {
        val greatest = minimum(newMoon, SYZYGY_HALF_WINDOW_SECONDS, shadow::solarGamma)
        val g = shadow.solarGlobal(greatest)
        if (!(g.gamma < g.limit)) return null
        return GlobalEclipse(true, greatest, g.gamma * RAD_TO_DEG, null, null, null, emptyMap())
    }

    private fun localLunar(e: GlobalEclipse, place: Place, s: CalcSettings): LocalEclipse {
        val kind = checkNotNull(e.lunarKind) { "lunar eclipse needs a lunar kind" }
        val penumbral = kind == EclipseKind.LUNAR_PENUMBRAL
        val magnitude = checkNotNull(if (penumbral) e.penumbralMag else e.umbralMag) { "magnitude missing" }
        val interval = if (penumbral) {
            span(e.contacts, Contact.P1, Contact.P4)
        } else {
            span(e.contacts, Contact.U1, Contact.U4)
        }
        val visible = interval?.let { shadow.visible(Body.MOON, place, it, s) }
        val sutak = interval?.takeIf { visible != null && !penumbral }
            ?.let { TimeRange(it.start - LUNAR_SUTAK_SECONDS.seconds, it.end) }
        return LocalEclipse(e, kind, magnitude, e.greatest, e.contacts, visible, sutak)
    }

    private fun localSolar(e: GlobalEclipse, place: Place, s: CalcSettings): LocalEclipse? {
        val maxAt = minimum(e.greatest, LOCAL_HALF_WINDOW_SECONDS) { shadow.sigma(place, it) }
        val d = shadow.discs(place, maxAt)
        if (!(d.sigma < d.sS + d.sM)) return null
        val magnitude = (d.sS + d.sM - d.sigma) / (2.0 * d.sS)
        val kind = when {
            d.sM > d.sS && d.sigma <= d.sM - d.sS -> EclipseKind.SOLAR_TOTAL
            d.sM < d.sS && d.sigma <= d.sS - d.sM -> EclipseKind.SOLAR_ANNULAR
            else -> EclipseKind.SOLAR_PARTIAL
        }
        val contacts = solarContacts(place, maxAt, central = kind != EclipseKind.SOLAR_PARTIAL)
        val interval = span(contacts, Contact.C1, Contact.C4)
        val visible = interval?.let { shadow.visible(Body.SUN, place, it, s) }
        val sutak = interval?.takeIf { visible != null }
            ?.let { TimeRange(it.start - SOLAR_SUTAK_SECONDS.seconds, it.end) }
        return LocalEclipse(e, kind, magnitude, maxAt, contacts, visible, sutak)
    }

    /** C1..C4 that exist: C1/C4 at sigma = s'S + s'M, C2/C3 (total or annular) at sigma = |s'M - s'S|. */
    private fun solarContacts(place: Place, maxAt: Instant, central: Boolean): Map<Contact, Instant> {
        val outer: (Instant) -> Double = { t -> shadow.discs(place, t).let { it.sigma - (it.sS + it.sM) } }
        val inner: (Instant) -> Double = { t -> shadow.discs(place, t).let { it.sigma - gap(it.sM, it.sS) } }
        val contacts = LinkedHashMap<Contact, Instant>()
        contact(maxAt, -1, CONTACT_REACH_SECONDS, outer)?.let { contacts[Contact.C1] = it }
        if (central) {
            contact(maxAt, -1, CONTACT_REACH_SECONDS, inner)?.let { contacts[Contact.C2] = it }
            contact(maxAt, 1, CONTACT_REACH_SECONDS, inner)?.let { contacts[Contact.C3] = it }
        }
        contact(maxAt, 1, CONTACT_REACH_SECONDS, outer)?.let { contacts[Contact.C4] = it }
        return contacts
    }

    private fun span(contacts: Map<Contact, Instant>, first: Contact, last: Contact): TimeRange? {
        val a = contacts[first]
        val b = contacts[last]
        return if (a != null && b != null) TimeRange(a, b) else null
    }

    private companion object {
        const val BETA_MAX_DEG = 2.0
        const val RAD_TO_DEG = 180.0 / PI
        const val SYZYGY_HALF_WINDOW_SECONDS = 6.0 * 3600.0
        const val LOCAL_HALF_WINDOW_SECONDS = 4.0 * 3600.0

        /** Farther than any contact from greatest (lunar P1..P4 lasts at most about 6 h). */
        const val CONTACT_REACH_SECONDS = 8.0 * 3600.0
        const val SOLAR_SUTAK_SECONDS = 43_200L
        const val LUNAR_SUTAK_SECONDS = 32_400L

        /** More than the greatest-to-syzygy distance (under 6 h). */
        val SYZYGY_MARGIN = 1.days
        val LUNAR_ORDER = listOf(Contact.P1, Contact.U1, Contact.U2, Contact.U3, Contact.U4, Contact.P4)
    }
}
