package app.parivaar.panchang.platform.engine

import app.parivaar.panchang.core.geo.OffsetResolver
import app.parivaar.panchang.core.geo.toEnginePlace
import app.parivaar.panchang.core.model.bond.BondTraits
import app.parivaar.panchang.core.model.geo.AmbiguityChoice
import app.parivaar.panchang.core.model.geo.tzContext
import app.parivaar.panchang.core.model.profile.Profile
import app.parivaar.panchang.engine.jyotish.Graha
import app.parivaar.panchang.engine.jyotish.NakshatraTable
import app.parivaar.panchang.engine.match.AshtakootaCalculator
import app.parivaar.panchang.engine.match.BondAxis
import app.parivaar.panchang.engine.match.BondAxisScorer
import app.parivaar.panchang.engine.match.BondType
import app.parivaar.panchang.engine.match.KootaTables
import app.parivaar.panchang.engine.match.MoonPoint
import app.parivaar.panchang.engine.match.VashyaClass
import app.parivaar.panchang.engine.match.manglikLevel
import app.parivaar.panchang.engine.match.vashyaOf
import app.parivaar.panchang.feature.bonds.BondEngine
import app.parivaar.panchang.feature.bonds.BondKind
import app.parivaar.panchang.feature.bonds.KootaLine
import app.parivaar.panchang.feature.bonds.MatchOutcome
import app.parivaar.panchang.feature.bonds.MatchRoles
import app.parivaar.panchang.feature.kundli.ChartRequest
import app.parivaar.panchang.feature.kundli.KundliEngine
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.time.Clock

/**
 * The Bonds port over the shared engine (M12): koota tables from `rules/koota.json`, axis weights from
 * `content/bond_weights.json`, traits from the Kundli engine (under the one engine lock). Blocking; call off the main
 * thread (the shared engine is built on first use, never here). Nothing is logged. Bond Code encode/decode are
 * wired to [codec] (null when no codec is given).
 */
class EngineBondEngine(
    private val kundliOf: () -> KundliEngine,
    private val offsets: OffsetResolver,
    private val asset: (String) -> String,
    private val lockOf: () -> Any,
    private val codec: BondCodePort?,
    private val clock: Clock = Clock.System,
) : BondEngine {
    /** The Bond Code codec (core/share, CRITICAL). Implementations return null on any rejection. */
    interface BondCodePort {
        fun encode(t: BondTraits): String?

        fun decode(input: String): BondTraits?
    }

    private class Tables(val calc: AshtakootaCalculator, val scorer: BondAxisScorer)

    private val tables: Tables? by lazy {
        runCatching {
            val n = NakshatraTable.parse(asset("rules/nakshatra.json"))
            Tables(
                AshtakootaCalculator(KootaTables.parse(asset("rules/koota.json")), n),
                BondAxisScorer(asset("content/bond_weights.json")),
            )
        }.getOrNull()
    }

    @Suppress("ReturnCount")
    override fun match(a: BondTraits, b: BondTraits, kind: BondKind, roles: MatchRoles?): MatchOutcome? {
        val t = tables ?: return null
        val pa = point(a) ?: return null
        val pb = point(b) ?: return null
        return synchronized(lockOf()) {
            val r = when (roles) {
                null -> t.calc.matchSymmetric(pa, pb)
                MatchRoles.A_GROOM -> t.calc.match(pa, pb)
                MatchRoles.B_GROOM -> t.calc.match(pb, pa)
            }
            val score = if (roles == null) t.scorer.score(BondType.valueOf(kind.name), r) else null
            val excluded = score?.excluded.orEmpty()
            val lines = r.scores.map { KootaLine(it.koota.name, it.q, it.koota.maxQ, it.noteIds, it.koota in excluded) }
            val counted = lines.filterNot { it.excluded }
            MatchOutcome(
                kootas = lines,
                totalQ = counted.sumOf { it.q },
                maxQ = counted.sumOf { it.maxQ },
                percent = score?.percent,
                labelKey = score?.labelKey,
                axes = score?.let { s -> BondAxis.entries.map { it.name to (s.axes[it] ?: 0) } }.orEmpty(),
                tableStatus = r.tableStatus,
            )
        }
    }

    override fun encode(t: BondTraits): String? = codec?.encode(t)

    override fun decode(input: String): BondTraits? = codec?.decode(input)

    override fun traitsOf(p: Profile, name: String): BondTraits? = runCatching {
        val noon = p.place.birthInstant == null
        val instant = p.place.birthInstant ?: offsets.localToInstant(
            LocalDateTime(p.birth.date, LocalTime(NOON, 0)), p.place.place.tzContext(), AmbiguityChoice.EARLIER,
            p.place.manualOffset,
        ).instant
        val req = ChartRequest(p.id, p.updatedAt, instant, noon, p.place.place.toEnginePlace(), p.calc, clock.now())
        val f = kundliOf().bundle(req).facts
        val moon = f.of(Graha.MOON)
        BondTraits(
            name = name,
            moonNakshatra = moon.nakshatra,
            moonPada = moon.pada,
            moonRashi = moon.rashi.ordinal,
            lagna = if (noon) null else f.lagna.ordinal,
            manglik = synchronized(lockOf()) { manglikLevel(f, lagnaKnown = !noon) },
            venusSign = f.of(Graha.VENUS).rashi.ordinal,
            sunSign = f.of(Graha.SUN).rashi.ordinal,
            gana = null,
            yoni = null,
            nadi = null,
            vashya = vashyaOf(moon.lon).ordinal,
        )
    }.getOrNull()

    /** Stored traits as a Moon point; a pre-M12 row without vashya is not matched (never guessed). */
    private fun point(t: BondTraits): MoonPoint? {
        val v = t.vashya ?: return null
        return MoonPoint(t.moonNakshatra, t.moonPada, VashyaClass.entries[v])
    }

    private companion object {
        const val NOON = 12
    }
}
