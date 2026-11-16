package app.parivaar.panchang.core.data.repo

import app.parivaar.panchang.core.data.DataError
import app.parivaar.panchang.core.data.DataResult
import app.parivaar.panchang.core.data.codec.FamilyFixture as F
import app.parivaar.panchang.core.data.store.StoredRow
import app.parivaar.panchang.core.data.store.Tbl
import app.parivaar.panchang.core.model.settings.AppSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Fable review item 3: a damaged settings row is surfaced and never silently replaced by defaults. */
class UnreadableSettingsTest {
    private val zeroId = ByteArray(16)

    @Test fun damagedRowIsFlaggedAndKeptUntilAnExplicitReset() = runBlocking {
        val k = RepoTestKit()
        assertFalse(k.settings.unreadable.first())
        assertEquals(DataResult.Ok(Unit), k.settings.update { F.settings })
        val row = k.store.tx { get(Tbl.SETTINGS, zeroId)!! }
        val bad = row.blob.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        k.store.tx { put(StoredRow(row.tbl, row.id, row.pos, row.ver, bad)) }

        assertEquals(AppSettings(), k.settings.settings.first(), "defaults in memory")
        assertTrue(k.settings.unreadable.first())
        assertEquals(DataResult.Err(DataError.UNREADABLE), k.settings.update { it.copy(onboardingDone = true) })
        assertContentEquals(bad, k.store.tx { get(Tbl.SETTINGS, zeroId)!! }.blob, "defaults never persisted over it")

        assertEquals(DataResult.Ok(Unit), k.settings.resetUnreadable())
        assertFalse(k.settings.unreadable.first())
        assertEquals(DataResult.Ok(Unit), k.settings.update { it.copy(privacyScreen = true) })
        assertTrue(k.settings.settings.first().privacyScreen)
    }

    @Test fun resetLeavesAReadableRowAlone() = runBlocking {
        val k = RepoTestKit()
        k.settings.update { F.settings }
        assertEquals(DataResult.Ok(Unit), k.settings.resetUnreadable())
        assertEquals(F.settings, k.settings.settings.first())
    }
}
