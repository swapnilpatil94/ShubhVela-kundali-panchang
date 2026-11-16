package app.parivaar.panchang.feature.kundli

import app.parivaar.panchang.core.geo.OffsetResolver
import app.parivaar.panchang.core.model.geo.AmbiguityChoice
import app.parivaar.panchang.core.model.geo.CivilDayOffset
import app.parivaar.panchang.core.model.geo.GeoPlace
import app.parivaar.panchang.core.model.geo.LocalResolution
import app.parivaar.panchang.core.model.geo.ManualOffset
import app.parivaar.panchang.core.model.geo.OffsetFlag
import app.parivaar.panchang.core.model.geo.OffsetSource
import app.parivaar.panchang.core.model.geo.ResolvedOffset
import app.parivaar.panchang.core.model.geo.TzContext
import app.parivaar.panchang.core.model.profile.BirthData
import app.parivaar.panchang.core.model.profile.PlaceSnapshot
import app.parivaar.panchang.core.model.profile.Profile
import app.parivaar.panchang.core.model.profile.ProfileOrigin
import app.parivaar.panchang.core.model.profile.RecordId
import app.parivaar.panchang.core.model.profile.Relation
import app.parivaar.panchang.engine.CalcSettings
import app.parivaar.panchang.engine.astro.AstronomyEngineEphemeris
import app.parivaar.panchang.engine.calendar.FamilyDateFinder
import app.parivaar.panchang.engine.jyotish.ChartCalculator
import app.parivaar.panchang.engine.jyotish.Gochar
import app.parivaar.panchang.engine.jyotish.Graha
import app.parivaar.panchang.engine.jyotish.NakshatraTable
import app.parivaar.panchang.engine.jyotish.Vargas
import app.parivaar.panchang.engine.jyotish.YogaRules
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Instant

/** Shared fixtures: synthetic profiles (no real person) and the real engine over Astronomy Engine. */
object KundliTestKit {
    private val root = File(checkNotNull(System.getProperty("pp.root.dir")))
    private fun rule(name: String) = File(root, "rules/$name").readText()

    val eph = AstronomyEngineEphemeris()

    fun direct(): DirectKundliEngine = DirectKundliEngine(
        ChartCalculator(eph), Vargas.parse(rule("vargas.json")), YogaRules.parse(rule("yogas.json")),
        NakshatraTable.parse(rule("nakshatra.json")), Gochar(eph), FinderFamilyDates(FamilyDateFinder(eph)),
    )

    val delhi = GeoPlace(1L, "Testpur", null, "ZZ", 28.61, 77.21, 216.0, 1L, "Asia/Kolkata", null)
    val ist = ResolvedOffset(19_800, OffsetSource.IANA, emptySet(), null)

    fun id(n: Int) = RecordId(n.toString(16).padStart(RecordId.LENGTH, '0'))

    fun profile(
        n: Int,
        instant: Instant? = Instant.parse("1990-05-17T01:00:00Z"),
        calc: CalcSettings = CalcSettings(),
        offset: ResolvedOffset = ist,
        relation: Relation = Relation.PARENT,
    ): Profile {
        val t0 = Instant.parse("2024-01-01T00:00:00Z")
        val time = if (instant == null) null else LocalTime(6, 30)
        return Profile(
            id(n), "Synthetic $n", relation, ProfileOrigin.MANUAL_OTHER,
            BirthData(LocalDate(1990, 5, 17), time, time != null),
            PlaceSnapshot(delhi, offset, null, AmbiguityChoice.EARLIER, instant),
            calc, null, t0, t0,
        )
    }

    /** Counts engine calls and records the thread they ran on. */
    class CountingEngine(private val inner: KundliEngine) : KundliEngine by inner {
        val bundles = AtomicInteger()

        @Volatile var lastThread: String? = null

        override fun bundle(r: ChartRequest): KundliBundle {
            bundles.incrementAndGet()
            lastThread = Thread.currentThread().name
            return inner.bundle(r)
        }

        override fun dasha(r: ChartRequest, path: List<Graha>) = inner.dasha(r, path)
    }

    /** Fixed-offset resolver with programmable flags (gap/ambiguous) for editor tests. */
    class FakeResolver(var flags: Set<OffsetFlag> = emptySet(), private val seconds: Int = 19_800) : OffsetResolver {
        private fun off(extra: Int = 0) = ResolvedOffset(seconds + extra, OffsetSource.IANA, flags, null)

        override fun offsetAt(instant: Instant, ctx: TzContext, manual: ManualOffset?) = off()

        override fun civilDayOffset(
            date: LocalDate, ctx: TzContext, manual: ManualOffset?,
        ) = CivilDayOffset.Valid(off())

        override fun localToInstant(
            local: LocalDateTime, ctx: TzContext, choice: AmbiguityChoice, manual: ManualOffset?,
        ): LocalResolution {
            val extra = if (OffsetFlag.AMBIGUOUS in flags && choice == AmbiguityChoice.LATER) -3600 else 0
            val o = off(extra)
            return LocalResolution(
                local.toInstant(TimeZone.UTC) - kotlin.time.Duration.parse("${o.offsetSeconds}s"), o, null,
            )
        }
    }
}
