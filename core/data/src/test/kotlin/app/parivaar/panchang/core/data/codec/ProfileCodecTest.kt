package app.parivaar.panchang.core.data.codec

import app.parivaar.panchang.core.data.store.Tbl
import app.parivaar.panchang.core.model.profile.FamilyPrefs
import app.parivaar.panchang.core.model.profile.PassingData
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import app.parivaar.panchang.core.data.codec.FamilyFixture as F

/** M10 payload v2: optional date of passing and family-date preferences. */
class ProfileCodecTest {
    private fun v1Json(): JsonObject =
        JsonObject(ProfileCodec.toJson(F.profileA).filterKeys { it != "passing" && it != "familyPrefs" })

    @Test
    fun v1_migrates_to_v2() {
        val bytes = PayloadBytes.encode(v1Json())
        val migrated = PayloadBytes.decode(bytes, Tbl.PROFILE, 1)
        val p = ProfileCodec.fromJson(migrated)
        assertEquals(F.profileA, p)
        assertEquals(null, p.passing)
        assertEquals(FamilyPrefs(), p.familyPrefs)
        // Other tables pass through unchanged.
        val o = JsonObject(mapOf("a" to JsonPrimitive(1)))
        assertEquals(o, PayloadMigrations.DEFAULT.migrate(Tbl.PLACE, 1, o))
        assertEquals(o, PayloadMigrations.DEFAULT.migrate(Tbl.SETTINGS, 1, o))
    }

    @Test
    fun v1_fixture_profiles_decode() {
        val root = System.getProperty("pp.root.dir")
        val text = File(root, "core/data/src/test/resources/vectors/family_v1.json").readText()
        val doc = FamilyDocument.fromBytes(text.toByteArray())
        assertEquals(listOf(null, null), doc.profiles.map { it.passing })
    }

    @Test
    fun roundTrip_passing_and_prefs() {
        val withTime = F.profileB.copy(
            passing = PassingData(LocalDate(2019, 3, 4), LocalTime(23, 59, 59)),
            familyPrefs = FamilyPrefs(tithi = false, nakshatra = true, shraddha = false),
        )
        assertEquals(withTime, ProfileCodec.fromJson(ProfileCodec.toJson(withTime)))
        val noTime = withTime.copy(passing = PassingData(LocalDate(2001, 1, 1), null))
        assertEquals(noTime, ProfileCodec.fromJson(ProfileCodec.toJson(noTime)))
        val bytes = PayloadBytes.encode(ProfileCodec.toJson(noTime))
        val row = PayloadBytes.decode(bytes, Tbl.PROFILE, PayloadMigrations.CURRENT_VERSION)
        assertEquals(noTime, ProfileCodec.fromJson(row))
    }

    @Test
    fun rejects_unknown_key() {
        val good = ProfileCodec.toJson(F.profileA)
        val extra = JsonObject(good + ("deathPlace" to JsonPrimitive("x")))
        assertFailsWith<CodecException> { ProfileCodec.fromJson(extra) }
        val badPrefs = JsonObject(good + ("familyPrefs" to JsonObject(mapOf("tithi" to JsonPrimitive(true)))))
        assertFailsWith<CodecException> { ProfileCodec.fromJson(badPrefs) }
        val passing = JsonObject(mapOf("date" to JsonPrimitive("1700-01-01"), "time" to JsonPrimitive("x")))
        val badPassing = JsonObject(good + ("passing" to passing))
        assertFailsWith<CodecException> { ProfileCodec.fromJson(badPassing) }
        // A v1 row that already carries a v2 key is malformed.
        assertFailsWith<CodecException> { PayloadBytes.decode(PayloadBytes.encode(good), Tbl.PROFILE, 1) }
    }

    @Test
    fun toString_is_redacted() {
        val p = PassingData(LocalDate(2019, 3, 4), LocalTime(1, 2, 3))
        assertFalse(p.toString().contains("2019"))
    }
}
