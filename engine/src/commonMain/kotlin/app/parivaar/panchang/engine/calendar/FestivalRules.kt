package app.parivaar.panchang.engine.calendar

import app.parivaar.panchang.engine.jyotish.Rashi
import app.parivaar.panchang.engine.jyotish.RuleFormatException
import app.parivaar.panchang.engine.jyotish.RuleStatus
import app.parivaar.panchang.engine.jyotish.internal.RuleJson
import app.parivaar.panchang.engine.panchang.Paksha
import app.parivaar.panchang.engine.panchang.Vaar
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Parser for the festival rule files the files under `rules/festivals` (docs/specs/M04.md section 3.5). Rules arrive as
 * Strings (no I/O). Every defect is a [RuleFormatException] with a code and a JSON path, never a value.
 *
 * A rule is an object whose `type` is `tithi_at`, `weekday_tithi_combo`, `ekadashi`, `sankranti`,
 * `nakshatra_in_solar_month`, `nakshatra_in_lunar_month` (docs/specs/M10.md), `fixed_gregorian` or `relative`; the
 * other keys are the fields of the spec.
 * Enum-like values
 * are lower case, except Rashi and Vaar names, which are the enum names.
 */
@Suppress("TooManyFunctions") // one small reader per rule type and field; splitting would scatter the call sites
object FestivalRules {
    private val STATUSES = setOf("APPROVED", "VERIFY")
    private val HEADER = setOf("version", "status", "sourceId", "festivals")
    private val DEF_KEYS = setOf("id", "name", "status", "sourceId", "regions", "rule", "adhika", "vratRules")
    private val TITHI_KEYS = setOf("type", "tithi", "masa", "system", "kala", "tie", "none", "bhadra")
    private val RASHIS = Rashi.entries.associateBy { it.name }
    private val VAARS = Vaar.entries.associateBy { it.name }
    private val MASAS = Masa.entries.associateBy { it.name.lowercase() }
    private val SYSTEMS = MonthSystem.entries.associateBy { it.name.lowercase() }
    private val KALAS = Kala.entries.associateBy { it.name.lowercase() }
    private val BHADRA_KEYS = setOf("free_in", "next_day_min_ghatis")
    private val TIES = Tie.entries.associateBy { it.name.lowercase() }
    private val NONES = NoneRule.entries.associateBy { it.name.lowercase() }
    private val ADHIKAS = AdhikaPolicy.entries.associateBy { it.name.lowercase() }
    private val SANKRANTI_DAYS = SankrantiDay.entries.associateBy { it.name.lowercase() }
    private val TRADITIONS = Tradition.entries.associateBy { it.name.lowercase() }
    private val PAKSHAS = mapOf("shukla" to Paksha.SHUKLA, "krishna" to Paksha.KRISHNA, "both" to null)
    private val SOLAR_REGIONS = SolarRegion.entries.associateBy { it.name.lowercase() }
    private val PICKS = Pick.entries.associateBy { it.name.lowercase() }
    private val LUNAR_PICKS = PICKS - "nearest_full_moon"
    private val TITHI_KALAS = KALAS - "ahoratra"
    private val TITHIS = 1..30
    private val NAKSHATRAS = 0..26
    private val MONTHS = 1..12
    private val DAYS = 1..31
    private val GHATIS = 1..60
    private const val RELATIVE_DAYS = 3

    /** Parses one rule file; [file] names it (for [FestivalSet.file]) and is never read as a path. */
    fun parse(file: String, json: String): FestivalSet {
        val root = RuleJson.obj(RuleJson.parse(json), "$")
        RuleJson.onlyKeys(root, HEADER, "$")
        RuleJson.intIn(RuleJson.field(root, "version", "$"), "$.version", 1..1)
        status(RuleJson.field(root, "status", "$"), "$.status")
        RuleJson.nonBlank(RuleJson.field(root, "sourceId", "$"), "$.sourceId")
        val list = RuleJson.arr(RuleJson.field(root, "festivals", "$"), "$.festivals")
        if (list.isEmpty()) RuleJson.fail("BAD_LENGTH", "$.festivals")
        val defs = list.mapIndexed { i, e -> def(e, "$.festivals[$i]") }
        val seen = HashSet<String>()
        defs.forEachIndexed { i, d -> if (!seen.add(d.id)) RuleJson.fail("DUPLICATE", "$.festivals[$i].id") }
        val byId = defs.associateBy { it.id }
        defs.forEachIndexed { i, d ->
            val r = d.rule
            if (r is FestivalRule.Relative && byId[r.to]?.rule.let { it == null || it is FestivalRule.Relative }) {
                RuleJson.fail("BAD_VALUE", "$.festivals[$i].rule.to")
            }
        }
        return FestivalSet(file, defs)
    }

