@file:Suppress("MaxLineLength")

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
    /** Module-qualified ids ("module:name") with the argument type their call site passes. */
    private val numeric = mapOf(
        "feature/calendar:cal_dots_more" to "Int", // DayCellView: more = festivalIds.size - 2
        "feature/calendar:grahan_year" to "Int", // GrahanScreen: year
        "feature/calendar:cal_cell_tithi_s" to "Int", // DayCellView: tithi within the paksha, 1..15
        "feature/calendar:cal_cell_tithi_k" to "Int", // DayCellView: tithi within the paksha, 1..15
        "app:widget_in_days" to "Int", // RemoteViewsBinder: days until the festival
        "core/data:passphrase_too_short" to "Int", // minimum passphrase length
        "core/designsystem:explain_altitude_value" to "Double", // ExplainSheet: ProofInfo.altitudeM
        "feature/aaj:aaj_countdown_starts" to "Long", // countdownText: h, m, s
        "feature/aaj:aaj_countdown_ends" to "Long", // countdownText: h, m, s
        "feature/aaj:aaj_abhi_detail" to "Int", // AajContent: minutes left
        "feature/aaj:aaj_panchang_eyebrow" to "Int", // AajContent: Vikram samvat year
        "feature/onboarding:onb_step_progress" to "Int", // OnboardingScreens: step, total
        "feature/onboarding:onb_birth_time_value" to "Int", // OnboardingScreens: hour, minute
    )

    private val root = File(System.getProperty("pp.root.dir"))

    /** Every Android module with resources: app, core/<x>, feature/<x>. */
    private fun modules(): List<Pair<String, File>> =
        (listOf("app" to File(root, "app")) + listOf("core", "feature").flatMap { g ->
            File(root, g).listFiles { f -> f.isDirectory }.orEmpty().map { "$g/${it.name}" to it }
        }).filter { File(it.second, "src/main/res").isDirectory }

    // Any java.util.Formatter conversion that is not a string: flags, width and precision included (%2$02d, %1$.0f).
    private val numericSpec = Regex("""%(\d+\$)?[-#+ 0,(]*\d*(\.\d+)?[dfxXeEgGo]""")

    @Test fun `only the listed strings use numeric placeholders, in every module and locale`() {
        val found = modules().flatMap { (name, dir) ->
            File(dir, "src/main/res").listFiles { f -> f.isDirectory && f.name.startsWith("values") }.orEmpty()
                .flatMap { it.listFiles { f -> f.name.endsWith(".xml") }.orEmpty().toList() }
                .flatMap { f ->
                    Regex("""<(string|plurals) name="([a-z0-9_]+)"[^>]*>(.*?)</\1>""", RegexOption.DOT_MATCHES_ALL).findAll(f.readText())
                        .filter { numericSpec.containsMatchIn(it.groupValues[3]) }.map { "$name:${it.groupValues[2]}" }
                }
        }.toSet()
        assertEquals(numeric.keys, found)
    }

    @Test fun `the scan sees every resource module`() {
        val names = modules().map { it.first }
        for (m in listOf("app", "core/designsystem", "feature/aaj", "feature/calendar", "feature/settings", "feature/onboarding")) {
            kotlin.test.assertTrue(m in names, "$m missing from $names")
        }
    }
}
