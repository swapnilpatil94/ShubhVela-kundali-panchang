package app.parivaar.panchang.core.share.bondcode

import app.parivaar.panchang.core.model.bond.BondTraits
import app.parivaar.panchang.engine.match.vashyaChoices
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.text.Normalizer

/**
 * A Bond Code could not be read or written. The message is the [reason] name only, never an input byte, a
 * trait or a name.
 */
class BondCodeException(val reason: Reason) : IllegalArgumentException(reason.name) {
    /** Why a code was rejected (stable ids for tests and the error state; not display text). */
    enum class Reason {
        EMPTY, TOO_LONG, BAD_CHARACTER, NON_CANONICAL, BAD_LENGTH, UNSUPPORTED_VERSION, BAD_CHECKSUM, BAD_FIELD,
        BAD_NAME, MISSING_VASHYA, BAD_LINK,
    }
}

/**
 * Bond Code v1 (docs/specs/M12.md rule 13): derived traits only, never a birth date, time or place.
 *
 * Bytes: b0 = 0x01; b1 = mn; b2 = (mp - 1) << 6 | mars << 4 | hasLagna << 3 | vs; b3 = mr << 4 | (lg or 0xF);
 * b4 = ven << 4 | sun; b5 = L (UTF-8 bytes of the NFC name, 1..36; 1..12 code points); the name; then CRC-8
 * (poly 0x07, init 0, no reflection, xorout 0) over every prior byte. Text: base64url without padding.
 *
 * Detection guarantee: CRC-8/0x07 detects every error burst of at most 8 bits, so every single-bit flip and
 * every single-character substitution (at most 6 adjacent bits; a change of only the unused trailing bits is
 * rejected as non-canonical) is always rejected. Insertions, deletions and multi-character edits are caught by
 * the length checks or, failing those, by the CRC with probability about 255/256 only.
 *
 * g, y and nd are not carried (D5): [decode] recomputes them from mn (indices: gana Deva 0, Manushya 1,
 * Rakshasa 2; yoni in the `rules/koota.json` order Horse 0 .. Lion 13; nadi Adi 0, Madhya 1, Antya 2).
 */
object BondCode {
    const val VERSION = 1
    const val MAX_CODE_CHARS = 128
    const val MAX_NAME_CODE_POINTS = 12
    const val MAX_NAME_BYTES = 36
    private const val MAX_LINK_CHARS = 512
    private const val HEADER = 6
    private const val B_SIGNS = 3
    private const val B_SUN = 4
    private const val OVERHEAD = 7
    private const val NO_LAGNA = 0xF
    private const val MAX_NAKSHATRA = 26
    private const val MAX_SIGN = 11
    private const val MAX_MARS = 2
    private const val MAX_VASHYA = 4
    private const val PADAS = 4
    private const val PADAS_PER_SIGN = 9
    private const val BYTE = 0xFF
    private const val NIBBLE = 0xF
    private const val CRC_POLY = 0x07
    private const val CRC_TOP = 0x80
    private const val SHIFT_PADA = 6
    private const val SHIFT_MARS = 4
    private const val SHIFT_LAGNA_FLAG = 3
    private const val SHIFT_HIGH = 4
    private const val MASK_2 = 0x3
    private const val MASK_3 = 0x7
    private const val BITS = 6
    private const val BYTE_BITS = 8
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    private const val LINK_PATH = "/b#"

    // Indices per nakshatra 0..26 of the gana, yoni and nadi codes of rules/nakshatra.json (BondCodeTest checks).
    private val GANA = intArrayOf(0, 1, 2, 1, 0, 1, 0, 0, 2, 2, 1, 1, 0, 2, 0, 2, 0, 2, 2, 1, 1, 0, 2, 2, 1, 1, 0)
    private val YONI = intArrayOf(
        0, 1, 2, 3, 3, 4, 5, 2, 5, 6, 6, 7, 8, 9, 8, 9, 10, 10, 4, 11, 12, 11, 13, 0, 13, 7, 1,
    )
    private val NADI = intArrayOf(0, 1, 2, 2, 1, 0, 0, 1, 2, 2, 1, 0, 0, 1, 2, 2, 1, 0, 0, 1, 2, 2, 1, 0, 0, 1, 2)

    private val BIDI = setOf(0x061C, 0x200E, 0x200F) + (0x202A..0x202E) + (0x2066..0x2069)

    private fun fail(r: BondCodeException.Reason): Nothing = throw BondCodeException(r)

