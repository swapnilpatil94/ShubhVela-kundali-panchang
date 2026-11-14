package app.parivaar.panchang.engine.panchang

import app.parivaar.panchang.engine.jyotish.Graha
import app.parivaar.panchang.engine.panchang.Choghadiya.AMRIT
import app.parivaar.panchang.engine.panchang.Choghadiya.CHAR
import app.parivaar.panchang.engine.panchang.Choghadiya.KAAL
import app.parivaar.panchang.engine.panchang.Choghadiya.LABH
import app.parivaar.panchang.engine.panchang.Choghadiya.ROG
import app.parivaar.panchang.engine.panchang.Choghadiya.SHUBH
import app.parivaar.panchang.engine.panchang.Choghadiya.UDVEG
import kotlin.test.Test
import kotlin.test.assertEquals

/** docs/specs/M03.md section 4, `WeekdayTablesTest`: the MASTER Part 6 tables, literal. */
class WeekdayTablesTest {
    private fun parts(f: (Vaar) -> Int): List<Int> = Vaar.entries.map(f)

    @Test
    fun rahuYamagandaGulikaPartsAreTheMasterTables() {
        assertEquals(listOf(8, 2, 7, 5, 6, 4, 3), parts(WeekdayTables::rahuPart))
        assertEquals(listOf(5, 4, 3, 2, 1, 7, 6), parts(WeekdayTables::yamagandaPart))
        assertEquals(listOf(7, 6, 5, 4, 3, 2, 1), parts(WeekdayTables::gulikaPart))
    }

    @Test
    fun horaOrderIsTheClassicalSequence() {
        assertEquals(
            listOf(Graha.SUN, Graha.VENUS, Graha.MERCURY, Graha.MOON, Graha.SATURN, Graha.JUPITER, Graha.MARS),
            WeekdayTables.HORA_ORDER,
        )
    }

    @Test
    fun dayChoghadiyaStartsAtTheWeekdayLordAndCyclesThroughSeven() {
        assertEquals(
            listOf(UDVEG, CHAR, LABH, AMRIT, KAAL, SHUBH, ROG, UDVEG),
            WeekdayTables.dayChoghadiya(Vaar.SUNDAY),
        )
        assertEquals(
            listOf(AMRIT, KAAL, SHUBH, ROG, UDVEG, CHAR, LABH, AMRIT),
            WeekdayTables.dayChoghadiya(Vaar.MONDAY),
        )
        Vaar.entries.forEach { v ->
            val day = WeekdayTables.dayChoghadiya(v)
            assertEquals(v.lord, day.first().lord)
            assertEquals(8, day.size)
            assertEquals(day.first(), day.last())
        }
    }

    @Test
    fun shoolFollowsTheMasterTable() {
        val expected = listOf(
            Direction.WEST to "JAGGERY", Direction.EAST to "CURD", Direction.NORTH to "MILK",
            Direction.NORTH to "MILK", Direction.SOUTH to "OIL", Direction.WEST to "JAGGERY",
            Direction.EAST to "CURD",
        )
        assertEquals(expected, Vaar.entries.map { WeekdayTables.shool(it).let { s -> s.direction to s.remedyId } })
    }
}
