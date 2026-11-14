package app.parivaar.panchang.engine.astro.internal

import kotlin.math.abs

/**
 * Root refinement by Brent's method (R. P. Brent, "Algorithms for Minimization without Derivatives", 1973,
 * chapter 4): inverse quadratic interpolation or the secant rule while they make good progress, bisection
 * otherwise. Written for this project from the description of the method; not derived from any other code.
 *
 * Three points are kept: `best` (the current estimate, smallest |f| of the bracket), `far` (the other end of
 * the bracket, where f has the opposite sign) and `prev` (the estimate before `best`).
 */
internal object Brent {
    private const val MAX_ITERATIONS = 200

    /** Smallest move, as a fraction of the requested bracket; below 0.5 so that it cannot jump past `far`. */
    private const val MIN_STEP_FRACTION = 0.4

    /** An interpolated step may cover at most this fraction of the bracket, measured from `best`. */
    private const val MAX_BRACKET_FRACTION = 0.75

    /**
     * Refines a root of [f] bracketed by [x0] and [x1], where [f0] = f(x0) and [f1] = f(x1) are zero or of
     * opposite sign. Iterates until f is zero at the best point or the bracket is no wider than [maxBracket],
     * then returns the point where the chord through the two ends of that bracket crosses zero. The result
     * lies inside the bracket, so f changes sign within [maxBracket] of it; for a smooth f it is far closer
     * to the root than that, at no extra evaluation.
     */
    fun findRoot(x0: Double, f0: Double, x1: Double, f1: Double, maxBracket: Double, f: (Double) -> Double): Double {
        require(maxBracket > 0.0) { "maxBracket must be positive" }
        require((f0 <= 0.0 && f1 >= 0.0) || (f0 >= 0.0 && f1 <= 0.0)) { "the root is not bracketed" }
        val s = State(x0, f0, x1, f1)
        val minStep = MIN_STEP_FRACTION * maxBracket
        var iterations = 0
        while (s.fBest != 0.0 && abs(s.far - s.best) > maxBracket && iterations < MAX_ITERATIONS) {
            val half = (s.far - s.best) / 2.0
            val interpolated = s.interpolatedStep(half, minStep)
            val step = if (interpolated.isNaN()) half else interpolated
            s.stepBeforeLast = if (interpolated.isNaN()) half else s.lastStep
            s.lastStep = step
            // Never move by less than minStep: a tiny step would leave the far end where it is for ever.
            val move = if (abs(step) >= minStep) step else if (half > 0.0) minStep else -minStep
            s.advance(s.best + move, f)
            iterations++
        }
        return if (s.fBest == 0.0) s.best else s.best - s.fBest * (s.far - s.best) / (s.fFar - s.fBest)
    }

    private class State(x0: Double, f0: Double, x1: Double, f1: Double) {
        var best = x1
        var fBest = f1
        var far = x0
        var fFar = f0
        var prev = x0
        var fPrev = f0
        var lastStep = x1 - x0
        var stepBeforeLast = lastStep

        init {
            keepBestAtSmallerValue()
        }

        /**
         * The step from `best` proposed by interpolation, or NaN when bisection must be used: the last steps
         * were already tiny or did not reduce |f|, or the proposal leaves the first three quarters of the
         * bracket, or it is not under half the step before last (so the steps shrink at least geometrically).
         */
        fun interpolatedStep(half: Double, minStep: Double): Double {
            if (abs(stepBeforeLast) < minStep || abs(fPrev) <= abs(fBest)) return Double.NaN
            val step = if (prev != far && fPrev != fFar && fFar != fBest) {
                // Inverse quadratic interpolation: x as a parabola in f through the three points, at f = 0.
                (far - best) * fBest * fPrev / ((fFar - fBest) * (fFar - fPrev)) +
                    (prev - best) * fBest * fFar / ((fPrev - fBest) * (fPrev - fFar))
            } else {
                // Secant through best and prev.
                -fBest * (best - prev) / (fBest - fPrev)
            }
            val inside = step * half > 0.0 && abs(step) < MAX_BRACKET_FRACTION * 2.0 * abs(half)
            return if (inside && abs(step) < abs(stepBeforeLast) / 2.0) step else Double.NaN
        }

        /** Evaluates f at [x], makes it the new estimate and restores the invariants of the three points. */
        fun advance(x: Double, f: (Double) -> Double) {
            prev = best
            fPrev = fBest
            best = x
            fBest = f(x)
            if (fBest != 0.0 && (fBest > 0.0) == (fFar > 0.0)) {
                // The root now lies between the new estimate and the previous one.
                far = prev
                fFar = fPrev
                lastStep = best - far
                stepBeforeLast = lastStep
            }
            keepBestAtSmallerValue()
        }

        private fun keepBestAtSmallerValue() {
            if (abs(fFar) >= abs(fBest)) return
            prev = best
            fPrev = fBest
            best = far
            fBest = fFar
            far = prev
            fFar = fPrev
        }
    }
}
