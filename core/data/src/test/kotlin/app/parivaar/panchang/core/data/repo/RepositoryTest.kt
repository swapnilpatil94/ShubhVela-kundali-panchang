package app.parivaar.panchang.core.data.repo

import app.parivaar.panchang.core.data.DataError
import app.parivaar.panchang.core.data.DataResult
import app.parivaar.panchang.core.data.VaultState
import app.parivaar.panchang.core.data.codec.FamilyFixture as F
import app.parivaar.panchang.core.model.profile.Relation
import app.parivaar.panchang.core.model.settings.AppSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RepositoryTest {
    private fun err(e: DataError) = DataResult.Err(e)

    private val ok = DataResult.Ok(Unit)

    @Test
    fun profileRoundTripAndNoPlaintextInRows() = runBlocking {
        val k = RepoTestKit()
        assertEquals(ok, k.profiles.upsert(F.profileA))
        assertEquals(ok, k.profiles.upsert(F.profileB))
        assertEquals(DataResult.Ok(F.profileA), k.profiles.get(F.profileA.id))
        val all = k.profiles.observeAll().first()
        assertEquals(listOf(F.profileA, F.profileB), all.items)
        assertEquals(0, all.unreadable)
        for (row in k.store.dump()) {
            val text = String(row.blob, Charsets.ISO_8859_1)
            assertFalse(text.contains(F.NAME_A) || text.contains("1990") || text.contains("Testville"))
        }
        assertEquals(err(DataError.NOT_FOUND), k.profiles.get(F.id(99)))
    }

    @Test
    fun lockedUntilReady() = runBlocking {
        val k = RepoTestKit(ready = false)
        assertEquals(err(DataError.LOCKED), k.profiles.upsert(F.profileA))
        assertEquals(err(DataError.LOCKED), k.profiles.get(F.profileA.id))
        assertEquals(err(DataError.LOCKED), k.profiles.delete(F.profileA.id))
        assertEquals(err(DataError.LOCKED), k.profiles.reorder(emptyList()))
        assertEquals(err(DataError.LOCKED), k.places.upsert(F.savedPlace))
        assertEquals(err(DataError.LOCKED), k.bonds.upsert(F.bond))
        assertEquals(err(DataError.LOCKED), k.settings.update { it })
        assertEquals(0, k.store.dump().size)
        assertTrue(k.profiles.observeAll().first().items.isEmpty())
        k.state.value = VaultState.KeyLost
        assertEquals(err(DataError.LOCKED), k.profiles.upsert(F.profileA))
        k.state.value = VaultState.Ready
        assertEquals(ok, k.profiles.upsert(F.profileA))
    }

    @Test
    fun profileValidation() = runBlocking {
        val k = RepoTestKit()
        assertEquals(err(DataError.VALIDATION), k.profiles.upsert(F.profileA.copy(displayName = "")))
        assertEquals(err(DataError.VALIDATION), k.profiles.upsert(F.profileA.copy(displayName = "x".repeat(61))))
        assertEquals(err(DataError.VALIDATION), k.profiles.upsert(F.profileA.copy(notes = "x".repeat(2001))))
        assertEquals(ok, k.profiles.upsert(F.profileA.copy(displayName = "x".repeat(60), notes = "x".repeat(2000))))
    }

    @Test
    fun onlyOneSelfProfile() = runBlocking {
        val k = RepoTestKit()
        assertEquals(ok, k.profiles.upsert(F.profileA))
        assertEquals(ok, k.profiles.upsert(F.profileA.copy(notes = "edited")))
        assertEquals(err(DataError.SELF_EXISTS), k.profiles.upsert(F.profileB.copy(relation = Relation.SELF)))
        assertEquals(ok, k.profiles.upsert(F.profileB))
        assertEquals(err(DataError.SELF_EXISTS), k.profiles.upsert(F.profileB.copy(relation = Relation.SELF)))
        assertEquals(ok, k.profiles.delete(F.profileA.id))
        assertEquals(ok, k.profiles.upsert(F.profileB.copy(relation = Relation.SELF)))
    }

    @Test
    fun profileLimit() = runBlocking {
        val k = RepoTestKit()
        repeat(500) { assertEquals(ok, k.profiles.upsert(F.profileB.copy(id = F.id(1000 + it)))) }
        assertEquals(err(DataError.LIMIT), k.profiles.upsert(F.profileB.copy(id = F.id(5000))))
        assertEquals(ok, k.profiles.upsert(F.profileB.copy(id = F.id(1000), notes = "edit")))
        assertEquals(ok, k.profiles.delete(F.id(1000)))
        assertEquals(ok, k.profiles.upsert(F.profileB.copy(id = F.id(5000))))
    }

    @Test
    fun placeAndBondLimitsAndDelete() = runBlocking {
        val k = RepoTestKit()
        repeat(2000) {
            assertEquals(ok, k.places.upsert(F.savedPlace.copy(id = F.id(10_000 + it))))
            assertEquals(ok, k.bonds.upsert(F.bond.copy(id = F.id(20_000 + it))))
        }
        assertEquals(err(DataError.LIMIT), k.places.upsert(F.savedPlace.copy(id = F.id(9))))
        assertEquals(err(DataError.LIMIT), k.bonds.upsert(F.bond.copy(id = F.id(9))))
        assertEquals(ok, k.places.upsert(F.savedPlace.copy(id = F.id(10_000), pinned = false)))
        assertEquals(ok, k.places.delete(F.id(10_000)))
        assertEquals(err(DataError.NOT_FOUND), k.places.delete(F.id(10_000)))
        assertEquals(ok, k.bonds.delete(F.id(20_000)))
        assertEquals(err(DataError.NOT_FOUND), k.bonds.delete(F.id(20_000)))
        assertEquals(1999, k.places.observeAll().first().items.size)
    }

    @Test
    fun reorder() = runBlocking {
        val k = RepoTestKit()
        val ids = (1..4).map { F.id(100 + it) }
        ids.forEach { k.profiles.upsert(F.profileB.copy(id = it)) }
        assertEquals(ids, k.profiles.observeAll().first().items.map { it.id })
        assertEquals(ok, k.profiles.reorder(listOf(ids[2], ids[0])))
        assertEquals(listOf(ids[2], ids[0], ids[1], ids[3]), k.profiles.observeAll().first().items.map { it.id })
        assertEquals(err(DataError.NOT_FOUND), k.profiles.reorder(listOf(F.id(7))))
        assertEquals(err(DataError.VALIDATION), k.profiles.reorder(listOf(ids[0], ids[0])))
        assertEquals(DataResult.Ok(F.profileB.copy(id = ids[2])), k.profiles.get(ids[2]))
    }

    @Test
    fun settingsDefaultsUpdateAndValidation() = runBlocking {
        val k = RepoTestKit()
        assertEquals(AppSettings(), k.settings.settings.first())
        assertEquals(ok, k.settings.update { F.settings })
        assertEquals(F.settings, k.settings.settings.first())
        assertEquals(ok, k.settings.update { it.copy(privacyScreen = false) })
        assertEquals(F.settings.copy(privacyScreen = false), k.settings.settings.first())
        assertEquals(err(DataError.VALIDATION), k.settings.update { it.copy(localeTag = "") })
        assertEquals(err(DataError.VALIDATION), k.settings.update { it.copy(localeTag = "x".repeat(36)) })
        assertEquals(err(DataError.VALIDATION), k.settings.update { throw IllegalArgumentException("x") })
        assertEquals(F.settings.copy(privacyScreen = false), k.settings.settings.first())
        assertEquals(1, k.store.dump().size)
    }

    @Test
    fun placesAndBondsRoundTrip() = runBlocking {
        val k = RepoTestKit()
        k.places.upsert(F.savedPlace)
        k.bonds.upsert(F.bond)
        assertEquals(listOf(F.savedPlace), k.places.observeAll().first().items)
        assertEquals(listOf(F.bond), k.bonds.observeAll().first().items)
    }
}
