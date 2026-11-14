package app.parivaar.panchang.engine.astro

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** docs/specs/M01.md section 4, `SelectingEphemerisTest` (fakes only; no real backend). */
class SelectingEphemerisTest {
    /** Answers every call inside [validRange] with `lonDeg = tag` and counts the calls. */
    private class FakeEphemeris(from: Double, to: Double, private val tag: Double) : Ephemeris {
        var calls = 0
        override val validRange: ClosedRange<JulianDay> = JulianDay(from)..JulianDay(to)

        override fun geoApparent(body: Body, tt: JulianDay): EclipticPos {
            if (tt !in validRange) throw EphemerisRangeException(tt)
            calls++
            return EclipticPos(lonDeg = tag, latDeg = 0.0, distanceAu = 1.0, speedDegPerDay = tag)
        }
    }

    private fun precision() = FakeEphemeris(PRECISION_FROM, PRECISION_TO, PRECISION_TAG)

    private fun base() = FakeEphemeris(BASE_FROM, BASE_TO, BASE_TAG)

    @Test
    fun withoutPrecisionEverythingIsBase() {
        val base = base()
        val eph = SelectingEphemeris(precision = null, base = base)
        for (jd in listOf(BASE_FROM, PRECISION_FROM, 2451545.0, PRECISION_TO, BASE_TO)) {
            assertEquals(BackendId.BASE_ASTRONOMY_ENGINE, eph.backendFor(JulianDay(jd)), "jd $jd")
            assertEquals(BASE_TAG, eph.geoApparent(Body.SUN, JulianDay(jd)).lonDeg, 0.0)
        }
        assertEquals(5, base.calls)
    }

    @Test
    fun precisionRangeIsInclusive() {
        val eph = SelectingEphemeris(precision(), base())
        assertEquals(BackendId.PRECISION_DE440, eph.backendFor(JulianDay(2415020.5)))
        assertEquals(BackendId.PRECISION_DE440, eph.backendFor(JulianDay(2451545.0)))
        assertEquals(BackendId.PRECISION_DE440, eph.backendFor(JulianDay(2488069.5)))
        assertEquals(BackendId.BASE_ASTRONOMY_ENGINE, eph.backendFor(JulianDay(2415020.4999)))
        assertEquals(BackendId.BASE_ASTRONOMY_ENGINE, eph.backendFor(JulianDay(2488069.5001)))
        assertEquals(BackendId.BASE_ASTRONOMY_ENGINE, eph.backendFor(JulianDay(BASE_FROM)))
        assertEquals(BackendId.BASE_ASTRONOMY_ENGINE, eph.backendFor(JulianDay(BASE_TO)))
    }

    @Test
    fun geoApparentGoesToTheSelectedBackendOnly() {
        val precision = precision()
        val base = base()
        val eph = SelectingEphemeris(precision, base)
        assertEquals(PRECISION_TAG, eph.geoApparent(Body.MOON, JulianDay(2415020.5)).lonDeg, 0.0)
        assertEquals(PRECISION_TAG, eph.geoApparent(Body.MOON, JulianDay(2488069.5)).lonDeg, 0.0)
        assertEquals(2, precision.calls)
        assertEquals(0, base.calls)
        assertEquals(BASE_TAG, eph.geoApparent(Body.MOON, JulianDay(2415020.4999)).lonDeg, 0.0)
        assertEquals(BASE_TAG, eph.geoApparent(Body.MOON, JulianDay(2488069.5001)).lonDeg, 0.0)
        assertEquals(2, precision.calls)
        assertEquals(2, base.calls)
        // The position-only path used by rise/set selects the same way.
        assertEquals(PRECISION_TAG, eph.geoApparentNoSpeed(Body.SUN, JulianDay(2451545.0)).lonDeg, 0.0)
        assertEquals(BASE_TAG, eph.geoApparentNoSpeed(Body.SUN, JulianDay(2400000.5)).lonDeg, 0.0)
    }

    @Test
    fun outsideTheBaseRangeThrows() {
        for (eph in listOf(SelectingEphemeris(precision(), base()), SelectingEphemeris(null, base()))) {
            for (jd in listOf(BASE_FROM - 1e-4, BASE_TO + 1e-4, 0.0, 1e9, Double.NaN)) {
                val tt = JulianDay(jd)
                assertFailsWith<EphemerisRangeException>("backendFor($jd)") { eph.backendFor(tt) }
                assertFailsWith<EphemerisRangeException>("geoApparent($jd)") { eph.geoApparent(Body.SUN, tt) }
                assertFailsWith<EphemerisRangeException>("noSpeed($jd)") { eph.geoApparentNoSpeed(Body.SUN, tt) }
            }
        }
    }

    @Test
    fun rangeExceptionKeepsTheRequestedInstantOutOfItsText() {
        // The requested instant can be a birth instant: it stays a property and never reaches message/toString.
        val requested = JulianDay(BASE_TO + 123.456)
        val thrown = listOf(
            EphemerisRangeException(requested),
            assertFailsWith<EphemerisRangeException> { SelectingEphemeris(precision(), base()).backendFor(requested) },
            assertFailsWith<EphemerisRangeException> {
                SelectingEphemeris(null, base()).geoApparent(Body.MOON, requested)
            },
        )
        for (e in thrown) {
            assertEquals(requested, e.tt)
            for (text in listOf(e.message.orEmpty(), e.toString())) {
                assertTrue(text.isNotBlank())
                assertTrue(text.none { it.isDigit() }, "exception text carries digits")
            }
        }
    }

    @Test
    fun validRangeIsTheBaseRange() {
        val eph = SelectingEphemeris(precision(), base())
        assertEquals(BASE_FROM, eph.validRange.start.value, 0.0)
        assertEquals(BASE_TO, eph.validRange.endInclusive.value, 0.0)
    }

    @Test
    fun precisionOutsideTheBaseRangeIsStillServed() {
        // The rule tests the precision range first, so a precision backend wider than the base one is used.
        val wide = FakeEphemeris(BASE_FROM - 10.0, PRECISION_TO, PRECISION_TAG)
        val eph = SelectingEphemeris(wide, base())
        assertEquals(BackendId.PRECISION_DE440, eph.backendFor(JulianDay(BASE_FROM - 5.0)))
    }

    private companion object {
        const val PRECISION_FROM = 2415020.5
        const val PRECISION_TO = 2488069.5
        const val BASE_FROM = 2341972.5
        const val BASE_TO = 2524593.5
        const val PRECISION_TAG = 1.0
        const val BASE_TAG = 2.0
    }
}
