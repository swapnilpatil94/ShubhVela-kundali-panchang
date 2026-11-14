package app.parivaar.panchang.core.geo

import app.parivaar.panchang.core.geo.internal.TzOverlay
import app.parivaar.panchang.core.model.geo.AmbiguityChoice
import app.parivaar.panchang.core.model.geo.CivilDayOffset
import app.parivaar.panchang.core.model.geo.TzContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.Tag
import java.io.File
import java.time.zone.ZoneRulesProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Exact (0 s) comparison of the resolver against `tz_offsets.json` (docs/specs/M05.md section 5). */
@Tag("golden")
class TzOffsetsGoldenTest {
    private val root: JsonObject =
        Json.parseToJsonElement(File(System.getProperty("pp.golden.dir"), "tz_offsets.json").readText()).jsonObject
    private val meta = root.getValue("meta").jsonObject
    private val zones = meta.getValue("zones").jsonArray.map { it.jsonPrimitive.content }
    private val overlay = TzOverlay.parse(File(System.getProperty("pp.rules.dir"), "tz_overlay.json").readText())
    private val resolver = JavaTimeOffsetResolver(overlay)

    private fun rows(name: String): List<JsonArray> = root.getValue(name).jsonArray.map { it.jsonArray }

    private fun zone(row: JsonArray): String = zones[row[0].jsonPrimitive.int]

    private fun instant(s: Long) = Instant.fromEpochSeconds(s)

    private fun report(vararg lines: String) {
        val jdk = ZoneRulesProvider.getVersions("UTC").lastKey()
        val fixture = meta.getValue("tzdataVersion").jsonPrimitive.content
        println("tz golden: fixture tzdata=$fixture jdk tzdb=$jdk ${lines.joinToString(" ")}")
    }

    @Test
    fun hostJdkTzdbMatchesFixtureExpectation() {
        val expected = meta.getValue("expectedJdkTzdb").jsonPrimitive.content
        val actual = ZoneRulesProvider.getVersions("UTC").lastKey()
        assertEquals(
            expected,
            actual,
            "host JDK tzdb $actual differs from the version $expected recorded in tz_offsets.json meta",
        )
    }

    @Test
    fun sampledOffsetsMatchExactly() {
        val rows = rows("sampled")
        assertEquals(meta.getValue("sampledCount").jsonPrimitive.int, rows.size)
        assertTrue(rows.isNotEmpty())
        var compared = 0
        val bad = mutableListOf<String>()
        for (r in rows) {
            val got = resolver.ianaOffsetAt(zone(r), instant(r[1].jsonPrimitive.long))
            if (got != r[2].jsonPrimitive.int) bad += "sampled ${r[0]} ${r[1]}"
            compared++
        }
        assertEquals(rows.size, compared)
        assertTrue(bad.isEmpty(), "mismatches ${bad.size}: ${bad.take(5)}")
        report("sampled=$compared")
    }

    @Test
    fun transitionsToTheSecond() {
        val rows = rows("transitions")
        assertEquals(meta.getValue("transitionCount").jsonPrimitive.int, rows.size)
        assertTrue(rows.isNotEmpty())
        var compared = 0
        val bad = mutableListOf<String>()
        for (r in rows) {
            val t = r[1].jsonPrimitive.long
            val before = resolver.ianaOffsetAt(zone(r), instant(t - 1))
            val after = resolver.ianaOffsetAt(zone(r), instant(t))
            if (before != r[2].jsonPrimitive.int || after != r[3].jsonPrimitive.int) bad += "transition ${r[0]} $t"
            compared += 2
        }
        assertEquals(meta.getValue("transitionRowCount").jsonPrimitive.int, compared)
        assertTrue(bad.isEmpty(), "mismatches ${bad.size}: ${bad.take(5)}")
        report("transitionRows=$compared")
    }

    @Test
    fun localGapOverlapAndMidnightCases() {
        val rows = rows("local")
        assertEquals(meta.getValue("localCount").jsonPrimitive.int, rows.size)
        assertTrue(rows.isNotEmpty())
        val bad = mutableListOf<String>()
        var compared = 0
        val kinds = mutableMapOf<String, Int>()
        for (r in rows) {
            val kind = r[1].jsonPrimitive.content
            require(kind in KINDS) { "unknown golden kind" }
            kinds.merge(kind, 1, Int::plus)
            val ctx = TzContext(zone(r), 0.0, null)
            val local = r[2].jsonPrimitive.long
            val sod = Math.floorMod(local, SECONDS_PER_DAY).toInt()
            val ldt = LocalDateTime(
                LocalDate.fromEpochDays(Math.floorDiv(local, SECONDS_PER_DAY).toInt()),
                LocalTime(sod / 3600, sod % 3600 / 60, sod % 60),
            )
            val f0 = r[3].jsonPrimitive.long
            val f1El = r[4]
            val e = resolver.localToInstant(ldt, ctx, AmbiguityChoice.EARLIER)
            if (e.instant.epochSeconds != f0) bad += "$kind ${r[0]} $local fold0"
            if (kind.endsWith("overlap")) {
                require(f1El !is JsonNull) { "overlap row without fold 1" }
                val f1 = f1El.jsonPrimitive.long
                val l = resolver.localToInstant(ldt, ctx, AmbiguityChoice.LATER)
                if (l.instant.epochSeconds != f1) bad += "$kind ${r[0]} $local fold1"
                if (e.alternative?.epochSeconds != f1) bad += "$kind ${r[0]} $local alt"
                compared++
            } else {
                assertTrue(f1El is JsonNull, "gap row with fold 1")
                assertNull(e.alternative)
            }
            if (kind.startsWith("midnight")) {
                if (resolver.civilDayOffset(ldt.date, ctx) !is CivilDayOffset.Valid) bad += "$kind ${r[0]} $local day"
                compared++
            }
            compared++
        }
        assertTrue(bad.isEmpty(), "mismatches ${bad.size}: ${bad.take(5)}")
        assertTrue(kinds.getValue("midnight_gap") > 0 && kinds.getValue("midnight_overlap") > 0)
        report("localRows=${rows.size} checks=$compared kinds=${kinds.toSortedMap()}")
    }

    private companion object {
        const val SECONDS_PER_DAY = 86_400L
        val KINDS = setOf("gap", "overlap", "midnight_gap", "midnight_overlap")
    }
}
