package app.parivaar.panchang.engine.astro

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** docs/specs/M01.md section 4, `SmokePositionsTest`: Precision (DE440) and Base (Astronomy Engine) backends. */
class SmokePositionsTest {
    private val eph = GoldenLoader.openDe440()
    private val base = AstronomyEngineEphemeris()
    private val j2000 = JulianDay(2451545.0)

    private fun wrap180(d: Double): Double {
        var x = d % 360.0
        if (x > 180.0) x -= 360.0
        if (x <= -180.0) x += 360.0
        return x
    }

    @Test
    fun sunMeeusExample25b() {
        val sun = eph.geoApparent(Body.SUN, JulianDay(2448908.5))
        assertTrue(abs(wrap180(sun.lonDeg - 199.906061)) <= 1.0 / 3600.0, "Sun lon ${sun.lonDeg}")
        // Sanity (loose, textbook values): latitude about 1 arcsec at most, distance 0.9976 AU, about 1 deg/day.
        assertTrue(abs(sun.latDeg) < 1.5 / 3600.0)
        assertEquals(0.99760775, sun.distanceAu, 2e-6)
        assertEquals(0.99, sun.speedDegPerDay, 0.02)
    }

    @Test
    fun meanNodeAtJ2000() {
        val dpsiDeg = Math.toDegrees(EarthOrientation.nutation(j2000).dpsiRad)
        val rahu = eph.geoApparent(Body.RAHU_MEAN, j2000)
        assertEquals(125.044555, rahu.lonDeg - dpsiDeg, 1e-6)
        assertEquals(0.0, rahu.latDeg, 0.0)
        assertEquals(0.0, rahu.distanceAu, 0.0)
        assertEquals(-0.05295, rahu.speedDegPerDay, 2e-4) // mean regression, about -19.34 deg per year
    }

    @Test
    fun ketuIsRahuPlus180() {
        for (tt in listOf(j2000, JulianDay(2448908.5), JulianDay(2460000.25))) {
            for ((rahu, ketu) in listOf(Body.RAHU_MEAN to Body.KETU_MEAN, Body.RAHU_TRUE to Body.KETU_TRUE)) {
                val r = eph.geoApparent(rahu, tt)
                val k = eph.geoApparent(ketu, tt)
                assertEquals(0.0, wrap180(k.lonDeg - r.lonDeg - 180.0), 1e-12)
                assertEquals(r.speedDegPerDay, k.speedDegPerDay, 0.0)
                assertTrue(k.lonDeg >= 0.0 && k.lonDeg < 360.0)
            }
        }
    }

    @Test
    fun trueNodeStaysNearMeanNode() {
        // The osculating node oscillates about the mean node by at most about 1.7 deg.
        for (k in 0 until 200) {
            val tt = JulianDay(2415020.5 + k * 365.123)
            val d = wrap180(eph.geoApparent(Body.RAHU_TRUE, tt).lonDeg - eph.geoApparent(Body.RAHU_MEAN, tt).lonDeg)
            assertTrue(abs(d) < 2.0, "true - mean node = $d deg at ${tt.value}")
        }
    }

    @Test
    fun allBodiesAreFiniteAndNormalised() {
        for (k in 0 until 50) {
            val tt = JulianDay(2415020.5 + k * 1460.97)
            for (body in Body.entries) {
                val p = eph.geoApparent(body, tt)
                assertTrue(p.lonDeg >= 0.0 && p.lonDeg < 360.0, "$body lon ${p.lonDeg}")
                assertTrue(abs(p.latDeg) < 12.0, "$body lat ${p.latDeg}")
                assertTrue(p.speedDegPerDay.isFinite() && abs(p.speedDegPerDay) < 16.0, "$body speed")
                val np = eph.geoApparentNoSpeed(body, tt)
                assertEquals(p.lonDeg, np.lonDeg, 0.0)
                assertEquals(p.latDeg, np.latDeg, 0.0)
                assertEquals(p.distanceAu, np.distanceAu, 0.0)
            }
        }
    }

    @Test
    fun moonSpeedAndDistanceArePlausible() {
        val moon = eph.geoApparent(Body.MOON, j2000)
        assertTrue(moon.speedDegPerDay in 11.5..15.5, "Moon speed ${moon.speedDegPerDay}")
        assertTrue(moon.distanceAu * 149597870.7 in 356000.0..407000.0)
        // Repeat calls are deterministic (caches must not change results).
        assertEquals(moon, eph.geoApparent(Body.MOON, j2000))
    }

    @Test
    fun baseSunMeeusExample25b() {
        val sun = base.geoApparent(Body.SUN, JulianDay(2448908.5))
        assertTrue(abs(wrap180(sun.lonDeg - 199.906061)) <= 20.0 / 3600.0, "Base Sun lon ${sun.lonDeg}")
        assertTrue(abs(sun.latDeg) < 1.5 / 3600.0)
        // Sanity only (the spec gates no distance): the library's truncated series gives 0.9976049 here,
        // 2.9e-6 au from Meeus and from the precision backend.
        assertEquals(0.99760775, sun.distanceAu, 1e-5)
        assertEquals(0.99, sun.speedDegPerDay, 0.02)
    }

    @Test
    fun baseMeanNodeAtJ2000() {
        val dpsiDeg = Math.toDegrees(EarthOrientation.nutation(j2000).dpsiRad)
        val rahu = base.geoApparent(Body.RAHU_MEAN, j2000)
        assertEquals(125.044555, rahu.lonDeg - dpsiDeg, 1e-6)
        assertEquals(0.0, rahu.latDeg, 0.0)
        assertEquals(0.0, rahu.distanceAu, 0.0)
        // The mean node is our own formula in both backends: identical, not merely close.
        assertEquals(eph.geoApparent(Body.RAHU_MEAN, j2000), rahu)
    }

