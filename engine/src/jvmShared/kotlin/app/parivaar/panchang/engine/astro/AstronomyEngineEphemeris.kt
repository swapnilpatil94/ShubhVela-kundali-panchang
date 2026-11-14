package app.parivaar.panchang.engine.astro

import app.parivaar.panchang.engine.astro.internal.Apparent
import app.parivaar.panchang.engine.astro.internal.FrameCache
import app.parivaar.panchang.engine.astro.internal.LunarNodes
import app.parivaar.panchang.engine.astro.internal.Vec3
import io.github.cosinekitty.astronomy.Aberration
import io.github.cosinekitty.astronomy.Time
import io.github.cosinekitty.astronomy.geoMoonState
import io.github.cosinekitty.astronomy.geoVector
import io.github.cosinekitty.astronomy.Body as AeBody

/**
 * Base backend: Astronomy Engine (MIT, `io.github.cosinekitty:astronomy`), docs/specs/M01.md section 3.7.
 *
 * The library supplies only vectors in the J2000 mean equator frame: `geoVector(body, time,
 * Aberration.Corrected)` in au for SUN..SATURN and `geoMoonState(time)` for the osculating node. Everything
 * after that is the engine's own code, shared with the precision backend: rotation to the true ecliptic and
 * equinox of date (section 3.3), the longitude rate (3.5 step 4) and the nodes (3.6). No light deflection.
 *
 * Known limits (docs/ENGINE_DIFFS.md): the library's Moon vector is geometric (no light-time, about 0.7 arcsec
 * in longitude), and its J2000 vectors are rotated as if they were GCRS (frame bias, under 0.02 arcsec).
 *
 * Lives in `src/jvmShared` (compiled into jvmMain and androidMain); commonMain never refers to it. Holds a
 * small frame cache: not thread-safe. Results are deterministic.
 */
class AstronomyEngineEphemeris : Ephemeris {
    private val frames = FrameCache()

    /** 1700-01-01 to 2200-01-01 (TT), inclusive. VERIFY against the library documentation (spec 3.7). */
    override val validRange: ClosedRange<JulianDay> = JulianDay(VALID_FROM_JD)..JulianDay(VALID_TO_JD)

    override fun geoApparent(body: Body, tt: JulianDay): EclipticPos {
        checkRange(tt)
        val rahu = LunarNodes.ascendingNodeOf(body)
        if (rahu != null) return LunarNodes.opposite(geoApparent(rahu, tt))
        return Apparent.withSpeed(tt) { position(body, it) }
    }

    /** As [geoApparent] with `speedDegPerDay` = 0.0 (one evaluation instead of three; used by rise/set). */
    internal fun geoApparentNoSpeed(body: Body, tt: JulianDay): EclipticPos {
        checkRange(tt)
        val rahu = LunarNodes.ascendingNodeOf(body)
        return if (rahu != null) LunarNodes.opposite(position(rahu, tt)) else position(body, tt)
    }

    private fun checkRange(tt: JulianDay) {
        if (!(tt.value >= VALID_FROM_JD && tt.value <= VALID_TO_JD)) throw EphemerisRangeException(tt)
    }

    /** Unchecked position without speed; [body] is never KETU_* here. */
    private fun position(body: Body, tt: JulianDay): EclipticPos {
        val frame = frames.at(tt)
        if (body == Body.RAHU_MEAN || body == Body.RAHU_TRUE) {
            // The Moon state is asked for by RAHU_TRUE only. Its units (au, au/day) do not matter: only the
            // direction of r x v is used.
            return LunarNodes.position(body, frame) {
                val s = geoMoonState(libraryTime(tt))
                Vec3(s.x, s.y, s.z) to Vec3(s.vx, s.vy, s.vz)
            }
        }
        val v = geoVector(libraryBody(body), libraryTime(tt), Aberration.Corrected)
        return Apparent.eclipticPos(frame.trueEcliptic, Vec3(v.x, v.y, v.z), v.length())
    }

    /** The library counts TT in days from J2000.0 and derives its own UT from it (not used here). */
    private fun libraryTime(tt: JulianDay): Time = Time.fromTerrestrialTime(tt.value - JulianDay.J2000)

    private fun libraryBody(body: Body): AeBody = when (body) {
        Body.SUN -> AeBody.Sun
        Body.MOON -> AeBody.Moon
        Body.MERCURY -> AeBody.Mercury
        Body.VENUS -> AeBody.Venus
        Body.MARS -> AeBody.Mars
        Body.JUPITER -> AeBody.Jupiter
        Body.SATURN -> AeBody.Saturn
        else -> throw IllegalArgumentException("$body has no Astronomy Engine vector")
    }

    private companion object {
        const val VALID_FROM_JD = 2341972.5
        const val VALID_TO_JD = 2524593.5
    }
}

/** The position-only path of the base backend for [positionOnly]; null for any other implementation. */
internal actual fun platformPositionOnly(eph: Ephemeris, body: Body, tt: JulianDay): EclipticPos? =
    (eph as? AstronomyEngineEphemeris)?.geoApparentNoSpeed(body, tt)
