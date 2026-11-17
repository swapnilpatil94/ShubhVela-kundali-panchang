package app.parivaar.panchang.feature.bonds

import app.parivaar.panchang.core.model.profile.ProfileOrigin
import app.parivaar.panchang.core.model.profile.Relation
import app.parivaar.panchang.feature.bonds.BondsTestKit.Harness
import app.parivaar.panchang.feature.bonds.BondsTestKit.profile
import kotlinx.datetime.LocalDate
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShareMyCodeTest {
    @Test fun lagnaIsOffByDefaultAndOnlyOnRequest() {
        val h = Harness(BondsData(emptyList(), listOf(profile(1, "Riya Sharma", Relation.SELF))))
        h.vm.onEvent(BondsEvent.OpenShare)
        assertTrue(h.s.share!!.canShare)
        assertFalse(h.s.share!!.includeLagna)
        assertNull(h.engine.encodedWith!!.lagna)
        assertEquals("Riya Sharma", h.engine.encodedWith!!.name)
        h.vm.onEvent(BondsEvent.Lagna(true))
        assertEquals(4, h.engine.encodedWith!!.lagna)
    }

    @Test fun under18ForcesInitials() {
        val minor = profile(1, "Riya Sharma", Relation.SELF, birth = LocalDate(2010, 1, 1))
        val h = Harness(BondsData(emptyList(), listOf(minor)))
        h.vm.onEvent(BondsEvent.OpenShare)
        assertTrue(h.s.share!!.initialsForced)
        assertEquals("R.S.", h.engine.encodedWith!!.name)
    }

    @Test fun shareIsAbsentWithoutAnOwnSelfProfile() {
        val manualSelf = profile(1, "Someone", Relation.SELF, ProfileOrigin.MANUAL_OTHER)
        val h = Harness(BondsData(emptyList(), listOf(manualSelf, profile(2))))
        h.vm.onEvent(BondsEvent.OpenShare)
        assertEquals(ShareStatus.NO_SELF, h.s.share!!.status)
        assertFalse(h.s.share!!.canShare)
        assertNull(h.engine.encodedWith)
    }

    /** The share text template holds only the code placeholder: no name, date, time or place slot, no query URL. */
    @Test fun shareTextCarriesOnlyTheCode() {
        val xml = File(System.getProperty("pp.root.dir"), "feature/bonds/src/main/res/values/strings.xml").readText()
        val text = Regex("<string name=\"bonds_share_text\">(.*?)</string>").find(xml)!!.groupValues[1]
        assertEquals(listOf("%1\$s"), Regex("%\\d\\$[sd]").findAll(text).map { it.value }.toList())
        assertFalse("?" in text.replace("\\n", "") && "http" in text)
        assertFalse(Regex("(?i)birth|date|time|place|ref=").containsMatchIn(text))
    }

    @Test fun namesClipAndInitials() {
        assertEquals("Abcdefghijkl", BondNames.clip("  Abcdefghijklmnop "))
        assertEquals("R.S.K.", BondNames.initials("riya s kumar extra"))
        assertNull(BondNames.clip("   "))
        assertEquals("27.5", BondTexts.gunas(110))
        assertEquals("21", BondTexts.gunas(84))
    }
}