    private fun status(e: JsonElement, path: String): RuleStatus =
        RuleStatus.valueOf(RuleJson.oneOf(RuleJson.str(e, path), STATUSES, path))

    private fun id(e: JsonElement, path: String): String =
        RuleJson.str(e, path).also { if (!FESTIVAL_ID.matches(it)) RuleJson.fail("BAD_VALUE", path) }

    private fun def(e: JsonElement, path: String): FestivalDef {
        val o = RuleJson.obj(e, path)
        RuleJson.onlyKeys(o, DEF_KEYS, path)
        val id = id(RuleJson.field(o, "id", path), "$path.id")
        val name = RuleJson.str(RuleJson.field(o, "name", path), "$path.name")
        if (name != NAME_KEY_PREFIX + id) RuleJson.fail("BAD_VALUE", "$path.name")
        val status = status(RuleJson.field(o, "status", path), "$path.status")
        val source = RuleJson.nonBlank(RuleJson.field(o, "sourceId", path), "$path.sourceId")
        val regionsArr = RuleJson.arr(RuleJson.field(o, "regions", path), "$path.regions")
        if (regionsArr.isEmpty()) RuleJson.fail("BAD_LENGTH", "$path.regions")
        val regions = regionsArr.mapIndexed { i, r ->
            RuleJson.oneOf(RuleJson.str(r, "$path.regions[$i]"), FESTIVAL_REGIONS, "$path.regions[$i]")
        }
        if (regions.toSet().size != regions.size) RuleJson.fail("DUPLICATE", "$path.regions")
        val rule = rule(RuleJson.field(o, "rule", path), "$path.rule")
        val adhika = o["adhika"]?.let { pick(ADHIKAS, it, "$path.adhika") }
            ?: if (isAnyMasa(rule)) AdhikaPolicy.BOTH else AdhikaPolicy.NIJA
        val vrat = o["vratRules"]?.let { v ->
            RuleJson.arr(v, "$path.vratRules").mapIndexed { i, x -> id(x, "$path.vratRules[$i]") }
        } ?: emptyList()
        return FestivalDef(id, name, regions.toSet(), rule, adhika, vrat, status, source)
    }

    private fun isAnyMasa(rule: FestivalRule): Boolean = when (rule) {
        is FestivalRule.TithiAt -> rule.masa == null
        is FestivalRule.WeekdayTithi -> rule.inner.masa == null
        is FestivalRule.Ekadashi -> rule.masa == null
        else -> false
    }

    private fun <T> pick(table: Map<String, T>, e: JsonElement, path: String): T =
        table[RuleJson.str(e, path)] ?: RuleJson.fail("BAD_VALUE", path)

    private fun rule(e: JsonElement, path: String): FestivalRule {
        val o = RuleJson.obj(e, path)
        val type = RuleJson.str(RuleJson.field(o, "type", path), "$path.type")
        return when (type) {
            "tithi_at" -> tithiAt(o, path, null)
            "weekday_tithi_combo" -> {
                val day = pick(VAARS, RuleJson.field(o, "weekday", path), "$path.weekday")
                FestivalRule.WeekdayTithi(day, tithiAt(o, path, "weekday"))
            }
            "ekadashi" -> ekadashi(o, path)
            "sankranti" -> sankranti(o, path)
            "nakshatra_in_solar_month" -> nakshatraInSolarMonth(o, path)
            "nakshatra_in_lunar_month" -> nakshatraInLunarMonth(o, path)
            "fixed_gregorian" -> fixedGregorian(o, path)
            "relative" -> relative(o, path)
            else -> RuleJson.fail("BAD_VALUE", "$path.type")
        }
    }

