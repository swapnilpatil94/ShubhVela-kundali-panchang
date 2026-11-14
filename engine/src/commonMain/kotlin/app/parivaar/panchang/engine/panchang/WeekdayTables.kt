package app.parivaar.panchang.engine.panchang

import app.parivaar.panchang.engine.jyotish.Graha

/**
 * Classical weekday tables (docs/specs/M03.md sections 3.3 and 3.4). Ids and numbers only, no prose.
 * Rahu, Yamaganda and Gulika are the MASTER Part 6 tables; the day choghadiya cycle and Dishashool are VERIFY
 * until the pandit sign-off (OVERVIEW C14). All lookups are by [Vaar] (Sunday first).
 */
@Suppress("MagicNumber") // the tables are literal data
object WeekdayTables {
    private val RAHU = intArrayOf(8, 2, 7, 5, 6, 4, 3)
    private val YAMAGANDA = intArrayOf(5, 4, 3, 2, 1, 7, 6)
    private val GULIKA = intArrayOf(7, 6, 5, 4, 3, 2, 1)
    private val SHOOL = listOf(
        Shool(Direction.WEST, "JAGGERY"),
        Shool(Direction.EAST, "CURD"),
        Shool(Direction.NORTH, "MILK"),
        Shool(Direction.NORTH, "MILK"),
        Shool(Direction.SOUTH, "OIL"),
        Shool(Direction.WEST, "JAGGERY"),
        Shool(Direction.EAST, "CURD"),
    )

    /** Hora lords in the order the horas follow each other through the day. */
    val HORA_ORDER: List<Graha> = listOf(
        Graha.SUN, Graha.VENUS, Graha.MERCURY, Graha.MOON, Graha.SATURN, Graha.JUPITER, Graha.MARS,
    )

    /** The day part (1..8 of the sunrise-to-sunset day) that is Rahu kaal on [v]. */
    fun rahuPart(v: Vaar): Int = RAHU[v.ordinal]

    /** The day part (1..8) that is Yamaganda on [v]. */
    fun yamagandaPart(v: Vaar): Int = YAMAGANDA[v.ordinal]

    /** The day part (1..8) that is Gulika kaal on [v]. */
    fun gulikaPart(v: Vaar): Int = GULIKA[v.ordinal]

    /** The eight day choghadiyas of [v]: the cycle starts at the choghadiya whose lord is the lord of [v]. */
    fun dayChoghadiya(v: Vaar): List<Choghadiya> {
        val cycle = Choghadiya.entries
        val c0 = cycle.indexOfFirst { it.lord == v.lord }
        return List(PARTS) { cycle[(c0 + it) % cycle.size] }
    }

    /** Dishashool (direction and tradition id) of [v]. */
    fun shool(v: Vaar): Shool = SHOOL[v.ordinal]

    private const val PARTS = 8
}
