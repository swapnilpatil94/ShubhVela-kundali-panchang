package app.parivaar.panchang.core.share.bondcode

import app.parivaar.panchang.core.model.bond.BondTraits
import app.parivaar.panchang.engine.match.vashyaChoices
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/** docs/specs/M12.md, `BondCodeFuzzTest` (seed 0x5EED). */
class BondCodeFuzzTest {
    private val rnd = Random(0x5EED)
    private val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    private val nameChars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ .-" +
        "अआकखगरिी्அகéü"

    private fun randomTraits(): BondTraits {
        val n = rnd.nextInt(27)
        val p = rnd.nextInt(1, 5)
        val choices = vashyaChoices(n, p)
        val len = rnd.nextInt(1, 13)
        val name = String(CharArray(len) { nameChars[rnd.nextInt(nameChars.length)] })
        return BondTraits(
            name = name, moonNakshatra = n, moonPada = p, moonRashi = (n * 4 + p - 1) / 9,
            lagna = if (rnd.nextBoolean()) null else rnd.nextInt(12), manglik = rnd.nextInt(3),
            venusSign = rnd.nextInt(12), sunSign = rnd.nextInt(12), gana = null, yoni = null, nadi = null,
            vashya = choices[rnd.nextInt(choices.size)].ordinal,
        )
    }

    /** decode returns a value or throws BondCodeException; anything else fails the test. */
    private fun safeDecode(s: String): BondTraits? = try {
        BondCode.decode(s)
    } catch (_: BondCodeException) {
        null
    } catch (t: Throwable) {
        fail("decode threw ${t::class.simpleName}")
    }

    private fun validCode(): String {
        while (true) {
            val t = randomTraits()
            try {
                return BondCode.encode(t)
            } catch (_: BondCodeException) {
                // a random name that NFC-normalises differently or hits a rule; draw again
            }
        }
    }

    @Test
    fun `100k random strings never crash`() {
        repeat(100_000) { i ->
            val len = rnd.nextInt(0, 140)
            val s = if (i % 2 == 0) {
                String(CharArray(len) { alphabet[rnd.nextInt(alphabet.length)] })
            } else {
                String(CharArray(len) { rnd.nextInt(0, 0x3000).toChar() })
            }
            safeDecode(s)
            if (i % 3 == 0) {
                try {
                    BondCode.fromLinkOrCode("https://example.org/b#$s", "example.org")
                } catch (_: BondCodeException) {
                    // expected for nearly all
                }
            }
        }
        repeat(20_000) { // random bytes with a correct CRC and version reach the field checks
            val b = ByteArray(rnd.nextInt(8, 44)) { rnd.nextInt(256).toByte() }
            b[0] = 1
            b[b.size - 1] = BondCode.crc8(b, b.size - 1).toByte()
            safeDecode(java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(b))
        }
    }

    @Test
    fun `10k random valid traits round-trip`() {
        repeat(10_000) {
            val t = randomTraits()
            val code = try {
                BondCode.encode(t)
            } catch (_: BondCodeException) {
                return@repeat
            }
            assertTrue(code.length <= BondCode.MAX_CODE_CHARS)
            val d = BondCode.decode(code)
            assertEquals(t.copy(gana = d.gana, yoni = d.yoni, nadi = d.nadi), d)
            assertEquals(code, BondCode.encode(d))
        }
    }

    @Test
    fun `every single-character substitution and single-bit flip of 1k codes is rejected`() {
        val dec = java.util.Base64.getUrlDecoder()
        val enc = java.util.Base64.getUrlEncoder().withoutPadding()
        repeat(1_000) {
            val code = validCode()
            for (i in code.indices) {
                for (c in alphabet) {
                    if (c == code[i]) continue
                    val s = code.substring(0, i) + c + code.substring(i + 1)
                    assertEquals(null, safeDecode(s), "substitution at $i accepted")
                }
                for (bit in 0 until 7) { // flips of the character itself (ASCII bits)
                    val s = code.substring(0, i) + (code[i].code xor (1 shl bit)).toChar() + code.substring(i + 1)
                    assertEquals(null, safeDecode(s), "char bit flip at $i accepted")
                }
            }
            val bytes = dec.decode(code)
            for (bit in 0 until bytes.size * 8) {
                val b = bytes.copyOf()
                b[bit / 8] = (b[bit / 8].toInt() xor (1 shl (bit % 8))).toByte()
                assertEquals(null, safeDecode(enc.encodeToString(b)), "byte bit flip $bit accepted")
            }
        }
    }

    @Test
    fun `insertions and deletions are rejected or decode to a different canonical code`() {
        var escaped = 0
        var total = 0
        repeat(1_000) {
            val code = validCode()
            for (i in 0..code.length) {
                val edits = buildList {
                    if (i < code.length) add(code.removeRange(i, i + 1))
                    add(code.substring(0, i) + alphabet[rnd.nextInt(alphabet.length)] + code.substring(i))
                }
                for (s in edits) {
                    total++
                    val d = safeDecode(s) ?: continue
                    escaped++
                    assertEquals(s, BondCode.encode(d)) // a valid code is canonical and differs from the original
                    assertTrue(s != code)
                }
            }
        }
        // Not guaranteed by CRC-8 (length changes): only the ~1/256 residual may slip through.
        assertTrue(escaped * 64 < total, "escaped $escaped of $total")
    }
}
