package app.parivaar.panchang.feature.bonds

import app.parivaar.panchang.core.model.profile.ProfileOrigin
import app.parivaar.panchang.core.model.profile.Relation
import app.parivaar.panchang.feature.bonds.BondsTestKit.Harness
import app.parivaar.panchang.feature.bonds.BondsTestKit.bond
import app.parivaar.panchang.feature.bonds.BondsTestKit.profile
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BondsViewModelTest {
    private val self = profile(1, "Riya Sharma", Relation.SELF)
    private val family = profile(2, "Synthetic Mother", Relation.PARENT, ProfileOrigin.MANUAL_OTHER)

    @Test fun listShowsBondsAndFamilyChoicesWithoutSelf() {
        val h = Harness(BondsData(listOf(bond(10)), listOf(self, family)))
        assertFalse(h.s.locked)
        assertEquals(listOf("B10"), h.s.bonds.map { it.name })
        assertEquals(listOf(family.id), h.s.profiles.map { it.id })
        assertTrue(h.s.hasSelf)
    }

    @Test fun vaultLockClearsEverythingWithAnExplicitEmptyState() {
        val h = Harness(BondsData(listOf(bond(10)), listOf(self, family)))
        h.vm.onEvent(BondsEvent.Open(BondsTestKit.id(10)))
        assertNotNull(h.s.result)
        h.flow.value = null
        assertEquals(BondsState(locked = true), h.s)
        h.vm.onEvent(BondsEvent.Open(BondsTestKit.id(10)))
        assertEquals(BondsState(locked = true), h.s, "events while locked change nothing")
    }

    @Test fun pastedCodePreviewsThenSavesOnlyOnConfirm() {
        val h = Harness(BondsData(emptyList(), listOf(self)))
        h.vm.onEvent(BondsEvent.OpenAdd)
        h.vm.onEvent(BondsEvent.Code("GOODCODE"))
        h.vm.onEvent(BondsEvent.Kind(BondKind.LOVE))
        h.vm.onEvent(BondsEvent.PreviewCode)
        assertEquals(BondsPage.PREVIEW, h.s.page)
        assertEquals(0, h.store.saves.get(), "a preview never saves")
        h.vm.onEvent(BondsEvent.ConfirmAdd)
        val saved = h.store.saved.single()
        assertEquals("love", saved.kindKey)
        assertNull(saved.profileRef)
        assertEquals(1, saved.payloadVersion)
        h.flow.value = BondsData(h.store.saved.toList(), listOf(self))
        assertEquals(BondsPage.RESULT, h.s.page)
        assertEquals(ResultStatus.SHOWN, h.s.result?.status)
        assertTrue(h.s.result!!.cardShareAllowed)
    }

    /** DeepLinkRejectTest: malformed code, wrong host, query form or oversize input -> error, nothing saved. */
    @Test fun rejectedInputShowsTheErrorAndSavesNothing() {
        val bad = listOf("!!bad!!", "https://evil.example/b#GOODCODE", "https://shubhvela.app/b?code=GOODCODE",
            "A".repeat(500))
        for (input in bad) {
            val h = Harness(BondsData(emptyList(), listOf(self)))
            h.vm.onEvent(BondsEvent.OpenAdd)
            h.vm.onEvent(BondsEvent.Code(input))
            h.vm.onEvent(BondsEvent.PreviewCode)
            h.vm.onEvent(BondsEvent.ConfirmAdd)
            assertEquals(AddError.INVALID_CODE, h.s.addError)
            assertEquals(BondsPage.ADD, h.s.page)
            assertEquals(0, h.store.saves.get())
        }
    }

    @Test fun familyBondKeepsProfileRefAndDisablesCardShare() {
        val h = Harness(BondsData(emptyList(), listOf(self, family)))
        h.vm.onEvent(BondsEvent.OpenAdd)
        h.vm.onEvent(BondsEvent.FromProfile(family.id))
        h.vm.onEvent(BondsEvent.ConfirmAdd)
        val saved = h.store.saved.single()
        assertEquals(family.id, saved.profileRef)
        assertFalse(h.s.result!!.cardShareAllowed)
    }

    @Test fun resultMapsManglikPairAndUnavailableEngine() {
        val h = Harness(BondsData(listOf(bond(10)), listOf(self)))
        h.vm.onEvent(BondsEvent.Open(BondsTestKit.id(10)))
        assertEquals(ManglikNote.BALANCED, h.s.result?.manglikKey)
        h.engine.available = false
        h.vm.onEvent(BondsEvent.Open(BondsTestKit.id(10)))
        assertEquals(ResultStatus.UNAVAILABLE, h.s.result?.status)
        assertEquals(ManglikNote.CONSULT, ManglikNote.pair(0, 2))
        assertEquals(ManglikNote.BALANCED, ManglikNote.pair(1, 2))
    }

    @Test fun noSelfProfileShowsTheCalmNoSelfState() {
        val h = Harness(BondsData(listOf(bond(10)), listOf(family)))
        h.vm.onEvent(BondsEvent.Open(BondsTestKit.id(10)))
        assertEquals(ResultStatus.NO_SELF, h.s.result?.status)
    }

    @Test fun deleteRemovesTheBond() {
        val h = Harness(BondsData(listOf(bond(10)), listOf(self)))
        h.store.saved += bond(10)
        h.vm.onEvent(BondsEvent.Open(BondsTestKit.id(10)))
        h.vm.onEvent(BondsEvent.Delete(BondsTestKit.id(10)))
        assertTrue(h.store.saved.isEmpty())
        assertEquals(BondsPage.LIST, h.s.page)
    }
}
