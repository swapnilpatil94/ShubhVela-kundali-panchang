package app.parivaar.panchang.engine.panchang

import app.parivaar.panchang.engine.CalcSettings
import app.parivaar.panchang.engine.astro.Ephemeris
import app.parivaar.panchang.engine.astro.TimeTables
import app.parivaar.panchang.engine.astro.internal.Brent
import app.parivaar.panchang.engine.jyotish.AyanamsaId
import app.parivaar.panchang.engine.panchang.internal.LimbFunction
import app.parivaar.panchang.engine.panchang.internal.LimbSample
import app.parivaar.panchang.engine.panchang.internal.arcIndex
import app.parivaar.panchang.engine.panchang.internal.arcStartDeg
import app.parivaar.panchang.engine.panchang.internal.boundaryDistance
import app.parivaar.panchang.engine.panchang.internal.function
import app.parivaar.panchang.engine.panchang.internal.reportedIndex
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Instant

/**
 * Start and end instants of the five limbs (docs/specs/M03.md section 3.1).
 *
 * Boundaries are bracketed on a 2 h grid anchored to the Unix epoch and refined by Brent's method inside the
 * grid cell, so a boundary is bit-identical whichever query finds it (a single day and a year agree). A
 * span always contains its query instant: within a fraction of a millisecond of a boundary the solved
 * instant, not the floor rule on F, decides the side. Holds caches of grid samples and roots (bounded; they
 * never change a result): not thread-safe.
 */
class LimbSolver(private val eph: Ephemeris, private val tables: TimeTables = TimeTables.DEFAULT) {
    private data class RootKey(val fn: LimbFunction, val boundaryDeg: Double, val cell: Long)

    private val samples = HashMap<Long, LimbSample>()
    private val roots = HashMap<RootKey, Instant>()
    private var cachedAyanamsa: AyanamsaId? = null

    /** The index of [k] at [at] by the floor rule on F(at) (TITHI 1..30, KARANA 1..60, others 0-based). */
    fun indexAt(k: LimbKind, at: Instant, s: CalcSettings = CalcSettings()): Int =
        reportedIndex(k, arcIndex(k, LimbSample.at(eph, tables, at, s.ayanamsa).value(k.function)))

    /** The span of [k] that contains [at] (start <= at < end); kshaya and adhika are false. */
    fun spanAt(k: LimbKind, at: Instant, s: CalcSettings = CalcSettings()): LimbSpan {
        prepare(s)
        val fn = k.function
        var j = arcIndex(k, LimbSample.at(eph, tables, at, s.ayanamsa).value(fn))
        val home = cellOf(at)
        var start = boundary(fn, arcStartDeg(k, j), home)
        var end = boundary(fn, arcStartDeg(k, j + 1), home)
        if (at < start) {
            end = start
            j -= 1
            start = boundary(fn, arcStartDeg(k, j), cellOf(end))
        } else if (at >= end) {
            start = end
            j += 1
            end = boundary(fn, arcStartDeg(k, j + 1), cellOf(start))
        }
        return LimbSpan(k, reportedIndex(k, j.mod(k.divisions)), start, end)
    }

    /**
     * The spans of [k] overlapping [from, to), in time order: [spanAt] of [from], then each next span starts
     * where the previous one ends (same instant, no re-solve). Throws [IllegalArgumentException] unless
     * [from] < [to].
     */
    fun spans(k: LimbKind, from: Instant, to: Instant, s: CalcSettings = CalcSettings()): List<LimbSpan> {
        require(from < to) { "the interval must not be empty" }
        val fn = k.function
        val out = ArrayList<LimbSpan>()
        var span = spanAt(k, from, s)
        out += span
        while (span.end < to) {
            val j = span.index - k.firstIndex + 1
            val end = boundary(fn, arcStartDeg(k, j + 1), cellOf(span.end))
            span = LimbSpan(k, reportedIndex(k, j.mod(k.divisions)), span.end, end)
            out += span
        }
        return out
    }

    private fun prepare(s: CalcSettings) {
        if (s.ayanamsa != cachedAyanamsa || samples.size > MAX_CACHE || roots.size > MAX_CACHE) {
            samples.clear()
            roots.clear()
            cachedAyanamsa = s.ayanamsa
        }
    }

    /**
     * The instant where F of [fn] reaches [b]: the unique grid cell [t_m, t_m+1] with g(t_m) < 0 <= g(t_m+1),
     * searched from cell [from] (forward if g < 0 there, else backward), then Brent inside it.
     */
    private fun boundary(fn: LimbFunction, b: Double, from: Long): Instant {
        var m = from
        var steps = 0
        if (g(fn, b, m) < 0.0) {
            while (g(fn, b, m + 1) < 0.0) {
                m++
                check(++steps <= MAX_STEPS) { "limb boundary not bracketed" }
            }
        } else {
            do {
                m--
                check(++steps <= MAX_STEPS) { "limb boundary not bracketed" }
            } while (g(fn, b, m) >= 0.0)
        }
        return roots.getOrPut(RootKey(fn, b, m)) { root(fn, b, m) }
    }

    private fun root(fn: LimbFunction, b: Double, m: Long): Instant {
        val left = gridInstant(m)
        val ayanamsa = checkNotNull(cachedAyanamsa)
        val x = Brent.findRoot(0.0, g(fn, b, m), STEP_SECONDS.toDouble(), g(fn, b, m + 1), MAX_BRACKET_S) { sec ->
            boundaryDistance(LimbSample.at(eph, tables, left + offset(sec), ayanamsa).value(fn), b)
        }
        return left + offset(x)
    }

    private fun g(fn: LimbFunction, b: Double, m: Long): Double {
        val sample = samples.getOrPut(m) {
            LimbSample.at(eph, tables, gridInstant(m), checkNotNull(cachedAyanamsa))
        }
        return boundaryDistance(sample.value(fn), b)
    }

    private companion object {
        const val STEP_SECONDS = 7_200L
        const val MAX_STEPS = 40
        const val MAX_BRACKET_S = 1.0
        const val NANOS_PER_SECOND = 1e9
        const val MAX_CACHE = 4_096

        fun cellOf(at: Instant): Long = at.epochSeconds.floorDiv(STEP_SECONDS)

        fun gridInstant(m: Long): Instant = Instant.fromEpochSeconds(m * STEP_SECONDS)

        fun offset(seconds: Double) = (seconds * NANOS_PER_SECOND).roundToLong().nanoseconds
    }
}
