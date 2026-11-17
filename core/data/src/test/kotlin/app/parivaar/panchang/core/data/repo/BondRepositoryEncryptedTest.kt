package app.parivaar.panchang.core.data.repo

import app.parivaar.panchang.core.data.codec.FamilyFixture as F
import app.parivaar.panchang.core.model.profile.RecordId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** M12: bond rows are sealed; no trait key, name or payload text appears in the stored bytes. */
class BondRepositoryEncryptedTest {
    private val bond = F.bond.copy(
        traits = F.bond.traits.copy(name = "Zoyaplain", vashya = 2),
        profileRef = RecordId("0000000000000000000000000000000b"),
    )

    @Test
    fun noTraitByteAppearsInPlaintext() = runBlocking {
        val k = RepoTestKit()
        k.bonds.upsert(bond)
        assertEquals(listOf(bond), k.bonds.observeAll().first().items)
        val rows = k.store.dump()
        assertEquals(1, rows.size)
        val stored = rows.flatMap { it.id.toList() + it.blob.toList() }.toByteArray()
        val needles = listOf("Zoyaplain", "\"mn\"", "\"vs\"", "\"mars\"", "traits", "profileRef", "0000000b")
        for (n in needles) {
            assertFalse(contains(stored, n.encodeToByteArray()), "plaintext found in a stored bond row")
        }
    }

    private fun contains(hay: ByteArray, needle: ByteArray): Boolean =
        (0..hay.size - needle.size).any { i -> needle.indices.all { hay[i + it] == needle[it] } }
}
