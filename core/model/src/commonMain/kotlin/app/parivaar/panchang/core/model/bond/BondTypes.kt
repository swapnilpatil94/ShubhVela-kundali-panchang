package app.parivaar.panchang.core.model.bond

import app.parivaar.panchang.core.model.profile.RecordId
import kotlin.time.Instant

/**
 * Derived traits only (MASTER Part 8). [gana], [yoni] and [nadi] are range-checked by the Bond Code codec (M12).
 * [vashya] is the Moon's Vashya class (0..4, order Chatushpada, Manava, Jalachara, Vanachara, Keeta; M12 D5); null only
 * on rows stored before M12.
 */
data class BondTraits(
    val name: String,
    val moonNakshatra: Int,
    val moonPada: Int,
    val moonRashi: Int,
    val lagna: Int?,
    val manglik: Int,
    val venusSign: Int,
    val sunSign: Int,
    val gana: Int?,
    val yoni: Int?,
    val nadi: Int?,
    val vashya: Int? = null,
) {
    init {
        require(name.length in NAME_RANGE) { "Invalid bond trait" }
        require(moonNakshatra in 0..MAX_NAKSHATRA) { "Invalid bond trait" }
        require(moonPada in 1..MAX_PADA) { "Invalid bond trait" }
        require(moonRashi in SIGN_RANGE) { "Invalid bond trait" }
        require(lagna == null || lagna in SIGN_RANGE) { "Invalid bond trait" }
        require(manglik in 0..2) { "Invalid bond trait" }
        require(venusSign in SIGN_RANGE) { "Invalid bond trait" }
        require(sunSign in SIGN_RANGE) { "Invalid bond trait" }
        require(vashya == null || vashya in VASHYA_RANGE) { "Invalid bond trait" }
    }

    override fun toString(): String = "BondTraits(redacted)"

    companion object {
        val NAME_RANGE = 1..12
        private val SIGN_RANGE = 0..11
        private const val MAX_NAKSHATRA = 26
        private const val MAX_PADA = 4
        val VASHYA_RANGE = 0..4
    }
}

/**
 * [kindKey] matches `^[a-z_]{1,32}$`. [profileRef] is set when the traits were computed locally from a family or manual
 * profile (such a bond's card share is disabled, M12 rule 14). [payloadVersion] is the Bond Code version the traits
 * follow (1 for rows stored before M12).
 */
data class Bond(
    val id: RecordId,
    val traits: BondTraits,
    val kindKey: String,
    val createdAt: Instant,
    val profileRef: RecordId? = null,
    val payloadVersion: Int = 1,
) {
    init {
        require(kindKey.length in 1..KIND_MAX && kindKey.all { it in 'a'..'z' || it == '_' }) { "Invalid bond kind" }
        require(payloadVersion in 1..MAX_PAYLOAD_VERSION) { "Invalid bond version" }
    }

    override fun toString(): String = "Bond(id=${id.hex})"

    private companion object {
        const val KIND_MAX = 32
        const val MAX_PAYLOAD_VERSION = 255
    }
}
