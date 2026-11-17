package app.parivaar.panchang.engine.jyotish

import app.parivaar.panchang.engine.jyotish.internal.RuleJson
import kotlinx.serialization.json.JsonObject

/**
 * Moon-derived Avakhada ids. Every field is an id or a code, never display text.
 *
 * @property moonRashi sign of the Moon
 * @property rashiLord lord of that sign
 * @property nakshatra 0..26
 * @property pada 1..4
 * @property nakshatraLord Vimshottari lord of the nakshatra
 * @property varna varna code of the sign
 * @property vashya vashya code of the sign (by the Moon's degree where the sign has two)
 * @property yoni yoni code of the nakshatra
 * @property gana gana code of the nakshatra
 * @property nadi nadi code of the nakshatra
 * @property tatva element code of the sign
 * @property deity deity id of the nakshatra
 */
@Suppress("LongParameterList") // the signature is the M02 contract (docs/specs/M02.md section 2)
data class Avakhada(
    val moonRashi: Rashi,
    val rashiLord: Graha,
    val nakshatra: Int,
    val pada: Int,
    val nakshatraLord: Graha,
    val varna: String,
    val vashya: String,
    val yoni: String,
    val gana: String,
    val nadi: String,
    val tatva: String,
    val deity: String,
)

/**
 * The 27-nakshatra and 12-rashi attribute tables read from `rules/nakshatra.json` (docs/specs/M02.md section
 * 3.6). Traditional values stated in our own words; VERIFY with a pandit before any UI shows them. Immutable.
 */
