package app.parivaar.panchang.engine.jyotish

import app.parivaar.panchang.engine.astro.Body
import app.parivaar.panchang.engine.astro.GoldenLoader
import app.parivaar.panchang.engine.astro.TimeScales
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** docs/specs/M02.md section 4, `GocharTest`. Synthetic charts and instants only. */
class GocharTest {
    private val eph = GoldenLoader.openDe440()
    private val gochar = Gochar(eph)

    private fun phase(moon: Rashi, saturnOffsetHouse: Int): SadeSatiPhase =
        gochar.sadeSati(moon, Rashi.entries[(moon.ordinal + saturnOffsetHouse - 1) % 12])

    @Test
    fun `sade sati phases by house from the natal Moon, two positive and two negative each`() {
        val expected = mapOf(
            12 to SadeSatiPhase.RISING, 1 to SadeSatiPhase.PEAK, 2 to SadeSatiPhase.SETTING,
            4 to SadeSatiPhase.DHAIYA_4TH, 8 to SadeSatiPhase.DHAIYA_8TH,
        )
        for ((house, p) in expected) {
            for (moon in listOf(Rashi.MESHA, Rashi.TULA)) { // two positives
                assertEquals(p, phase(moon, house), "$p positive for $moon")
            }
            for (other in (1..12).filter { it != house && it !in expected.keys }.take(2)) { // two negatives
                assertEquals(SadeSatiPhase.NONE, phase(Rashi.KANYA, other), "house $other is NONE")
            }
        }
        assertEquals(SadeSatiPhase.NONE, phase(Rashi.MEENA, 3))
        assertEquals(SadeSatiPhase.PEAK, gochar.sadeSati(Rashi.MEENA, Rashi.MEENA))
        assertEquals(SadeSatiPhase.RISING, gochar.sadeSati(Rashi.MESHA, Rashi.MEENA))
    }

    @Test
    fun `houseFrom symmetry`() {
        for (a in Rashi.entries) {
            for (b in Rashi.entries) {
                val sum = Zodiac.houseFrom(a, b) + Zodiac.houseFrom(b, a)
                assertEquals(if (a == b) 2 else 14, sum)
            }
        }
    }

    @Test
    fun `snapshot counts houses by sign from lagna and Moon`() {
        val natal = RuleTestSupport.chart(35.0, DoubleArray(9) { 100.0 })
        val transitLons = DoubleArray(9) { 30.0 * it + 5.0 }
        val transit = RuleTestSupport.chart(0.0, transitLons)
        val snap = gochar.snapshot(natal, transit)
        assertEquals(9, snap.size)
        snap.forEach {
            assertEquals(Zodiac.houseFrom(Rashi.VRISHABHA, it.rashi), it.houseFromLagna)
            assertEquals(Zodiac.houseFrom(Rashi.KARKA, it.rashi), it.houseFromMoon)
            assertEquals(Graha.entries[snap.indexOf(it)], it.graha)
        }
        assertTrue(snap.none { it.retrograde })
        assertTrue(snap[0].toString().none { c -> c.isDigit() })
    }

    private fun saturnRashi(t: Instant): Rashi {
        val tt = TimeScales.utcToTt(t)
        val lon = Ayanamsa.siderealDeg(eph.geoApparent(Body.SATURN, tt).lonDeg, AyanamsaId.LAHIRI, tt)
        return Zodiac.rashiOf(lon)
    }

    @Test
    fun `ten years of Saturn ingresses are real crossings and none is missed`() {
        val from = Instant.parse("2024-01-01T00:00:00Z")
        val to = Instant.parse("2034-01-01T00:00:00Z")
        val events = gochar.ingresses(Graha.SATURN, from, to)
        assertTrue(events.size == 8 && events.count { it.retrograde } == 2, "eight sign changes, two retrograde")
        for (e in events) {
            val before = saturnRashi(e.at - 1.seconds)
            val after = saturnRashi(e.at + 1.seconds)
            assertTrue(before != after, "rashi must differ across the event")
            assertEquals(after, e.into)
            assertEquals((after.ordinal - before.ordinal).mod(12) == 11, e.retrograde)
        }
        var t = from
        var prev = saturnRashi(t)
        while (t < to) {
            val next = t + 1.days
            val r = saturnRashi(next)
            if (r != prev) assertTrue(events.any { it.at > t && it.at <= next }, "daily change without an event")
            prev = r
            t = next
        }
        assertEquals(events.sortedBy { it.at }, events)
    }

    @Test
    fun `Moon ingresses use the finer step and run forward`() {
        val from = Instant.parse("2026-01-01T00:00:00Z")
        val events = gochar.ingresses(Graha.MOON, from, from + 30.days)
        assertTrue(events.size in 12..14)
        assertTrue(events.none { it.retrograde })
        events.zipWithNext().forEach { (a, b) -> assertEquals((a.into.ordinal + 1) % 12, b.into.ordinal) }
    }

    @Test
    fun `mean Rahu only moves backwards`() {
        val from = Instant.parse("2024-01-01T00:00:00Z")
        val events = gochar.ingresses(
            Graha.RAHU, from, from + 1500.days,
            app.parivaar.panchang.engine.CalcSettings(node = NodeType.MEAN),
        )
        assertTrue(events.isNotEmpty() && events.all { it.retrograde })
    }
}
