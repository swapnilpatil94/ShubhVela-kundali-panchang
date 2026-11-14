package app.parivaar.panchang.engine.jyotish

import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit
import kotlin.time.Instant

/**
 * Lords of the Yogini dasha, in order, with their years (total 36).
 * VERIFY with pandit: order, start rule and lords; the UI flag stays off until sign-off.
 */
@Suppress("MagicNumber") // each value is the definition of the period length in years
enum class Yogini(val years: Int) {
    MANGALA(1),
    PINGALA(2),
    DHANYA(3),
    BHRAMARI(4),
    BHADRIKA(5),
    ULKA(6),
    SIDDHA(7),
    SANKATA(8),
}

/**
 * One dasha period. Its dates follow from the birth instant, so `toString` is redacted.
 *
 * @property lords the lord of every level down to this period (size = level; 1 = maha)
 * @property startDay start in days from birth (negative before birth)
 * @property endDay end in days from birth, exclusive
 * @property start birth + [startDay], rounded to the millisecond
 * @property end birth + [endDay], rounded to the millisecond
 */
data class DashaPeriod<L>(
    val lords: List<L>,
    val startDay: Double,
    val endDay: Double,
    val start: Instant,
    val end: Instant,
) {
    override fun toString(): String = "DashaPeriod(redacted)"
}

/**
 * A dasha sequence with its sub-periods (docs/specs/M02.md section 3.4). Sub-periods are computed on demand;
 * the tree holds only the maha periods. Immutable.
 *
 * @property maxDepth the deepest level (5 for Vimshottari, 3 for Yogini)
 * @property maha the level-1 periods in time order; the first one contains the birth
 */
class DashaTree<L> internal constructor(
    private val order: List<L>,
    private val years: List<Int>,
    private val birth: Instant,
    val maxDepth: Int,
    val maha: List<DashaPeriod<L>>,
) {
    private val totalYears = years.sum()

    /**
     * The sub-periods of [p], in time order: they start with the lord of [p], follow the dasha order and
     * share the length of [p] in proportion to their years. They tile [p] exactly: each one starts on the
     * bit-identical day its predecessor ends, the first at `p.startDay`, the last ending at `p.endDay`.
     * Throws [IllegalArgumentException] for a period at [maxDepth].
     */
    fun children(p: DashaPeriod<L>): List<DashaPeriod<L>> {
        require(p.lords.isNotEmpty() && p.lords.size < maxDepth) { "the period has no sub-periods" }
        val first = order.indexOf(p.lords.last())
        require(first >= 0) { "the period does not belong to this dasha" }
        val length = p.endDay - p.startDay
        val last = order.size - 1
        var cumulativeYears = 0
        var start = p.startDay
        return List(order.size) { k ->
            val i = (first + k) % order.size
            cumulativeYears += years[i]
            val end = if (k == last) p.endDay else p.startDay + length * cumulativeYears / totalYears
            dashaPeriod(birth, p.lords + order[i], start, end).also { start = end }
        }
    }

    /**
     * The periods running at [t], one per level from 1 to [depth] (start inclusive, end exclusive, compared in
     * days from birth). Throws [IllegalArgumentException] when [t] lies outside [maha].
     */
    fun at(t: Instant, depth: Int = maxDepth): List<DashaPeriod<L>> {
        require(depth in 1..maxDepth) { "depth out of range" }
        val day = (t - birth).toDouble(DurationUnit.MILLISECONDS) / MS_PER_DAY
        val chain = ArrayList<DashaPeriod<L>>(depth)
        var level = maha
        while (chain.size < depth) {
            val p = level.firstOrNull { day >= it.startDay && day < it.endDay }
            requireNotNull(p) { "instant outside the dasha periods" }
            chain += p
            if (chain.size < depth) level = children(p)
        }
        return chain
    }

    override fun toString(): String = "DashaTree(redacted)"
}

