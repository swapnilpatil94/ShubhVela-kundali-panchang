package app.parivaar.panchang.engine.calendar

import app.parivaar.panchang.engine.CalcSettings
import app.parivaar.panchang.engine.astro.Ephemeris
import app.parivaar.panchang.engine.astro.RiseSet
import app.parivaar.panchang.engine.astro.TimeTables
import app.parivaar.panchang.engine.calendar.internal.EraYears
import app.parivaar.panchang.engine.calendar.internal.MasaNaming
import app.parivaar.panchang.engine.jyotish.AyanamsaId
import app.parivaar.panchang.engine.jyotish.Gochar
import app.parivaar.panchang.engine.jyotish.Graha
import app.parivaar.panchang.engine.panchang.LimbKind
import app.parivaar.panchang.engine.panchang.LimbSolver
import app.parivaar.panchang.engine.panchang.LimbSpan
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Instant

/**
 * Lunations, sankrantis and masa labels (docs/specs/M04.md section 3.1). Global: nothing here depends on a
 * place.
 *
 * New and full moons are the starts of the TITHI spans 1 and 16 of [limbs] (bit-equal to M03 limb
 * boundaries). Sankrantis are M02 sidereal Sun ingresses searched from whole UTC days since the Unix epoch,
 * so every instant is bit-identical whatever range is asked. Holds bounded caches that never change a result:
 * not thread-safe.
 */
class LunarCalendar(eph: Ephemeris, time: TimeTables = TimeTables.DEFAULT) {
    /** The limb solver behind the lunations; use it for tithi and nakshatra spans of the same calendar. */
    val limbs: LimbSolver = LimbSolver(eph, time)

    internal val ephemeris: Ephemeris = eph
    internal val timeTables: TimeTables = time
    internal val riseSet: RiseSet = RiseSet(eph, time)

    private val gochar = Gochar(eph, time)

    private data class Key(val newMoon: Instant, val ayanamsa: AyanamsaId)

    private val memo = LinkedHashMap<Key, Lunation>()
    private val eraMemo = LinkedHashMap<Key, EraYears>()

    /** New moons in [from, to), in time order. Throws [IllegalArgumentException] if [from] > [to]. */
    fun newMoons(from: Instant, to: Instant): List<Instant> = starts(NEW_MOON_TITHI, from, to, CalcSettings())

    /** Full moons (start of tithi 16) in [from, to), in time order. Throws [IllegalArgumentException] if from > to. */
    fun fullMoons(from: Instant, to: Instant): List<Instant> = starts(FULL_MOON_TITHI, from, to, CalcSettings())

    /**
     * Sidereal (`s.ayanamsa`) sankrantis in [from, to), in time order. Throws [IllegalArgumentException] if
     * [from] > [to].
     */
    fun sankrantis(from: Instant, to: Instant, s: CalcSettings = CalcSettings()): List<Sankranti> {
        require(from <= to) { "from must not be after to" }
        val start = Instant.fromEpochSeconds(from.epochSeconds.floorDiv(SECONDS_PER_DAY) * SECONDS_PER_DAY)
        val endDay = to.epochSeconds.floorDiv(SECONDS_PER_DAY) + if (onDayStart(to)) 0 else 1
        val end = Instant.fromEpochSeconds(endDay * SECONDS_PER_DAY)
        return gochar.ingresses(Graha.SUN, start, end, s)
            .filter { !it.retrograde && from <= it.at && it.at < to }
            .map { Sankranti(it.into, it.at) }
    }

    /** The lunations whose new moon is in [from, to), in time order (they tile). */
    fun lunations(from: Instant, to: Instant, s: CalcSettings = CalcSettings()): List<Lunation> {
        require(from <= to) { "from must not be after to" }
        if (from == to) return emptyList()
        val spans = limbs.spans(LimbKind.TITHI, from, to + MAX_LUNATION, s).filter { it.start >= from }
        val newMoons = spans.filter { it.index == NEW_MOON_TITHI }.map { it.start }
        val sk = if (newMoons.size < 2) emptyList() else sankrantis(newMoons[0], newMoons.last() + SANKRANTI_REACH, s)
        return newMoons.zipWithNext().filter { it.first < to }.map { (nm, next) ->
            val full = spans.first { it.index == FULL_MOON_TITHI && it.start > nm }.start
            remember(Key(nm, s.ayanamsa)) { build(nm, full, next, sk) }
        }
    }

