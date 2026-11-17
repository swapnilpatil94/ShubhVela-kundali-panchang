package app.parivaar.panchang.feature.bonds

import app.parivaar.panchang.core.model.bond.Bond
import app.parivaar.panchang.core.model.bond.BondTraits
import app.parivaar.panchang.core.model.geo.AmbiguityChoice
import app.parivaar.panchang.core.model.geo.GeoPlace
import app.parivaar.panchang.core.model.geo.OffsetSource
import app.parivaar.panchang.core.model.geo.ResolvedOffset
import app.parivaar.panchang.core.model.profile.BirthData
import app.parivaar.panchang.core.model.profile.PlaceSnapshot
import app.parivaar.panchang.core.model.profile.Profile
import app.parivaar.panchang.core.model.profile.ProfileOrigin
import app.parivaar.panchang.core.model.profile.RecordId
import app.parivaar.panchang.core.model.profile.Relation
import app.parivaar.panchang.engine.CalcSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Clock
import kotlin.time.Instant

/** Synthetic fixtures (no real person) and fakes for the Bonds view model. */
object BondsTestKit {
    private val place = GeoPlace(1L, "Testpur", null, "ZZ", 18.52, 73.86, 560.0, 1L, "Asia/Kolkata", null)
    private val ist = ResolvedOffset(19_800, OffsetSource.IANA, emptySet(), null)
    val t0: Instant = Instant.parse("2024-01-01T00:00:00Z")
    val today = LocalDate(2026, 11, 16)

    fun id(n: Int) = RecordId(n.toString(16).padStart(RecordId.LENGTH, '0'))

    fun profile(
        n: Int,
        name: String = "Synthetic $n",
        relation: Relation = Relation.FRIEND,
        origin: ProfileOrigin = ProfileOrigin.OWN,
        birth: LocalDate = LocalDate(1990, 4, 12),
    ) = Profile(
        id(n), name, relation, origin, BirthData(birth, LocalTime(14, 35), true),
        PlaceSnapshot(place, ist, null, AmbiguityChoice.EARLIER, t0), CalcSettings(), null, t0, t0,
    )

    fun traits(name: String = "Zoya", lagna: Int? = 4, vashya: Int? = 1) =
        BondTraits(name, 12, 1, 5, lagna, 1, 3, 0, null, null, null, vashya)

    fun bond(n: Int, ref: RecordId? = null) = Bond(id(n), traits("B$n"), "bestie", t0, ref)

    val outcome = MatchOutcome(
        listOf(KootaLine("VARNA", 4, 4, emptyList(), false), KootaLine("NADI", 0, 32, listOf("koota.nadi.same"), true)),
        totalQ = 4, maxQ = 4, percent = 100, labelKey = "bond.label.cosmic_twins",
        axes = listOf("VIBE" to 9, "FIGHTS" to 4), tableStatus = "VERIFY",
    )

    class FakeEngine(var available: Boolean = true) : BondEngine {
        val decoded = mutableListOf<String>()
        var encodedWith: BondTraits? = null

        override fun match(a: BondTraits, b: BondTraits, kind: BondKind,
            roles: MatchRoles?) = outcome.takeIf { available }

        override fun encode(t: BondTraits): String? {
            encodedWith = t
            return if (available) "AQwhBQ" else null
        }

        override fun decode(input: String): BondTraits? {
            decoded += input
            return if (input == "GOODCODE" || input == "https://shubhvela.app/b#GOODCODE") traits() else null
        }

        override fun traitsOf(p: Profile, name: String) = traits(name)
    }

    class FakeStore : BondStore {
        val saved = mutableListOf<Bond>()
        val saves = AtomicInteger()

        override suspend fun save(b: Bond): Boolean {
            saves.incrementAndGet()
            saved.removeAll { it.id == b.id }
            saved += b
            return true
        }

        override suspend fun delete(id: RecordId): Boolean = saved.removeAll { it.id == id }
    }

    class Harness(data: BondsData?) {
        val flow = MutableStateFlow(data)
        val engine = FakeEngine()
        val store = FakeStore()
        private val ids = AtomicInteger(100)
        val vm = BondsViewModel(
            flow, store, engine, { id(ids.incrementAndGet()) }, { today }, Clock.System,
            CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined,
        )
        val s get() = vm.state.value
    }
}
