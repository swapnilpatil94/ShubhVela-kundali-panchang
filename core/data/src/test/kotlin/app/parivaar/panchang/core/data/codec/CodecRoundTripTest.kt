package app.parivaar.panchang.core.data.codec

import app.parivaar.panchang.core.data.codec.FamilyFixture as F
import app.parivaar.panchang.core.model.profile.Relation
import app.parivaar.panchang.core.model.settings.AppSettings
import app.parivaar.panchang.engine.CalcSettings
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CodecRoundTripTest {
    private val fixture =
        File(System.getProperty("pp.root.dir"), "core/data/src/test/resources/vectors/family_v1.json")

    @Test
    fun everyTypeRoundTrips() {
        assertEquals(F.profileA, ProfileCodec.fromJson(ProfileCodec.toJson(F.profileA)))
        assertEquals(F.profileB, ProfileCodec.fromJson(ProfileCodec.toJson(F.profileB)))
        assertEquals(F.savedPlace, PlaceCodec.fromJson(PlaceCodec.toJson(F.savedPlace)))
        assertEquals(F.bond, BondCodec.fromJson(BondCodec.toJson(F.bond)))
        assertEquals(F.settings, SettingsCodec.fromJson(SettingsCodec.toJson(F.settings)))
        assertEquals(F.doc, FamilyDocument.fromBytes(F.doc.toBytes()))
    }

    @Test
    fun extremeCoordinatesAndDoublesRoundTrip() {
        val extremes = listOf(-90.0 to -180.0, 90.0 to 180.0, 0.1 + 0.2 to Math.PI, 1e-7 to -1e-7)
        for ((lat, lon) in extremes) {
            val p = F.placeA.copy(latDeg = lat, lonDeg = lon)
            assertEquals(p, PlaceCodec.geoFromJson(PlaceCodec.geoToJson(p)))
        }
        val c = CalcSettings(pressureHPa = 0.0, temperatureC = -272.9)
        assertEquals(c, SettingsCodec.calcFromJson(SettingsCodec.calcToJson(c)))
    }

    @Test
    fun outOfRangeCoordinatesRejected() {
        for (lat in listOf(90.0001, -91.0)) {
            assertFailsWith<CodecException> {
                PlaceCodec.geoFromJson(PlaceCodec.geoToJson(F.placeA.copy(latDeg = lat)))
            }
        }
        assertFailsWith<CodecException> {
            PlaceCodec.geoFromJson(PlaceCodec.geoToJson(F.placeA.copy(lonDeg = 180.5)))
        }
    }

    @Test
    fun missingCalcFieldReadsAsDefault() {
        val full = SettingsCodec.calcToJson(F.profileA.calc)
        val partial = JsonObject(full.filterKeys { it != "ayanamsa" && it != "temp" })
        val c = SettingsCodec.calcFromJson(partial)
        assertEquals(CalcSettings().ayanamsa, c.ayanamsa)
        assertEquals(CalcSettings().temperatureC, c.temperatureC)
        assertEquals(F.profileA.calc.pressureHPa, c.pressureHPa)
    }

    @Test
    fun failsClosed() {
        val good = ProfileCodec.toJson(F.profileA)
        fun bad(o: JsonObject) = assertFailsWith<CodecException> { ProfileCodec.fromJson(o) }
        bad(JsonObject(good + ("extra" to JsonPrimitive(1))))
        bad(JsonObject(good - "name"))
        bad(JsonObject(good + ("relation" to JsonPrimitive("CHILD_OF_NOBODY"))))
        bad(JsonObject(good + ("id" to JsonPrimitive("XYZ"))))
        bad(JsonObject(good + ("name" to JsonPrimitive(""))))
        bad(JsonObject(good + ("name" to JsonPrimitive("x".repeat(61)))))
        bad(JsonObject(good + ("notes" to JsonPrimitive("x".repeat(2001)))))
        bad(JsonObject(good + ("createdAt" to JsonPrimitive("yesterday"))))
        bad(JsonObject(good + ("birth" to buildJsonObject { put("date", "1990-01-01") })))
        assertFailsWith<CodecException> { FamilyDocument.fromBytes("not json".toByteArray()) }
        assertFailsWith<CodecException> { FamilyDocument.fromBytes(byteArrayOf(0xC3.toByte())) }
        assertFailsWith<CodecException> { FamilyDocument.fromBytes("[]".toByteArray()) }
    }

    @Test
    fun codecExceptionMessageIsStatic() {
        val e = assertFailsWith<CodecException> {
            ProfileCodec.fromJson(JsonObject(ProfileCodec.toJson(F.profileA) + ("name" to JsonPrimitive(""))))
        }
        assertEquals("Invalid data", e.message)
    }

    @Test
    fun documentRulesAreEnforced() {
        val obj = F.doc.toJson()
        fun doc(f: (FamilyDocument) -> FamilyDocument) = f(F.doc).toBytes()
        fun rejects(bytes: ByteArray) = assertFailsWith<CodecException> { FamilyDocument.fromBytes(bytes) }
        rejects(JsonObject(obj + ("v" to JsonPrimitive(2))).toString().toByteArray())
        rejects(doc { it.copy(profiles = it.profiles + it.profiles) })
        val secondSelf = F.profileB.copy(relation = Relation.SELF)
        rejects(doc { it.copy(profiles = listOf(F.profileA, secondSelf)) })
        val many = List(501) { F.profileB.copy(id = F.id(1000 + it)) }
        rejects(doc { it.copy(profiles = many) })
        rejects(doc { it.copy(places = List(2) { F.savedPlace }) })
    }

    @Test
    fun frozenFixtureDecodesToTheDocumentAndBack() {
        val bytes = fixture.readBytes()
        val doc = FamilyDocument.fromBytes(bytes)
        assertEquals(F.doc, doc)
        // M07: re-encoding == frozen JSON plus exactly the 15 new settings keys, at their default values.
        val frozen = PpJson.parseToJsonElement(bytes.decodeToString()) as JsonObject
        val reencoded = doc.toJson()
        val newKeys =
            setOf(
                "homePlaceId", "monthSystem", "presetSource", "ekadashi", "chartStyle", "themeMode",
                "dynamicColor", "badaText", "reduceMotion", "reduceTransparency", "reminders", "onboardingDone",
                "showCityOnWidgets", "travelMode", "calendarView",
            )
        val settings = reencoded["settings"] as JsonObject
        assertEquals(newKeys, settings.keys - (frozen["settings"] as JsonObject).keys)
        val defaults = SettingsCodec.toJson(AppSettings())
        for (k in newKeys) assertEquals(defaults[k], settings[k], k)
        val stripped = JsonObject(reencoded + ("settings" to JsonObject(settings.filterKeys { it !in newKeys })))
        assertEquals(frozen, stripped)
        assertEquals(2, doc.profiles.size)
        assertEquals(null, doc.profiles[1].birth.time)
        assertEquals(null, doc.bonds[0].traits.lagna)
    }
}