    /** The code of [t]; throws [BondCodeException] if a trait is out of range or the name is not shareable. */
    fun encode(t: BondTraits): String {
        val vs = t.vashya ?: fail(BondCodeException.Reason.MISSING_VASHYA)
        val name = Normalizer.normalize(t.name, Normalizer.Form.NFC)
        checkName(name)
        val nameBytes = name.encodeToByteArray()
        if (nameBytes.size > MAX_NAME_BYTES) fail(BondCodeException.Reason.BAD_NAME)
        checkTraits(t.moonNakshatra, t.moonPada, t.moonRashi, t.lagna, t.manglik, vs)
        val b = ByteArray(OVERHEAD + nameBytes.size)
        b[0] = VERSION.toByte()
        b[1] = t.moonNakshatra.toByte()
        val hasLagna = if (t.lagna != null) 1 else 0
        b[2] = ((t.moonPada - 1) shl SHIFT_PADA or (t.manglik shl SHIFT_MARS) or
            (hasLagna shl SHIFT_LAGNA_FLAG) or vs).toByte()
        b[B_SIGNS] = (t.moonRashi shl SHIFT_HIGH or (t.lagna ?: NO_LAGNA)).toByte()
        b[B_SUN] = (t.venusSign shl SHIFT_HIGH or t.sunSign).toByte()
        b[HEADER - 1] = nameBytes.size.toByte()
        nameBytes.copyInto(b, HEADER)
        b[b.size - 1] = crc8(b, b.size - 1).toByte()
        return base64(b)
    }

    /** The traits of [s]; throws [BondCodeException] on any defect (every check of rule 13). */
    @Suppress("CyclomaticComplexMethod") // a flat list of the spec's reject rules
    fun decode(s: String): BondTraits {
        if (s.isEmpty()) fail(BondCodeException.Reason.EMPTY)
        if (s.length > MAX_CODE_CHARS) fail(BondCodeException.Reason.TOO_LONG)
        val b = unbase64(s)
        if (b.size < OVERHEAD + 1) fail(BondCodeException.Reason.BAD_LENGTH)
        if (b[0].toInt() != VERSION) fail(BondCodeException.Reason.UNSUPPORTED_VERSION)
        val len = b[HEADER - 1].toInt() and BYTE
        if (b.size != OVERHEAD + len) fail(BondCodeException.Reason.BAD_LENGTH)
        if (crc8(b, b.size - 1) != (b[b.size - 1].toInt() and BYTE)) fail(BondCodeException.Reason.BAD_CHECKSUM)
        if (len !in 1..MAX_NAME_BYTES) fail(BondCodeException.Reason.BAD_NAME)
        val mn = b[1].toInt() and BYTE
        val b2 = b[2].toInt() and BYTE
        val b3 = b[B_SIGNS].toInt() and BYTE
        val b4 = b[B_SUN].toInt() and BYTE
        val mp = (b2 shr SHIFT_PADA) + 1
        val mars = (b2 shr SHIFT_MARS) and MASK_2
        val hasLagna = (b2 shr SHIFT_LAGNA_FLAG) and 1
        val vs = b2 and MASK_3
        val mr = b3 shr SHIFT_HIGH
        val lgRaw = b3 and NIBBLE
        if ((hasLagna == 0) != (lgRaw == NO_LAGNA)) fail(BondCodeException.Reason.BAD_FIELD)
        val lg = if (hasLagna == 1) lgRaw else null
        val ven = b4 shr SHIFT_HIGH
        val sun = b4 and NIBBLE
        if (ven > MAX_SIGN || sun > MAX_SIGN) fail(BondCodeException.Reason.BAD_FIELD)
        checkTraits(mn, mp, mr, lg, mars, vs)
        val name = utf8(b, HEADER, len)
        checkName(name)
        if (Normalizer.normalize(name, Normalizer.Form.NFC) != name) fail(BondCodeException.Reason.BAD_NAME)
        return try {
            BondTraits(
                name = name, moonNakshatra = mn, moonPada = mp, moonRashi = mr, lagna = lg, manglik = mars,
                venusSign = ven, sunSign = sun, gana = GANA[mn], yoni = YONI[mn],
                nadi = NADI[mn], vashya = vs,
            )
        } catch (_: IllegalArgumentException) { // BondTraits counts UTF-16 units; message never carries input
            fail(BondCodeException.Reason.BAD_NAME)
        }
    }

