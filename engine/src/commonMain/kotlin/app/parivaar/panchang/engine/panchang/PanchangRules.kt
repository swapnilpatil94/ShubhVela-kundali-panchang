package app.parivaar.panchang.engine.panchang

import app.parivaar.panchang.engine.jyotish.internal.RuleJson
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * Parser for the panchang rule files (docs/specs/M03.md section 3.4): `rules/durmuhurta.json`,
 * `rules/choghadiya.json` and the optional, not yet shipped Gowri and varjyam files. Rules arrive as Strings
 * (no I/O). Every defect is a `RuleFormatException` with a code and a JSON path, never a value.
 */
object PanchangRules {
    private val STATUSES = setOf("APPROVED", "VERIFY")
    private val HEADER = setOf("version", "status", "sourceId")
    private val WEEKDAYS = Vaar.entries.map { it.name }.toSet()
    private val CHOGHADIYA_IDS = Choghadiya.entries.map { it.name }.toSet()
    private val ID = Regex("[A-Z_]+")
    private val NAKSHATRAS = listOf(
        "ASHWINI", "BHARANI", "KRITTIKA", "ROHINI", "MRIGASHIRA", "ARDRA", "PUNARVASU", "PUSHYA", "ASHLESHA",
        "MAGHA", "PURVA_PHALGUNI", "UTTARA_PHALGUNI", "HASTA", "CHITRA", "SWATI", "VISHAKHA", "ANURADHA",
        "JYESHTHA", "MULA", "PURVA_ASHADHA", "UTTARA_ASHADHA", "SHRAVANA", "DHANISHTA", "SHATABHISHA",
        "PURVA_BHADRAPADA", "UTTARA_BHADRAPADA", "REVATI",
    )
    private const val MUHURTAS = 15
    private const val PARTS = 8
    private const val MAX_GHATI = 56.0

    /**
     * Parses [durmuhurta] and [choghadiya] (required) and [gowri] and [varjyam] (optional) into immutable
     * [PanchangTables]; an absent optional file leaves that table null.
     */
    fun parse(durmuhurta: String, choghadiya: String, gowri: String? = null, varjyam: String? = null): PanchangTables =
        PanchangTables(
            durmuhurta = durmuhurtaSlots(durmuhurta),
            choghadiya = eightPart(choghadiya, withDay = false, allowed = CHOGHADIYA_IDS),
            gowri = gowri?.let { eightPart(it, withDay = true, allowed = null) },
            varjyam = varjyam?.let { varjyamTable(it) },
        )

    /** Checks version, status and sourceId and that no key is outside [HEADER] plus [payload]. */
    private fun header(text: String, payload: Set<String>): JsonObject {
        val root = RuleJson.obj(RuleJson.parse(text), "$")
        RuleJson.onlyKeys(root, HEADER + payload, "$")
        RuleJson.intIn(RuleJson.field(root, "version", "$"), "$.version", 1..1)
        RuleJson.oneOf(RuleJson.str(RuleJson.field(root, "status", "$"), "$.status"), STATUSES, "$.status")
        RuleJson.nonBlank(RuleJson.field(root, "sourceId", "$"), "$.sourceId")
        return root
    }

    /** A weekday-keyed object: exactly the seven weekday names, each mapped by [row]. */
    private fun <T> byWeekday(e: JsonElement, path: String, row: (JsonElement, String) -> T): Map<Vaar, T> {
        val o = RuleJson.obj(e, path)
        RuleJson.onlyKeys(o, WEEKDAYS, path)
        return Vaar.entries.associateWith { row(RuleJson.field(o, it.name, path), "$path.${it.name}") }
    }

    private fun bool(e: JsonElement, path: String): Boolean =
        (e as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull ?: RuleJson.fail("BAD_TYPE", path)

    private fun durmuhurtaSlots(text: String): Map<Vaar, List<DurmuhurtaSlot>> {
        val root = header(text, setOf("weekdays"))
        return byWeekday(RuleJson.field(root, "weekdays", "$"), "$.weekdays") { e, p ->
            val arr = RuleJson.arr(e, p)
            if (arr.isEmpty() || arr.size > MUHURTAS) RuleJson.fail("BAD_LENGTH", p)
            val slots = arr.mapIndexed { i, s ->
                val o = RuleJson.obj(s, "$p[$i]")
                RuleJson.onlyKeys(o, setOf("night", "muhurta"), "$p[$i]")
                DurmuhurtaSlot(
                    night = bool(RuleJson.field(o, "night", "$p[$i]"), "$p[$i].night"),
                    muhurta = RuleJson.intIn(RuleJson.field(o, "muhurta", "$p[$i]"), "$p[$i].muhurta", 1..MUHURTAS),
                )
            }
            if (slots.toSet().size != slots.size) RuleJson.fail("DUPLICATE", p)
            slots
        }
    }

    /** Choghadiya file (night only, ids restricted to [allowed]) or Gowri file (day and night, any id). */
    private fun eightPart(text: String, withDay: Boolean, allowed: Set<String>?): EightPartTable {
        val root = header(text, if (withDay) setOf("day", "night") else setOf("night"))
        val row = { e: JsonElement, p: String ->
            val arr = RuleJson.arr(e, p)
            if (arr.size != PARTS) RuleJson.fail("BAD_LENGTH", p)
            arr.mapIndexed { i, v ->
                val id = RuleJson.str(v, "$p[$i]")
                if (!ID.matches(id)) RuleJson.fail("BAD_VALUE", "$p[$i]")
                if (allowed != null) RuleJson.oneOf(id, allowed, "$p[$i]") else id
            }
        }
        val day = if (withDay) byWeekday(RuleJson.field(root, "day", "$"), "$.day", row) else emptyMap()
        return EightPartTable(day, byWeekday(RuleJson.field(root, "night", "$"), "$.night", row))
    }

    private fun ghatiList(e: JsonElement, path: String): List<Double> =
        RuleJson.arr(e, path).mapIndexed { i, v ->
            RuleJson.double(v, "$path[$i]").also {
                if (it !in 0.0..MAX_GHATI) RuleJson.fail("OUT_OF_RANGE", "$path[$i]")
            }
        }

    private fun varjyamTable(text: String): VarjyamTable {
        val rows = RuleJson.arr(RuleJson.field(header(text, setOf("rows")), "rows", "$"), "$.rows")
        if (rows.size != NAKSHATRAS.size) RuleJson.fail("BAD_LENGTH", "$.rows")
        val parsed = rows.mapIndexed { i, r ->
            val p = "$.rows[$i]"
            val o = RuleJson.obj(r, p)
            RuleJson.onlyKeys(o, setOf("nakshatra", "varjyam", "amrit"), p)
            if (RuleJson.str(RuleJson.field(o, "nakshatra", p), "$p.nakshatra") != NAKSHATRAS[i]) {
                RuleJson.fail("OUT_OF_ORDER", "$p.nakshatra")
            }
            val varjyamGhati = ghatiList(RuleJson.field(o, "varjyam", p), "$p.varjyam")
            varjyamGhati to ghatiList(RuleJson.field(o, "amrit", p), "$p.amrit")
        }
        return VarjyamTable(parsed.map { it.first }, parsed.map { it.second })
    }
}
