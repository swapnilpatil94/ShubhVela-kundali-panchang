package app.parivaar.panchang.engine.astro

import app.parivaar.panchang.engine.astro.internal.Apparent
import app.parivaar.panchang.engine.astro.internal.BarycentricStates
import app.parivaar.panchang.engine.astro.internal.ChebyshevBin
import app.parivaar.panchang.engine.astro.internal.FrameCache
import app.parivaar.panchang.engine.astro.internal.LunarNodes
import app.parivaar.panchang.engine.astro.internal.Vec3

/**
 * Precision backend: JPL DE440 excerpt `pp-de440-1900-2100.bin` (docs/specs/M01.md sections 3.4-3.6).
 * Mars, Jupiter and Saturn are the system barycentres 4, 5, 6 (as the oracle; see docs/ENGINE_DIFFS.md).
 * Holds per-segment record and frame caches: not thread-safe. Results are deterministic.
 */
class De440Ephemeris private constructor(private val bin: ChebyshevBin) : Ephemeris {
    private val frames = FrameCache()
    private val segMercury = segment(SSB, MERCURY_BARYCENTER)
    private val segVenus = segment(SSB, VENUS_BARYCENTER)
    private val segEmb = segment(SSB, EARTH_MOON_BARYCENTER)
    private val segMars = segment(SSB, MARS_BARYCENTER)
    private val segJupiter = segment(SSB, JUPITER_BARYCENTER)
    private val segSaturn = segment(SSB, SATURN_BARYCENTER)
    private val segSun = segment(SSB, SUN)
    private val segMoon = segment(EARTH_MOON_BARYCENTER, MOON)

    /** Earth/Moon mass ratio from the file header. */
    internal val emrat: Double = bin.emrat

    /** 1900-01-01 to 2100-01-01 (TT), inclusive. */
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

    /** Raw state of segment `center -> target` at a TDB Julian Day: km and km/day (reader checks only). */
    internal fun segmentState(center: Int, target: Int, jdTdb: Double): Pair<Vec3, Vec3> {
        val seg = segment(center, target)
        return bin.position(seg, jdTdb, 0.0) to bin.velocity(seg, jdTdb, 0.0)
    }

    private fun checkRange(tt: JulianDay) {
        if (!(tt.value >= VALID_FROM_JD && tt.value <= VALID_TO_JD)) throw EphemerisRangeException(tt)
    }

    /** Unchecked position without speed; [body] is never KETU_* here. */
    private fun position(body: Body, tt: JulianDay): EclipticPos {
        val frame = frames.at(tt)
        if (body != Body.RAHU_MEAN && body != Body.RAHU_TRUE) return Apparent.precision(states, frame, body)
        return LunarNodes.position(body, frame) {
            moonGeocentric(bin.position(segMoon, tt.value, frame.tdbOffsetDays)) to
                moonGeocentric(bin.velocity(segMoon, tt.value, frame.tdbOffsetDays))
        }
    }

    /** Moon_geo = (Moon wrt EMB) * (1 + 1 / EMRAT); the same factor applies to the velocity. */
    private fun moonGeocentric(moonWrtEmb: Vec3): Vec3 = moonWrtEmb * (1.0 + 1.0 / emrat)

    private val states = object : BarycentricStates {
        override fun bodyPosition(body: Body, jd: Double, extraDays: Double): Vec3 = when (body) {
            Body.SUN -> bin.position(segSun, jd, extraDays)
            // Moon_bary = Earth + Moon_geo = EMB + (Moon wrt EMB).
            Body.MOON -> bin.position(segEmb, jd, extraDays) + bin.position(segMoon, jd, extraDays)
            Body.MERCURY -> bin.position(segMercury, jd, extraDays)
            Body.VENUS -> bin.position(segVenus, jd, extraDays)
            Body.MARS -> bin.position(segMars, jd, extraDays)
            Body.JUPITER -> bin.position(segJupiter, jd, extraDays)
            Body.SATURN -> bin.position(segSaturn, jd, extraDays)
            else -> throw IllegalArgumentException("$body has no barycentric state")
        }

        override fun sunPosition(jd: Double, extraDays: Double): Vec3 = bin.position(segSun, jd, extraDays)

        /** Earth = EMB - Moon_geo / (1 + EMRAT). */
        override fun earthPosition(jd: Double, extraDays: Double): Vec3 =
            bin.position(segEmb, jd, extraDays) -
                moonGeocentric(bin.position(segMoon, jd, extraDays)) / (1.0 + emrat)

        /** v_E = v(0->3) - v(3->301) / EMRAT. */
        override fun earthVelocity(jd: Double, extraDays: Double): Vec3 =
            bin.velocity(segEmb, jd, extraDays) - bin.velocity(segMoon, jd, extraDays) / emrat
    }

    private fun segment(center: Int, target: Int): Int {
        val index = bin.segmentIndex(center, target)
        require(index >= 0) { "ephemeris file has no segment $center->$target" }
        return index
    }

    companion object {
        private const val VALID_FROM_JD = 2415020.5
        private const val VALID_TO_JD = 2488069.5
        private const val SSB = 0
        private const val MERCURY_BARYCENTER = 1
        private const val VENUS_BARYCENTER = 2
        private const val EARTH_MOON_BARYCENTER = 3
        private const val MARS_BARYCENTER = 4
        private const val JUPITER_BARYCENTER = 5
        private const val SATURN_BARYCENTER = 6
        private const val SUN = 10
        private const val MOON = 301

        /**
         * Opens the excerpt and verifies it: magic, version, SHA-256 of the whole source against
         * [expectedSha256Hex], and the presence of the eight segments. Throws [IllegalArgumentException]
         * on any mismatch. Hashing reads the source once (about 14 MB).
         */
        fun open(src: BinSource, expectedSha256Hex: String): De440Ephemeris =
            De440Ephemeris(ChebyshevBin.open(src, expectedSha256Hex))
    }
}
