package app.parivaar.panchang.core.model.bond

import app.parivaar.panchang.core.model.profile.RecordId
import kotlin.time.Instant

/** Derived traits only (MASTER Part 8). [gana], [yoni] and [nadi] are range-checked by the Bond Code codec (M12). */
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
    }

    override fun toString(): String = "BondTraits(redacted)"

    companion object {
        val NAME_RANGE = 1..12
        private val SIGN_RANGE = 0..11
        private const val MAX_NAKSHATRA = 26
        private const val MAX_PADA = 4
    }
}

/** [kindKey] matches `^[a-z_]{1,32}$`. */
data class Bond(val id: RecordId, val traits: BondTraits, val kindKey: String, val createdAt: Instant) {
    init {
        require(kindKey.length in 1..KIND_MAX && kindKey.all { it in 'a'..'z' || it == '_' }) { "Invalid bond kind" }
    }

    override fun toString(): String = "Bond(id=${id.hex})"

    private companion object {
        const val KIND_MAX = 32
    }
}