    /** The lunation [newMoon, nextNewMoon) containing [at]. */
    fun lunationAt(at: Instant, s: CalcSettings = CalcSettings()): Lunation {
        val spans = limbs.spans(LimbKind.TITHI, at - MAX_LUNATION, at + MAX_LUNATION, s)
        val newMoon = spans.last { it.index == NEW_MOON_TITHI && it.start <= at }.start
        return remember(Key(newMoon, s.ayanamsa)) {
            val next = spans.first { it.index == NEW_MOON_TITHI && it.start > newMoon }.start
            val full = spans.first { it.index == FULL_MOON_TITHI && it.start > newMoon }.start
            build(newMoon, full, next, sankrantis(newMoon, next + SANKRANTI_REACH, s))
        }
    }

    /**
     * The label of tithi [tithi] (1..30) of [l] in [system] (section 3.1): AMANTA = the lunation's own label;
     * PURNIMANTA = the same for 1..15, and for 16..30 the adhika label of an adhika [l], else the nija label of
     * the next non-adhika lunation.
     */
    fun label(l: Lunation, tithi: Int, system: MonthSystem, s: CalcSettings = CalcSettings()): LunarLabel {
        require(tithi in LimbKind.TITHI.firstIndex..LimbKind.TITHI.lastIndex) { "tithi out of range" }
        if (system == MonthSystem.AMANTA) return MasaNaming.amanta(l)
        return MasaNaming.purnimanta(l, tithi) {
            val next = lunationAt(l.nextNewMoon, s)
            if (next.adhika) lunationAt(next.nextNewMoon, s) else next
        }
    }

    /** Shaka and Vikram years of [l] from the latest nija Chaitra and Kartika lunations (section 3.2). */
    internal fun eraYears(l: Lunation, s: CalcSettings = CalcSettings()): EraYears {
        val key = Key(l.newMoon, s.ayanamsa)
        eraMemo[key]?.let { return it }
        var chaitra: Instant? = null
        var kartika: Instant? = null
        var cur = l
        var steps = 0
        while (chaitra == null || kartika == null) {
            if (chaitra == null && MasaNaming.isNija(cur, Masa.CHAITRA)) chaitra = cur.newMoon
            if (kartika == null && MasaNaming.isNija(cur, Masa.KARTIKA)) kartika = cur.newMoon
            check(++steps <= MAX_WALK) { "no year start found" }
            cur = lunationAt(cur.newMoon - 1.nanoseconds, s)
        }
        val years = MasaNaming.eraYears(chaitra, kartika)
        if (eraMemo.size >= MEMO_SIZE) eraMemo.remove(eraMemo.keys.first())
        eraMemo[key] = years
        return years
    }

    private fun starts(index: Int, from: Instant, to: Instant, s: CalcSettings): List<Instant> {
        require(from <= to) { "from must not be after to" }
        if (from == to) return emptyList()
        return limbs.spans(LimbKind.TITHI, from, to, s).filter { it.index == index && it.start >= from }
            .map(LimbSpan::start)
    }

    private fun build(newMoon: Instant, fullMoon: Instant, next: Instant, sk: List<Sankranti>): Lunation {
        val inside = sk.filter { newMoon <= it.at && it.at < next }
        val label = MasaNaming.name(inside, sk.firstOrNull { it.at >= newMoon })
        return Lunation(newMoon, fullMoon, next, inside, label.masa, label.adhika, label.kshayaMasa)
    }

    private fun remember(key: Key, compute: () -> Lunation): Lunation {
        memo[key]?.let { return it }
        val l = compute()
        if (memo.size >= MEMO_SIZE) memo.remove(memo.keys.first())
        memo[key] = l
        return l
    }

    private companion object {
        const val NEW_MOON_TITHI = 1
        const val FULL_MOON_TITHI = 16
        const val SECONDS_PER_DAY = 86_400L
        const val MEMO_SIZE = 64
        const val MAX_WALK = 16

        /** Longer than any synodic month (at most about 29.9 d). */
        val MAX_LUNATION = 31.days

        /** From a new moon, past the first sankranti after the next new moon (sankrantis at most 31.5 d apart). */
        val SANKRANTI_REACH = 33.days

        fun onDayStart(t: Instant): Boolean = t.nanosecondsOfSecond == 0 && t.epochSeconds.mod(SECONDS_PER_DAY) == 0L
    }
}
