package app.parivaar.panchang.engine.astro

import app.parivaar.panchang.engine.astro.internal.Brent
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sign
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Own root finder used by rise/set (docs/specs/M01.md section 3.8): bracket no wider than requested. */
class BrentTest {
    private class Counted(private val f: (Double) -> Double) : (Double) -> Double {
        var calls = 0

        override fun invoke(x: Double): Double {
            calls++
            return f(x)
        }
    }

    /** Solves and asserts that the function changes sign within +/- [width] of the answer. */
    private fun solve(lo: Double, hi: Double, width: Double, maxCalls: Int, f: (Double) -> Double): Double {
        val counted = Counted(f)
        val x = Brent.findRoot(lo, f(lo), hi, f(hi), width, counted)
        assertTrue(x in minOf(lo, hi)..maxOf(lo, hi), "answer $x outside the bracket")
        val before = f(maxOf(minOf(lo, hi), x - width))
        val after = f(minOf(maxOf(lo, hi), x + width))
        assertTrue(before == 0.0 || after == 0.0 || sign(before) != sign(after), "no sign change around $x")
        assertTrue(counted.calls <= maxCalls, "${counted.calls} evaluations (limit $maxCalls)")
        return x
    }

    @Test
    fun smoothFunctionsConvergeInFewEvaluations() {
        assertEquals(1.5707963267948966, solve(1.0, 2.0, 1e-9, 12) { cos(it) }, 1e-9)
        assertEquals(2.0945514815423265, solve(2.0, 3.0, 1e-9, 12) { it * it * it - 2.0 * it - 5.0 }, 1e-9)
        // Rise/set shaped problem: a sine through a threshold on a 600 s bracket, 0.5 s target.
        val x = solve(0.0, 600.0, 0.5, 8) { sin((it - 250.0) * 7.27e-5) }
        assertEquals(250.0, x, 0.5)
    }

    @Test
    fun bracketOrderAndSignDoNotMatter() {
        assertEquals(1.5707963267948966, solve(2.0, 1.0, 1e-9, 12) { cos(it) }, 1e-9)
        assertEquals(1.5707963267948966, solve(1.0, 2.0, 1e-9, 12) { -cos(it) }, 1e-9)
    }

    @Test
    fun hardFunctionsStillMeetTheBracket() {
        // Flat on one side, steep on the other: interpolation misleads, bisection must take over.
        assertEquals(0.0, solve(-4.0, 4.0 / 3.0, 1e-8, 60) { if (it < 0.0) -exp(it) * it * it else it * 1e3 }, 1e-8)
        // High-order root.
        assertEquals(0.0, solve(-1.0, 1.3, 1e-6, 120) { it * it * it * it * it * it * it * it * it }, 1e-6)
        // Step function: no continuity at all.
        assertEquals(0.3, solve(0.0, 1.0, 1e-6, 60) { if (it < 0.3) -1.0 else 1.0 }, 1e-6)
    }

    @Test
    fun zeroAtAnEndpointIsReturnedWithoutEvaluations() {
        val counted = Counted { it - 3.0 }
        assertEquals(3.0, Brent.findRoot(3.0, 0.0, 9.0, 6.0, 0.5, counted), 0.0)
        assertEquals(9.0, Brent.findRoot(3.0, -6.0, 9.0, 0.0, 0.5, counted), 0.0)
        assertEquals(0, counted.calls)
    }

    @Test
    fun theAnswerIsInterpolatedInsideTheFinalBracket() {
        // Already narrow enough: no evaluation, the chord through the two ends gives the root of a line.
        val counted = Counted { it - 0.1 }
        assertEquals(0.1, Brent.findRoot(0.0, -0.1, 0.4, 0.3, 0.5, counted), 1e-15)
        assertEquals(0, counted.calls)
        // A smooth function is located far better than the bracket width that was asked for.
        val x = solve(0.0, 600.0, 0.5, 8) { sin((it - 250.0) * 7.27e-5) }
        assertTrue(abs(x - 250.0) < 1e-6, "root $x")
    }

    @Test
    fun rejectsABracketWithoutSignChange() {
        assertFailsWith<IllegalArgumentException> { Brent.findRoot(0.0, 1.0, 1.0, 2.0, 0.5) { it + 1.0 } }
        assertFailsWith<IllegalArgumentException> { Brent.findRoot(0.0, -1.0, 1.0, 1.0, 0.0) { it } }
        assertFailsWith<IllegalArgumentException> { Brent.findRoot(0.0, Double.NaN, 1.0, 1.0, 0.5) { it } }
    }
}