class NakshatraTable private constructor(
    private val nakshatras: List<NakshatraRow>,
    private val rashis: List<RashiRow>,
) {
    private class NakshatraRow(
        val id: String,
        val deity: String,
        val gana: String,
        val yoni: String,
        val nadi: String,
        val symbolId: String,
    )

    private class RashiRow(val varna: String, val vashya: List<String>, val tatva: String)

    /** Id of nakshatra [n] (0..26), e.g. `ASHWINI`. */
    fun id(n: Int): String {
        require(n in nakshatras.indices) { "nakshatra index out of range" }
        return nakshatras[n].id
    }

    /** Id of the symbol content of nakshatra [n] (0..26). */
    fun symbolId(n: Int): String {
        require(n in nakshatras.indices) { "nakshatra index out of range" }
        return nakshatras[n].symbolId
    }

    /** Gana code of nakshatra [n] (0..26), e.g. `DEVA`. */
    fun gana(n: Int): String = nak(n).gana

    /** Yoni code of nakshatra [n] (0..26), e.g. `HORSE`. */
    fun yoni(n: Int): String = nak(n).yoni

    /** Nadi code of nakshatra [n] (0..26), e.g. `ADI`. */
    fun nadi(n: Int): String = nak(n).nadi

    /** Varna code of sign [rashi] (0..11), e.g. `KSHATRIYA`. */
    fun varna(rashi: Int): String {
        require(rashi in rashis.indices) { "rashi index out of range" }
        return rashis[rashi].varna
    }

    /** Vashya codes of sign [rashi] (0..11): one, or two (first half, second half from 15 degrees). */
    fun vashyas(rashi: Int): List<String> {
        require(rashi in rashis.indices) { "rashi index out of range" }
        return rashis[rashi].vashya
    }

    private fun nak(n: Int): NakshatraRow {
        require(n in nakshatras.indices) { "nakshatra index out of range" }
        return nakshatras[n]
    }

    /** The Avakhada of the Moon of [facts]: sign, degree, nakshatra and pada. */
    fun avakhada(facts: ChartFacts): Avakhada {
        val moon = facts.of(Graha.MOON)
        val nak = nakshatras[moon.nakshatra]
        val rashi = rashis[moon.rashi.ordinal]
        val vashya = if (rashi.vashya.size == 1 || moon.degInRashi < HALF_SIGN) rashi.vashya[0] else rashi.vashya[1]
        return Avakhada(
            moonRashi = moon.rashi,
            rashiLord = Zodiac.lordOf(moon.rashi),
            nakshatra = moon.nakshatra,
            pada = moon.pada,
            nakshatraLord = Zodiac.nakshatraLord(moon.nakshatra),
            varna = rashi.varna,
            vashya = vashya,
            yoni = nak.yoni,
            gana = nak.gana,
            nadi = nak.nadi,
            tatva = rashi.tatva,
            deity = nak.deity,
        )
    }

    companion object {
        private const val NAKSHATRA_COUNT = 27
        private const val RASHI_COUNT = 12
        private const val HALF_SIGN = 15.0
        private val GANAS = setOf("DEVA", "MANUSHYA", "RAKSHASA")
        private val NADIS = setOf("ADI", "MADHYA", "ANTYA")
        private val VARNAS = setOf("KSHATRIYA", "VAISHYA", "SHUDRA", "BRAHMIN")
        private val VASHYAS = setOf("CHATUSHPADA", "MANAVA", "JALACHARA", "VANACHARA", "KEETA")
        private val TATVAS = setOf("FIRE", "EARTH", "AIR", "WATER")
        private val TOP_KEYS = setOf("version", "nakshatras", "rashis")
        private val NAK_KEYS = setOf("id", "deity", "gana", "yoni", "nadi", "symbolId")
        private val RASHI_KEYS = setOf("id", "varna", "vashya", "tatva")

        /** Parses and validates `rules/nakshatra.json`; throws [RuleFormatException] on any defect. */
        fun parse(json: String): NakshatraTable {
            val root = RuleJson.obj(RuleJson.parse(json), "$")
            RuleJson.onlyKeys(root, TOP_KEYS, "$")
            RuleJson.intIn(RuleJson.field(root, "version", "$"), "$.version", 1..1)
            val naks = RuleJson.arr(RuleJson.field(root, "nakshatras", "$"), "$.nakshatras")
            if (naks.size != NAKSHATRA_COUNT) RuleJson.fail("BAD_COUNT", "$.nakshatras")
            val rs = RuleJson.arr(RuleJson.field(root, "rashis", "$"), "$.rashis")
            if (rs.size != RASHI_COUNT) RuleJson.fail("BAD_COUNT", "$.rashis")
            val nakRows = naks.mapIndexed { i, e -> nakshatraRow(RuleJson.obj(e, "$.nakshatras[$i]"), i) }
            if (nakRows.map { it.id }.toSet().size != NAKSHATRA_COUNT) RuleJson.fail("DUPLICATE", "$.nakshatras")
            val rashiRows = rs.mapIndexed { i, e -> rashiRow(RuleJson.obj(e, "$.rashis[$i]"), i) }
            return NakshatraTable(nakRows, rashiRows)
        }

        private fun nakshatraRow(o: JsonObject, i: Int): NakshatraRow {
            val p = "$.nakshatras[$i]"
            RuleJson.onlyKeys(o, NAK_KEYS, p)
            fun s(k: String) = RuleJson.nonBlank(RuleJson.field(o, k, p), "$p.$k")
            return NakshatraRow(
                id = s("id"),
                deity = s("deity"),
                gana = RuleJson.oneOf(s("gana"), GANAS, "$p.gana"),
                yoni = s("yoni"),
                nadi = RuleJson.oneOf(s("nadi"), NADIS, "$p.nadi"),
                symbolId = s("symbolId"),
            )
        }

        private fun rashiRow(o: JsonObject, i: Int): RashiRow {
            val p = "$.rashis[$i]"
            RuleJson.onlyKeys(o, RASHI_KEYS, p)
            if (RuleJson.str(RuleJson.field(o, "id", p), "$p.id") != Rashi.entries[i].name) {
                RuleJson.fail("BAD_VALUE", "$p.id")
            }
            val vashya = RuleJson.arr(RuleJson.field(o, "vashya", p), "$p.vashya")
                .mapIndexed { j, e -> RuleJson.oneOf(RuleJson.str(e, "$p.vashya[$j]"), VASHYAS, "$p.vashya[$j]") }
            if (vashya.size !in 1..2) RuleJson.fail("BAD_COUNT", "$p.vashya")
            return RashiRow(
                varna = RuleJson.oneOf(RuleJson.str(RuleJson.field(o, "varna", p), "$p.varna"), VARNAS, "$p.varna"),
                vashya = vashya,
                tatva = RuleJson.oneOf(RuleJson.str(RuleJson.field(o, "tatva", p), "$p.tatva"), TATVAS, "$p.tatva"),
            )
        }
    }
}
