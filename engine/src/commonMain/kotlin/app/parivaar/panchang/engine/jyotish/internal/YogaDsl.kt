package app.parivaar.panchang.engine.jyotish.internal

import app.parivaar.panchang.engine.jyotish.ChartFacts
import app.parivaar.panchang.engine.jyotish.Graha
import app.parivaar.panchang.engine.jyotish.Rashi
import app.parivaar.panchang.engine.jyotish.Zodiac

private const val RASHI_COUNT = 12
private const val DEBILITATION_OFFSET = 6

/** A graha, or the lord of the rashi of a house counted from the lagna (docs/specs/M02.md section 3.8). */
internal sealed interface Term {
    fun graha(f: ChartFacts): Graha

    data class Fixed(val graha: Graha) : Term {
        override fun graha(f: ChartFacts): Graha = graha
    }

    data class LordOf(val house: Int) : Term {
        override fun graha(f: ChartFacts): Graha =
            Zodiac.lordOf(Rashi.entries[(f.lagna.ordinal + house - 1) % RASHI_COUNT])
    }
}

private fun Term.rashi(f: ChartFacts): Rashi = f.of(graha(f)).rashi

/** The reference sign of a `from`: null means the lagna. */
private fun Term?.refRashi(f: ChartFacts): Rashi = this?.rashi(f) ?: f.lagna

/** One condition of the yoga DSL. Houses are always counted by sign, whatever the chart's house system. */
internal sealed interface Cond {
    fun eval(f: ChartFacts): Boolean

    data class AllOf(val items: List<Cond>) : Cond {
        override fun eval(f: ChartFacts): Boolean = items.all { it.eval(f) }
    }

    data class AnyOf(val items: List<Cond>) : Cond {
        override fun eval(f: ChartFacts): Boolean = items.any { it.eval(f) }
    }

    data class Not(val item: Cond) : Cond {
        override fun eval(f: ChartFacts): Boolean = !item.eval(f)
    }

    /** [graha] sits in one of [houses] counted from [from] (null = lagna). Kendra and trikona are sets of it. */
    data class InHouse(val graha: Term, val from: Term?, val houses: Set<Int>) : Cond {
        override fun eval(f: ChartFacts): Boolean = Zodiac.houseFrom(from.refRashi(f), graha.rashi(f)) in houses
    }

    data class NoneInHouses(val grahas: List<Term>, val from: Term?, val houses: Set<Int>) : Cond {
        override fun eval(f: ChartFacts): Boolean {
            val ref = from.refRashi(f)
            return grahas.none { Zodiac.houseFrom(ref, it.rashi(f)) in houses }
        }
    }

    data class Conjunct(val a: Term, val b: Term) : Cond {
        override fun eval(f: ChartFacts): Boolean = a.graha(f) != b.graha(f) && a.rashi(f) == b.rashi(f)
    }

    data class Aspect(val by: Term, val on: Term) : Cond {
        override fun eval(f: ChartFacts): Boolean =
            by.graha(f) != on.graha(f) && Zodiac.houseFrom(by.rashi(f), on.rashi(f)) in Zodiac.aspectsOf(by.graha(f))
    }

    data class Exchange(val a: Term, val b: Term) : Cond {
        override fun eval(f: ChartFacts): Boolean {
            val ga = a.graha(f)
            val gb = b.graha(f)
            return ga != gb && Zodiac.lordOf(a.rashi(f)) == gb && Zodiac.lordOf(b.rashi(f)) == ga
        }
    }

    data class Exalted(val t: Term) : Cond {
        override fun eval(f: ChartFacts): Boolean = Zodiac.exaltationOf(t.graha(f)) == t.rashi(f)
    }

    data class Debilitated(val t: Term) : Cond {
        override fun eval(f: ChartFacts): Boolean {
            val exalt = Zodiac.exaltationOf(t.graha(f)) ?: return false
            return Rashi.entries[(exalt.ordinal + DEBILITATION_OFFSET) % RASHI_COUNT] == t.rashi(f)
        }
    }

    data class OwnSign(val t: Term) : Cond {
        override fun eval(f: ChartFacts): Boolean = Zodiac.lordOf(t.rashi(f)) == t.graha(f)
    }

    /** Every graha lies strictly between [from] and [to] walking forward from [from]. */
    data class AllInArc(val grahas: List<Term>, val from: Term, val to: Term) : Cond {
        override fun eval(f: ChartFacts): Boolean {
            val start = f.of(from.graha(f)).lon
            val width = Zodiac.wrap360(f.of(to.graha(f)).lon - start)
            return grahas.all {
                val off = Zodiac.wrap360(f.of(it.graha(f)).lon - start)
                off > 0.0 && off < width
            }
        }
    }
}