    /**
     * A raw code, or the link `https://<allowedHost>/b#<code>`; any other scheme, host, port, user info, path or
     * query is rejected (the code travels only in the fragment). Surrounding whitespace is ignored.
     */
    fun fromLinkOrCode(s: String, allowedHost: String): BondTraits {
        val t = s.trim()
        if (t.length > MAX_LINK_CHARS) fail(BondCodeException.Reason.TOO_LONG)
        if (!t.contains(':') && !t.contains('/')) return decode(t)
        val prefix = "https://" + allowedHost.lowercase() + LINK_PATH
        if (allowedHost.isEmpty() || !t.lowercase().startsWith(prefix)) fail(BondCodeException.Reason.BAD_LINK)
        return decode(t.substring(prefix.length))
    }

    /** The share link of [code] on [host]. */
    fun link(code: String, host: String): String = "https://$host$LINK_PATH$code"

    private fun checkTraits(mn: Int, mp: Int, mr: Int, lg: Int?, mars: Int, vs: Int) {
        if (mn !in 0..MAX_NAKSHATRA || mp !in 1..PADAS || mr !in 0..MAX_SIGN) fail(BondCodeException.Reason.BAD_FIELD)
        if (lg != null && lg !in 0..MAX_SIGN) fail(BondCodeException.Reason.BAD_FIELD)
        if (mars !in 0..MAX_MARS || vs !in 0..MAX_VASHYA) fail(BondCodeException.Reason.BAD_FIELD)
        if (mr != (mn * PADAS + mp - 1) / PADAS_PER_SIGN) fail(BondCodeException.Reason.BAD_FIELD)
        if (vashyaChoices(mn, mp).none { it.ordinal == vs }) fail(BondCodeException.Reason.BAD_FIELD)
    }

    private fun checkName(name: String) {
        val cps = name.codePoints().toArray()
        if (cps.size !in 1..MAX_NAME_CODE_POINTS) fail(BondCodeException.Reason.BAD_NAME)
        for (c in cps) {
            val type = Character.getType(c)
            val bad = type == Character.CONTROL.toInt() || type == Character.SURROGATE.toInt() ||
                type == Character.LINE_SEPARATOR.toInt() || type == Character.PARAGRAPH_SEPARATOR.toInt() ||
                type == Character.UNASSIGNED.toInt() || c in BIDI
            if (bad) fail(BondCodeException.Reason.BAD_NAME)
        }
    }

    private fun utf8(b: ByteArray, off: Int, len: Int): String = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(b, off, len)).toString()
    } catch (_: CharacterCodingException) {
        fail(BondCodeException.Reason.BAD_NAME)
    }

    /** CRC-8, poly 0x07, init 0x00, no reflection, xorout 0x00, over the first [len] bytes of [b]. */
    fun crc8(b: ByteArray, len: Int = b.size): Int {
        var crc = 0
        for (i in 0 until len) {
            crc = crc xor (b[i].toInt() and BYTE)
            repeat(BYTE_BITS) { crc = if (crc and CRC_TOP != 0) (crc shl 1 xor CRC_POLY) and BYTE else crc shl 1 }
        }
        return crc
    }

    private fun base64(b: ByteArray): String {
        val sb = StringBuilder()
        var acc = 0
        var n = 0
        for (x in b) {
            acc = acc shl BYTE_BITS or (x.toInt() and BYTE)
            n += BYTE_BITS
            while (n >= BITS) {
                n -= BITS
                sb.append(ALPHABET[(acc shr n) and MASK_6])
            }
            acc = acc and ((1 shl n) - 1)
        }
        if (n > 0) sb.append(ALPHABET[(acc shl (BITS - n)) and MASK_6])
        return sb.toString()
    }

    private const val MASK_6 = 0x3F

    private fun unbase64(s: String): ByteArray {
        if (s.length % PADS == 1) fail(BondCodeException.Reason.NON_CANONICAL)
        val out = ByteArray(s.length * BITS / BYTE_BITS)
        var acc = 0
        var n = 0
        var k = 0
        for (ch in s) {
            val v = ALPHABET.indexOf(ch)
            if (v < 0) fail(BondCodeException.Reason.BAD_CHARACTER)
            acc = acc shl BITS or v
            n += BITS
            if (n >= BYTE_BITS) {
                n -= BYTE_BITS
                out[k++] = (acc shr n).toByte()
                acc = acc and ((1 shl n) - 1)
            }
        }
        if (acc != 0) fail(BondCodeException.Reason.NON_CANONICAL) // unused trailing bits must be zero
        return out
    }

    private const val PADS = 4
}
