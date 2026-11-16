package app.parivaar.panchang.feature.kundli

import app.parivaar.panchang.core.designsystem.chart.ChartGeometry
import app.parivaar.panchang.core.designsystem.chart.ChartStyle
import app.parivaar.panchang.core.model.profile.RecordId
import app.parivaar.panchang.engine.jyotish.DashaPeriod
import app.parivaar.panchang.engine.jyotish.Graha
import app.parivaar.panchang.engine.jyotish.Rashi
import app.parivaar.panchang.engine.jyotish.VargaChart
import app.parivaar.panchang.engine.jyotish.VargaPos
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class ChartModelsTest {
    private val d9 = VargaChart(VargaPos(Rashi.MEENA, 1), Graha.entries.map { VargaPos(Rashi.entries[it.ordinal], 1) })

    @Test fun d9CellsFollowLagna() {
        val m = ChartModels.d9(d9, noon = false)
        assertTrue(m.lagnaKnown)
        assertEquals(Rashi.MEENA.ordinal, m.cells[0].rashi)
        assertTrue(m.cells[0].lagna)
        // Sun is in Mesha = house 2 from a Meena lagna.
        assertEquals(listOf(0), m.cellOfHouse(2).grahas.map { it.code })
    }

    @Test fun noonChartHidesLagnaAndDisablesNorth() {
        val m = ChartModels.d9(d9, noon = true)
        assertFalse(m.lagnaKnown)
        assertTrue(m.cells.none { it.lagna })
        assertEquals(ChartStyle.SOUTH, ChartGeometry.effective(ChartStyle.NORTH, m.lagnaKnown))
        assertFalse(ChartStyle.NORTH in ChartGeometry.available(m.lagnaKnown))
    }

    @Test fun degreesTruncateNeverRoundUp() {
        assertEquals("29°59′", ChartModels.dms(29.99999))
        assertEquals("0°00′", ChartModels.dms(0.0))
        assertEquals("12°", ChartModels.degText(12.9999))
    }
}

class DashaRowsTest {
    private val birth = Instant.parse("2000-01-01T00:00:00Z")

    private fun p(lords: List<Graha>, s: Int, e: Int) =
        DashaPeriod(lords, s.toDouble(), e.toDouble(), birth + s.days, birth + e.days)

    @Test fun currentChainHighlightedAndBoundaryBelongsToLaterPeriod() {
        val maha = listOf(p(listOf(Graha.KETU), 0, 100), p(listOf(Graha.VENUS), 100, 200))
        val venus = listOf(Graha.VENUS)
        val kids = mapOf(venus to listOf(p(venus + Graha.VENUS, 100, 150), p(venus + Graha.SUN, 150, 200)))
        val now = birth + 150.days // exactly on the boundary
        val rows = DashaRows.build(maha, kids, setOf(listOf(Graha.VENUS)), now, TimeZone.UTC)
        assertEquals(4, rows.size)
        assertEquals(listOf(false, true, false, true), rows.map { it.current })
        assertEquals(listOf(1, 1, 2, 2), rows.map { it.level })
    }

    @Test fun autoExpandStopsAboveSookshma() {
        val chain = (1..4).map { n -> p(List(n) { Graha.MOON }, 0, 10) }
        assertEquals(3, DashaRows.autoExpand(chain).size)
        assertTrue(DashaRows.build(chain.take(1), emptyMap(), emptySet(), birth, TimeZone.UTC).single().expandable)
    }
}

class KundliCacheTest {
    private fun key(n: Int) = BundleKey(RecordId(n.toString(16).padStart(32, '0')), 0, 0, false, null)

    @Test fun lruOfEightAndEvictByProfile() {
        val c = KundliCache<String>()
        val b = "bundle"
        for (i in 1..9) c.put(key(i), b)
        assertEquals(8, c.size())
        assertEquals(null, c.get(key(1)))
        c.evict(key(2).profileId)
        assertEquals(null, c.get(key(2)))
        c.clear()
        assertEquals(0, c.size())
        // A new local day is a new key (current dasha, Sade Sati and gochar refresh after midnight).
        val k = key(3)
        c.put(k.copy(day = kotlinx.datetime.LocalDate(2026, 11, 16)), b)
        assertEquals(null, c.get(k.copy(day = kotlinx.datetime.LocalDate(2026, 11, 17))))
    }
}