    private fun masa(o: JsonObject, path: String): Masa? {
        val v = RuleJson.str(RuleJson.field(o, "masa", path), "$path.masa")
        return if (v == "any") null else MASAS[v] ?: RuleJson.fail("BAD_VALUE", "$path.masa")
    }

    private fun system(o: JsonObject, path: String): MonthSystem =
        o["system"]?.let { pick(SYSTEMS, it, "$path.system") } ?: MonthSystem.AMANTA

    private fun kala(o: JsonObject, path: String, table: Map<String, Kala> = TITHI_KALAS): Kala =
        pick(table, RuleJson.field(o, "kala", path), "$path.kala")

    private fun tithiAt(o: JsonObject, path: String, extra: String?): FestivalRule.TithiAt {
        RuleJson.onlyKeys(o, if (extra == null) TITHI_KEYS else TITHI_KEYS + extra, path)
        val t = RuleJson.field(o, "tithi", path)
        val tithis = if (t is JsonArray) {
            if (t.isEmpty()) RuleJson.fail("BAD_LENGTH", "$path.tithi")
            t.mapIndexed { i, x -> RuleJson.intIn(x, "$path.tithi[$i]", TITHIS) }
        } else {
            listOf(RuleJson.intIn(t, "$path.tithi", TITHIS))
        }
        if (tithis.toSet().size != tithis.size) RuleJson.fail("DUPLICATE", "$path.tithi")
        val (tie, prefer) = tie(o["tie"], "$path.tie")
        return FestivalRule.TithiAt(
            tithi = tithis,
            masa = masa(o, path),
            system = system(o, path),
            kala = kala(o, path),
            tie = tie,
            preferNakshatra = prefer,
            none = o["none"]?.let { pick(NONES, it, "$path.none") } ?: NoneRule.CONTAINING_START,
            bhadra = o["bhadra"]?.let { bhadra(it, "$path.bhadra") } ?: emptyList(),
        )
    }

    /** A non-empty list of steps: `{"free_in":kala}`, `"after_bhadra_before_midnight"`, `{"next_day_min_ghatis":n}`. */
    private fun bhadra(e: JsonElement, path: String): List<BhadraStep> {
        val arr = RuleJson.arr(e, path)
        if (arr.isEmpty()) RuleJson.fail("BAD_LENGTH", path)
        return arr.mapIndexed { i, step -> bhadraStep(step, "$path[$i]") }
    }

    private fun bhadraStep(e: JsonElement, path: String): BhadraStep {
        if (e is JsonPrimitive) {
            if (RuleJson.str(e, path) != "after_bhadra_before_midnight") RuleJson.fail("BAD_VALUE", path)
            return BhadraStep.AfterBhadraBeforeMidnight
        }
        val o = RuleJson.obj(e, path)
        RuleJson.onlyKeys(o, BHADRA_KEYS, path)
        if (o.size != 1) RuleJson.fail("BAD_LENGTH", path)
        return if ("free_in" in o) {
            BhadraStep.FreeIn(pick(TITHI_KALAS, RuleJson.field(o, "free_in", path), "$path.free_in"))
        } else {
            BhadraStep.NextDay(
                RuleJson.intIn(RuleJson.field(o, "next_day_min_ghatis", path), "$path.next_day_min_ghatis", GHATIS),
            )
        }
    }

    private fun relative(o: JsonObject, path: String): FestivalRule.Relative {
        RuleJson.onlyKeys(o, setOf("type", "to", "days"), path)
        val to = RuleJson.str(RuleJson.field(o, "to", path), "$path.to")
        if (!FESTIVAL_ID.matches(to)) RuleJson.fail("BAD_VALUE", "$path.to")
        val days = RuleJson.intIn(RuleJson.field(o, "days", path), "$path.days", -RELATIVE_DAYS..RELATIVE_DAYS)
        if (days == 0) RuleJson.fail("OUT_OF_RANGE", "$path.days")
        return FestivalRule.Relative(to, days)
    }

    private fun tie(e: JsonElement?, path: String): Pair<Tie, Int?> = when (e) {
        null -> Tie.FIRST to null
        is JsonPrimitive -> pick(TIES, e, path) to null
        is JsonObject -> {
            RuleJson.onlyKeys(e, setOf("prefer_nakshatra", "else"), path)
            val n = RuleJson.intIn(RuleJson.field(e, "prefer_nakshatra", path), "$path.prefer_nakshatra", NAKSHATRAS)
            pick(TIES, RuleJson.field(e, "else", path), "$path.else") to n
        }
        else -> RuleJson.fail("BAD_TYPE", path)
    }

