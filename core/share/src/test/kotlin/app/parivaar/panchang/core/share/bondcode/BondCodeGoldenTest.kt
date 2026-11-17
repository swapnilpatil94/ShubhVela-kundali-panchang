package app.parivaar.panchang.core.share.bondcode

import app.parivaar.panchang.core.model.bond.BondTraits
import app.parivaar.panchang.engine.jyotish.NakshatraTable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** docs/specs/M12.md, `BondCodeGoldenTest`: the vectors of tools/oracle/gen_bondcode.py plus the CRC check. */
class BondCodeGoldenTest {
    private val root = Json.parseToJsonElement(
        checkNotNull(javaClass.getResourceAsStream("/bondcode_vectors.json")) { "missing vectors" }
            .use { it.readBytes().decodeToString() },
    ).jsonObject

    private fun JsonObject.i(k: String) = getValue(k).jsonPrimitive.int

    private fun traits(o: JsonObject) = BondTraits(
        name = o.getValue("name").jsonPrimitive.content,
        moonNakshatra = o.i("mn"), moonPada = o.i("mp"), moonRashi = o.i("mr"),
        lagna = if (o.getValue("lg") is JsonNull) null else o.i("lg"),
        manglik = o.i("mars"), venusSign = o.i("ven"), sunSign = o.i("sun"),
        gana = o.i("g"), yoni = o.i("y"), nadi = o.i("nd"), vashya = o.i("vs"),
    )

    @Test
    fun `CRC-8 check value is 0xF4`() {
        assertEquals(0xF4, BondCode.crc8("123456789".encodeToByteArray()))
        assertEquals(0xF4, root.i("crcCheckValue"))
    }

    @Test
    fun `50 valid vectors encode and decode exactly`() {
        val valid = root.getValue("valid").jsonArray
        assertEquals(50, valid.size)
        for ((i, e) in valid.withIndex()) {
            val o = e.jsonObject
            val code = o.getValue("code").jsonPrimitive.content
            val t = traits(o)
            assertEquals(code, BondCode.encode(t.copy(gana = null, yoni = null, nadi = null)), "encode $i")
            assertEquals(t, BondCode.decode(code), "decode $i")
            assertEquals(t, BondCode.fromLinkOrCode(BondCode.link(code, "example.org"), "example.org"))
        }
    }

    @Test
    fun `every labelled invalid vector is rejected with a BondCodeException`() {
        val invalid = root.getValue("invalid").jsonArray
        assertTrue(invalid.size >= 30)
        for (e in invalid) {
            val o = e.jsonObject
            val why = o.getValue("why").jsonPrimitive.content
            val code = o.getValue("code").jsonPrimitive.content
            val ex = assertFailsWith<BondCodeException>(why) { BondCode.decode(code) }
            assertFalse(code.isNotEmpty() && ex.message!!.contains(code), "message must not carry input")
        }
    }

    @Test
    fun `recomputed gana yoni nadi equal rules nakshatra json`() {
        val dir = checkNotNull(System.getProperty("pp.root.dir"))
        val nak = NakshatraTable.parse(File(dir, "rules/nakshatra.json").readText())
        val ganas = listOf("DEVA", "MANUSHYA", "RAKSHASA")
        val nadis = listOf("ADI", "MADHYA", "ANTYA")
        val yonis = listOf(
            "HORSE", "ELEPHANT", "SHEEP", "SERPENT", "DOG", "CAT", "RAT", "COW", "BUFFALO", "TIGER", "DEER",
            "MONKEY", "MONGOOSE", "LION",
        )
        for (n in 0 until 27) {
            val p = 1
            val mr = (n * 4) / 9
            val vs = app.parivaar.panchang.engine.match.vashyaChoices(n, p)[0].ordinal
            val t = BondTraits("Ab", n, p, mr, null, 0, 0, 0, null, null, null, vs)
            val d = BondCode.decode(BondCode.encode(t))
            assertEquals(nak.gana(n), ganas[d.gana!!])
            assertEquals(nak.yoni(n), yonis[d.yoni!!])
            assertEquals(nak.nadi(n), nadis[d.nadi!!])
        }
    }

    @Test
    fun `links - only https, the allowed host and path b with a fragment`() {
        val code = root.getValue("valid").jsonArray[0].jsonObject.getValue("code").jsonPrimitive.content
        val host = "example.org"
        BondCode.fromLinkOrCode("  https://$host/b#$code\n", host)
        BondCode.fromLinkOrCode(code, host)
        for (bad in listOf(
            "http://$host/b#$code", "https://evil.org/b#$code", "https://$host.evil.org/b#$code",
            "https://$host/c#$code", "https://$host/b?code=$code", "https://$host:8443/b#$code",
            "https://user@$host/b#$code", "https://$host/b#", "https://$host/b/#$code", "intent://$host/b#$code",
            "https://$host/b#$code" + "x".repeat(600),
        )) {
            assertFailsWith<BondCodeException>(bad.take(40)) { BondCode.fromLinkOrCode(bad, host) }
        }
    }

    @Test
    fun `encode rejects a missing vashya and an unshareable name, and toString is redacted`() {
        val t = BondTraits("Asha", 0, 1, 0, 3, 1, 2, 4, null, null, null, 0)
        assertEquals(BondCodeException.Reason.MISSING_VASHYA, assertFailsWith<BondCodeException> {
            BondCode.encode(t.copy(vashya = null))
        }.reason)
        assertFailsWith<BondCodeException> { BondCode.encode(t.copy(name = "A‮B")) }
        assertFailsWith<BondCodeException> { BondCode.encode(t.copy(vashya = 1)) } // Mesha is Chatushpada
        assertFalse(BondCode.decode(BondCode.encode(t)).toString().contains("Asha"))
    }
}
