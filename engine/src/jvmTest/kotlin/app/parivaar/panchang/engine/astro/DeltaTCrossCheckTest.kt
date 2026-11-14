package app.parivaar.panchang.engine.astro

import io.github.cosinekitty.astronomy.Time
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Cross-checks our Espenak-Meeus deltaT rows (all eras, including the pre-1900 rows that the oracle does not
 * cover) against the independent implementation of the same polynomials in Astronomy Engine (MIT, test-side
 * use only). Astronomy Engine maps a UT day count d (from J2000) to y = 2000 + (d - 14) / 365.24217, so d is
 * chosen to hit the wanted y exactly; this compares coefficients, not decimal-year conventions.
 */
class DeltaTCrossCheckTest {
    @Test
    fun polynomialRowsAgreeWithAstronomyEngine() {
        var y = -1000.0
        var worst = 0.0
        while (y <= 2300.0) {
            val utDays = (y - 2000.0) * 365.24217 + 14.0
            val time = Time(utDays)
            val theirs = (time.tt - time.ut) * 86400.0
            val ours = TimeScales.deltaTSeconds(y)
            // (tt - ut) loses about 1e-6 s at |ut| ~ 1e6 days; real coefficient slips are far larger.
            val tol = 2e-5 + 1e-9 * abs(theirs)
            assertTrue(abs(ours - theirs) <= tol, "deltaT($y): ours $ours, Astronomy Engine $theirs")
            worst = maxOf(worst, abs(ours - theirs))
            y += 0.37
        }
        println("DeltaTCrossCheckTest: worst |difference| = $worst s")
    }
}
