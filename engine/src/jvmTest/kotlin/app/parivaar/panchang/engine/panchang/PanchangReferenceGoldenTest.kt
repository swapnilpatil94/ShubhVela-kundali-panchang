package app.parivaar.panchang.engine.panchang

import app.parivaar.panchang.engine.Place
import app.parivaar.panchang.engine.panchang.M03Golden.a
import app.parivaar.panchang.engine.panchang.M03Golden.d
import app.parivaar.panchang.engine.panchang.M03Golden.i
import app.parivaar.panchang.engine.panchang.M03Golden.s
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonArray
import org.junit.jupiter.api.Tag
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * G-M03 reference values (docs/specs/M03.md section 4): `panchang_reference.json` with status TODO_OWNER must
 * hold exactly 0 entries; once the owner fills it (status not TODO_OWNER) it must hold at least 20 entries and
 * every limb end time must be within 60 s of the engine on both backends.
 */
@Tag("golden")
class PanchangReferenceGoldenTest {
    private fun check(limits: M03Golden.Limits): List<String> {
        val root = M03Golden.fixture("panchang_reference.json")
        val status = root.getValue("status").s()
        val entries = root.getValue("entries").a()
        assertEquals("UPPER_LIMB_REFRACTED", root.getValue("convention").s(), "reference convention")
        val lines = mutableListOf("status=$status entries=${entries.size}")
        if (status == "TODO_OWNER") {
            assertEquals(0, entries.size, "TODO_OWNER reference file must have no entries")
            lines += "no owner-supplied reference values yet (TODO(owner)); nothing compared"
            M03Golden.report("M03 reference ${limits.name}", lines)
            return emptyList()
        }
        assertTrue(entries.size >= MIN_ENTRIES, "reference entries ${entries.size} < $MIN_ENTRIES")
        val calc = PanchangCalculator(limits.eph(), M03Golden.tables)
        val failures = mutableListOf<String>()
        var worst = 0.0
        for ((n, e) in entries.withIndex()) {
            val x: JsonArray = e.a()
            require(x.size == FIELDS) { "reference entry $n has ${x.size} fields" }
            val kind = LimbKind.entries.firstOrNull { it.name == x[4].s() } ?: error("unknown limb kind in entry $n")
            val day = calc.day(LocalDate.parse(x[3].s()), x[2].i() * SECONDS_PER_MINUTE, Place(x[0].d(), x[1].d(), 0.0))
            val span = day.limbs.getValue(kind).firstOrNull { it.index == x[5].i() }
            if (span == null) {
                failures += "entry $n: no $kind span with the reference index on that day"
                continue
            }
            val diff = timeOfDayDiffSeconds(span.end.epochSeconds + x[2].i() * SECONDS_PER_MINUTE, x[6].s())
            worst = maxOf(worst, diff)
            if (!(diff <= LIMIT_S)) failures += "entry $n $kind end differs by $diff s > $LIMIT_S s"
        }
        lines += "max |end - reference| = $worst s; limit $LIMIT_S s"
        M03Golden.report("M03 reference ${limits.name}", lines)
        return failures
    }

    /** |local time of day of the engine end - reference "HH:mm" or "HHmm"|, wrapped around midnight, seconds. */
    private fun timeOfDayDiffSeconds(localEpochSeconds: Long, hhmm: String): Double {
        val digits = hhmm.replace(":", "")
        require(digits.length == HHMM_DIGITS && digits.all { it.isDigit() }) { "bad end time format" }
        val ref = (digits.substring(0, 2).toInt() * MINUTES_PER_HOUR + digits.substring(2).toInt()) * SECONDS_PER_MINUTE
        val d = abs(localEpochSeconds.mod(SECONDS_PER_DAY) - ref)
        return minOf(d, SECONDS_PER_DAY - d).toDouble()
    }

    @Test
    fun precisionBackendMatchesReference() =
        M03Golden.assertNoFailures("M03 reference PRECISION", check(M03Golden.PRECISION))

    @Test
    fun baseBackendMatchesReference() = M03Golden.assertNoFailures("M03 reference BASE", check(M03Golden.BASE))

    private companion object {
        const val MIN_ENTRIES = 20
        const val FIELDS = 8
        const val LIMIT_S = 60.0
        const val SECONDS_PER_MINUTE = 60
        const val MINUTES_PER_HOUR = 60
        const val SECONDS_PER_DAY = 86_400L
        const val HHMM_DIGITS = 4
    }
}
