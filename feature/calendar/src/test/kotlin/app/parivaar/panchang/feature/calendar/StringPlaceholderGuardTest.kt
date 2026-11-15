package app.parivaar.panchang.feature.calendar

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Numeric placeholders (`%d`, `%f`) crash at runtime when given a String. Every such string must be listed here with
 * its argument type; all other parameterised strings take `%s` (formatted text). Adding a numeric placeholder forces
 * a review of the call site.
 */
class StringPlaceholderGuardTest {
    private val numeric = mapOf(
        "cal_dots_more" to "Int", // DayCellView: more = festivalIds.size - 2
        "grahan_year" to "Int", // GrahanScreen: year
    )

    @Test fun `only the listed strings use numeric placeholders`() {
        val dir = File(System.getProperty("pp.root.dir"), "feature/calendar/src/main/res/values")
        val found = dir.listFiles { f -> f.name.startsWith("strings") && f.name.endsWith(".xml") }!!.flatMap { f ->
            Regex("""<string name="([a-z0-9_]+)"[^>]*>([^<]*)</string>""").findAll(f.readText())
                .filter { Regex("""%(\d+\$)?[dfx]""").containsMatchIn(it.groupValues[2]) }.map { it.groupValues[1] }
        }.toSet()
        assertEquals(numeric.keys, found)
    }
}