    @Test
    fun baseKetuIsRahuPlus180() {
        for (tt in listOf(j2000, JulianDay(2448908.5), JulianDay(2460000.25), JulianDay(2350000.5))) {
            for ((rahu, ketu) in listOf(Body.RAHU_MEAN to Body.KETU_MEAN, Body.RAHU_TRUE to Body.KETU_TRUE)) {
                val r = base.geoApparent(rahu, tt)
                val k = base.geoApparent(ketu, tt)
                assertEquals(0.0, wrap180(k.lonDeg - r.lonDeg - 180.0), 1e-12)
                assertEquals(r.speedDegPerDay, k.speedDegPerDay, 0.0)
                assertTrue(k.lonDeg >= 0.0 && k.lonDeg < 360.0)
            }
        }
    }

    @Test
    fun baseRangeIs1700To2200() {
        assertEquals(2341972.5, base.validRange.start.value, 0.0)
        assertEquals(2524593.5, base.validRange.endInclusive.value, 0.0)
        for (jd in listOf(2341972.5, 2524593.5)) {
            for (body in Body.entries) {
                val p = base.geoApparent(body, JulianDay(jd))
                assertTrue(p.lonDeg >= 0.0 && p.lonDeg < 360.0 && p.speedDegPerDay.isFinite(), "$body at $jd")
                val np = base.geoApparentNoSpeed(body, JulianDay(jd))
                assertEquals(p.copy(speedDegPerDay = 0.0), np)
            }
        }
        for (jd in listOf(2341972.4999, 2524593.5001, Double.NaN)) {
            assertFailsWith<EphemerisRangeException> { base.geoApparent(Body.SUN, JulianDay(jd)) }
            assertFailsWith<EphemerisRangeException> { base.geoApparentNoSpeed(Body.KETU_TRUE, JulianDay(jd)) }
        }
    }

    /**
     * Early warning for the G2 gate (not a replacement): Base against Precision at the Base column of the
     * section 4 table. Precision is within 0.003 arcsec of the oracle, so this is the same comparison.
     */
    @Test
    fun baseStaysWithinItsGoldenToleranceOfPrecision() {
        val lonLimitArcsec = mapOf(
            Body.SUN to 20.0, Body.MOON to 10.0, Body.MERCURY to 60.0, Body.VENUS to 60.0, Body.MARS to 60.0,
            Body.JUPITER to 60.0, Body.SATURN to 60.0, Body.RAHU_MEAN to 0.1, Body.RAHU_TRUE to 60.0,
        )
        val worstLon = LinkedHashMap<Body, Double>()
        val worstLat = LinkedHashMap<Body, Double>()
        val worstSpeed = LinkedHashMap<Body, Double>()
        val worstDistance = LinkedHashMap<Body, Double>()
        for (k in 0 until 500) {
            val tt = JulianDay(2415020.5 + (k + 0.37) * (2488069.5 - 2415020.5) / 500.0)
            for ((body, limit) in lonLimitArcsec) {
                val b = base.geoApparent(body, tt)
                val p = eph.geoApparent(body, tt)
                val dLon = abs(wrap180(b.lonDeg - p.lonDeg)) * 3600.0
                val dLat = abs(b.latDeg - p.latDeg) * 3600.0
                val dSpeed = abs(b.speedDegPerDay - p.speedDegPerDay)
                worstLon[body] = maxOf(worstLon[body] ?: 0.0, dLon)
                worstLat[body] = maxOf(worstLat[body] ?: 0.0, dLat)
                worstSpeed[body] = maxOf(worstSpeed[body] ?: 0.0, dSpeed)
                if (p.distanceAu > 0.0) {
                    val relative = abs(b.distanceAu - p.distanceAu) / p.distanceAu
                    worstDistance[body] = maxOf(worstDistance[body] ?: 0.0, relative)
                }
                assertTrue(dLon <= limit, "$body at ${tt.value}: Base - Precision = $dLon arcsec (limit $limit)")
                assertTrue(dLat <= limit, "$body at ${tt.value}: latitude differs by $dLat arcsec (limit $limit)")
                assertTrue(dSpeed <= 1e-2, "$body at ${tt.value}: speed differs by $dSpeed deg/day")
            }
        }
        println("SmokePositionsTest: worst Base - Precision over 500 epochs")
        println("  longitude (arcsec): $worstLon")
        println("  latitude (arcsec): $worstLat")
        println("  speed (deg/day): $worstSpeed")
        println("  distance (relative, not gated): $worstDistance")
    }

    @Test
    fun selectionSwitchesBackendAtThePrecisionRangeEnds() {
        val selecting = SelectingEphemeris(eph, base)
        val inside = JulianDay(2415020.5)
        val outside = JulianDay(2415020.4999)
        assertEquals(BackendId.PRECISION_DE440, selecting.backendFor(inside))
        assertEquals(BackendId.BASE_ASTRONOMY_ENGINE, selecting.backendFor(outside))
        for (body in Body.entries) {
            assertEquals(eph.geoApparent(body, inside), selecting.geoApparent(body, inside))
            assertEquals(base.geoApparent(body, outside), selecting.geoApparent(body, outside))
            assertEquals(eph.geoApparentNoSpeed(body, inside), selecting.geoApparentNoSpeed(body, inside))
            assertEquals(base.geoApparentNoSpeed(body, outside), selecting.geoApparentNoSpeed(body, outside))
        }
        assertEquals(base.validRange, selecting.validRange)
        // Without the precision file the same instant comes from the base backend.
        assertEquals(base.geoApparent(Body.MOON, inside), SelectingEphemeris(null, base).geoApparent(Body.MOON, inside))
    }
}
