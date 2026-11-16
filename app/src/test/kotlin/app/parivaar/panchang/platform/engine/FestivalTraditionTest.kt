@file:Suppress("MaxLineLength")

package app.parivaar.panchang.platform.engine

import app.parivaar.panchang.core.model.settings.AppSettings
import app.parivaar.panchang.engine.calendar.FestivalRules
import app.parivaar.panchang.engine.calendar.Tradition
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Reminders and the Festival widget see only the user's Ekadashi tradition, like Aaj and the calendar. */
class FestivalTraditionTest {
    private val defs = FestivalRules.parse("recurring.json", File("../rules/festivals/recurring.json").readText()).defs

    private fun ekadashiIds(t: Tradition) =
        defsFor(defs, AppSettings(ekadashi = t)).map { it.id }.filter { it.startsWith("ekadashi") }

    @Test fun shippedRulesHoldBothTraditions() {
        assertTrue("ekadashi_smarta" in defs.map { it.id } && "ekadashi_vaishnava" in defs.map { it.id })
    }

    @Test fun smartaKeepsOnlySmarta() = assertEquals(listOf("ekadashi_smarta"), ekadashiIds(Tradition.SMARTA))

    @Test fun vaishnavaKeepsOnlyVaishnava() = assertEquals(
        listOf("ekadashi_vaishnava"), ekadashiIds(Tradition.VAISHNAVA),
    )

    @Test fun nonEkadashiDefinitionsStay() {
        val others = defs.filter { !it.id.startsWith("ekadashi") }.map { it.id }
        assertEquals(others, defsFor(defs, AppSettings()).map { it.id }.filter { !it.startsWith("ekadashi") })
    }
}