    private fun ekadashi(o: JsonObject, path: String): FestivalRule.Ekadashi {
        RuleJson.onlyKeys(o, setOf("type", "tradition", "masa", "system", "paksha"), path)
        val paksha = RuleJson.str(RuleJson.field(o, "paksha", path), "$path.paksha")
        if (paksha !in PAKSHAS) RuleJson.fail("BAD_VALUE", "$path.paksha")
        return FestivalRule.Ekadashi(
            tradition = pick(TRADITIONS, RuleJson.field(o, "tradition", path), "$path.tradition"),
            masa = masa(o, path),
            system = system(o, path),
            paksha = PAKSHAS[paksha],
        )
    }

    private fun rashiOrAny(o: JsonObject, key: String, path: String): Rashi? {
        val v = RuleJson.str(RuleJson.field(o, key, path), "$path.$key")
        return if (v == "any") null else RASHIS[v] ?: RuleJson.fail("BAD_VALUE", "$path.$key")
    }

    private fun sankranti(o: JsonObject, path: String): FestivalRule.SankrantiRule {
        RuleJson.onlyKeys(o, setOf("type", "into", "day"), path)
        return FestivalRule.SankrantiRule(
            into = rashiOrAny(o, "into", path),
            day = pick(SANKRANTI_DAYS, RuleJson.field(o, "day", path), "$path.day"),
        )
    }

    private fun nakshatraInSolarMonth(o: JsonObject, path: String): FestivalRule.NakshatraInSolarMonth {
        RuleJson.onlyKeys(
            o,
            setOf("type", "nakshatra", "month", "region", "kala", "occurrence", "preceded_in_month_by"),
            path,
        )
        return FestivalRule.NakshatraInSolarMonth(
            nakshatra = RuleJson.intIn(RuleJson.field(o, "nakshatra", path), "$path.nakshatra", NAKSHATRAS),
            month = pick(RASHIS, RuleJson.field(o, "month", path), "$path.month"),
            region = pick(SOLAR_REGIONS, RuleJson.field(o, "region", path), "$path.region"),
            kala = kala(o, path, KALAS),
            pick = pick(PICKS, RuleJson.field(o, "occurrence", path), "$path.occurrence"),
            precededBy = o["preceded_in_month_by"]?.let {
                RuleJson.intIn(it, "$path.preceded_in_month_by", NAKSHATRAS)
            },
        )
    }

    /** `masa` is required and never `any`; `occurrence` is `first` or `last`; `system` defaults to amanta. */
    private fun nakshatraInLunarMonth(o: JsonObject, path: String): FestivalRule.NakshatraInLunarMonth {
        RuleJson.onlyKeys(o, setOf("type", "nakshatra", "masa", "system", "kala", "occurrence"), path)
        return FestivalRule.NakshatraInLunarMonth(
            nakshatra = RuleJson.intIn(RuleJson.field(o, "nakshatra", path), "$path.nakshatra", NAKSHATRAS),
            masa = masa(o, path) ?: RuleJson.fail("BAD_VALUE", "$path.masa"),
            system = system(o, path),
            kala = kala(o, path, KALAS),
            pick = pick(LUNAR_PICKS, RuleJson.field(o, "occurrence", path), "$path.occurrence"),
        )
    }

    @Suppress("SwallowedException") // the constructor message is static but the path is the useful part
    private fun fixedGregorian(o: JsonObject, path: String): FestivalRule.FixedGregorian {
        RuleJson.onlyKeys(o, setOf("type", "month", "day"), path)
        val month = RuleJson.intIn(RuleJson.field(o, "month", path), "$path.month", MONTHS)
        val day = RuleJson.intIn(RuleJson.field(o, "day", path), "$path.day", DAYS)
        return try {
            FestivalRule.FixedGregorian(month, day)
        } catch (e: IllegalArgumentException) {
            RuleJson.fail("OUT_OF_RANGE", "$path.day")
        }
    }
}