/** Vimshottari and Yogini dashas from the Moon's sidereal longitude (docs/specs/M02.md section 3.4). */
object Dashas {
    /** Vimshottari lords in dasha order; years 7, 20, 6, 10, 7, 18, 16, 19, 17 (total 120). */
    val VIMSHOTTARI_ORDER: List<Graha> = listOf(
        Graha.KETU, Graha.VENUS, Graha.SUN, Graha.MOON, Graha.MARS,
        Graha.RAHU, Graha.JUPITER, Graha.SATURN, Graha.MERCURY,
    )

    private val VIMSHOTTARI_YEARS = listOf(7, 20, 6, 10, 7, 18, 16, 19, 17)

    private val VIMSHOTTARI =
        DashaScheme(VIMSHOTTARI_ORDER, VIMSHOTTARI_YEARS, startShift = 0, cycles = 1, maxDepth = 5)

    private val YOGINI =
        DashaScheme(Yogini.entries.toList(), Yogini.entries.map { it.years }, startShift = 3, cycles = 4, maxDepth = 3)

    /**
     * Vimshottari dasha: one 120-year cycle of 9 maha periods, 5 levels deep. The first lord is the lord of
     * the Moon's nakshatra and the elapsed fraction of the nakshatra has already passed at [birth].
     */
    fun vimshottari(
        moonSiderealDeg: Double,
        birth: Instant,
        year: DashaYear = DashaYear.JULIAN_365_25,
    ): DashaTree<Graha> = VIMSHOTTARI.tree(moonSiderealDeg, birth, year)

    /**
     * Yogini dasha: four 36-year cycles (32 maha periods), 3 levels deep; the first lord is
     * order[(nakshatra + 3) mod 8]. VERIFY with pandit (see [Yogini]).
     */
    fun yogini(
        moonSiderealDeg: Double,
        birth: Instant,
        year: DashaYear = DashaYear.JULIAN_365_25,
    ): DashaTree<Yogini> = YOGINI.tree(moonSiderealDeg, birth, year)
}

private const val MS_PER_DAY = 86_400_000.0
private const val NAKSHATRAS = 27
private const val FULL_CIRCLE_DEG = 360.0

/** birth + round(day * 86,400,000) ms, ties towards the future; day arithmetic ignores leap seconds. */
private fun <L> dashaPeriod(birth: Instant, lords: List<L>, startDay: Double, endDay: Double): DashaPeriod<L> =
    DashaPeriod(
        lords = lords,
        startDay = startDay,
        endDay = endDay,
        start = birth + (startDay * MS_PER_DAY).roundToLong().milliseconds,
        end = birth + (endDay * MS_PER_DAY).roundToLong().milliseconds,
    )

/**
 * A nakshatra dasha: lords in [order] with their [years]; the first lord is
 * order[(nakshatra + [startShift]) mod size] and [cycles] full cycles are laid out.
 */
private class DashaScheme<L>(
    private val order: List<L>,
    private val years: List<Int>,
    private val startShift: Int,
    private val cycles: Int,
    private val maxDepth: Int,
) {
    fun tree(moonSiderealDeg: Double, birth: Instant, year: DashaYear): DashaTree<L> {
        val lon = Zodiac.wrap360(moonSiderealDeg)
        require(!lon.isNaN()) { "longitude must be finite" }
        val x = lon * NAKSHATRAS / FULL_CIRCLE_DEG
        val n = min(NAKSHATRAS - 1, floor(x).toInt())
        val elapsed = x - n
        val first = (n + startShift) % order.size
        var start = 0.0 - elapsed * years[first] * year.days // "0.0 -": never -0.0 when nothing has elapsed
        val maha = List(order.size * cycles) { j ->
            val i = (first + j) % order.size
            val end = start + years[i] * year.days
            dashaPeriod(birth, listOf(order[i]), start, end).also { start = end }
        }
        return DashaTree(order, years, birth, maxDepth, maha)
    }
}
