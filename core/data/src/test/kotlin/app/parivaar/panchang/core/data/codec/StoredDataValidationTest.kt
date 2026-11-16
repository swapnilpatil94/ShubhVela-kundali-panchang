package app.parivaar.panchang.core.data.codec

import app.parivaar.panchang.core.data.DataError
import app.parivaar.panchang.core.data.DataResult
import app.parivaar.panchang.core.data.repo.RepoTestKit
import app.parivaar.panchang.core.data.crypto.RowEnvelope
import app.parivaar.panchang.core.data.repo.toBytes
import app.parivaar.panchang.core.data.store.StoredRow
import app.parivaar.panchang.core.data.store.Tbl
import app.parivaar.panchang.core.model.geo.isKnownZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import app.parivaar.panchang.core.data.codec.FamilyFixture as F
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Fable review item 2: stored or imported rows that would crash a consumer are rejected as bad rows. */
class StoredDataValidationTest {
    private fun JsonObject.with(key: String, v: JsonElement) = JsonObject(this + (key to v))

    private fun placeWith(key: String, v: JsonElement): JsonObject {
        val saved = PlaceCodec.toJson(F.savedPlace)
        val geo = (saved["place"] as JsonObject).with(key, v)
        return saved.with("place", geo)
    }

    @Test fun zoneCheckMatchesTheRuntime() {
        assertTrue(isKnownZoneId("Asia/Kolkata"))
        assertTrue(isKnownZoneId("Etc/GMT-5"))
        for (bad in listOf("X/Y", "", "Mars/Olympus", "Asia/" + "k".repeat(80))) assertFalse(isKnownZoneId(bad))
    }

    @Test fun unknownOrOversizedPlaceFieldsAreRejected() {
        val bad = listOf(
            "zone" to JsonPrimitive("X/Y"),
            "name" to JsonPrimitive("n".repeat(DataLimits.PLACE_NAME_MAX + 1)),
            "name" to JsonPrimitive("  "),
            "admin1" to JsonPrimitive("a".repeat(DataLimits.ADMIN1_MAX + 1)),
            "cc" to JsonPrimitive("zz"),
            "cc" to JsonPrimitive("ZZZ"),
            "pop" to JsonPrimitive(-1),
            "pop" to JsonPrimitive(DataLimits.POPULATION_MAX + 1),
            "elev" to JsonPrimitive(20_000.0),
            "overlay" to JsonPrimitive("o".repeat(DataLimits.OVERLAY_KEY_MAX + 1)),
        )
        for ((k, v) in bad) assertFailsWith<CodecException>(k) { PlaceCodec.fromJson(placeWith(k, v)) }
        assertEquals(F.savedPlace, PlaceCodec.fromJson(PlaceCodec.toJson(F.savedPlace)))
    }

    @Test fun profileOffsetAndBirthYearAreBounded() {
        val json = ProfileCodec.toJson(F.profileA)
        val snap = json["place"] as JsonObject
        val offset = (snap["offset"] as JsonObject).with("sec", JsonPrimitive(1_000_000))
        assertFailsWith<CodecException> { ProfileCodec.fromJson(json.with("place", snap.with("offset", offset))) }
        val birth = json["birth"] as JsonObject
        for (date in listOf("1700-01-01", "2500-01-01")) {
            assertFailsWith<CodecException>(date) {
                ProfileCodec.fromJson(json.with("birth", birth.with("date", JsonPrimitive(date))))
            }
        }
        val geo = (snap["place"] as JsonObject).with("zone", JsonPrimitive("X/Y"))
        assertFailsWith<CodecException> { ProfileCodec.fromJson(json.with("place", snap.with("place", geo))) }
    }

    @Test fun importRejectsADocumentWithAnUnknownZone() {
        val doc = F.doc.toJson()
        val places = kotlinx.serialization.json.JsonArray(listOf(placeWith("zone", JsonPrimitive("X/Y"))))
        assertFailsWith<CodecException> { FamilyDocument.fromJson(doc.with("places", places)) }
    }

    @Test fun repositoriesRefuseAndSkipBadRows() = runBlocking {
        val kit = RepoTestKit()
        val bad = F.savedPlace.copy(place = F.placeA.copy(zoneId = "X/Y"))
        assertEquals(DataResult.Err(DataError.VALIDATION), kit.places.upsert(bad))
        val old = F.profileA.copy(birth = F.profileA.birth.copy(date = LocalDate(1700, 1, 1)))
        assertEquals(DataResult.Err(DataError.VALIDATION), kit.profiles.upsert(old))
        // A row written by an older build (or tampered) with an unknown zone reads as unreadable, never crashes.
        val id = bad.id.toBytes()
        val plain = PayloadBytes.encode(PlaceCodec.toJson(bad))
        val blob = RowEnvelope.seal(kit.ring, Tbl.PLACE, id, PayloadMigrations.CURRENT_VERSION, plain, kit.rng)
        kit.store.tx { put(StoredRow(Tbl.PLACE, id, 0, PayloadMigrations.CURRENT_VERSION, blob)) }
        val r = kit.places.observeAll().first()
        assertEquals(0, r.items.size)
        assertEquals(1, r.unreadable)
    }
}
