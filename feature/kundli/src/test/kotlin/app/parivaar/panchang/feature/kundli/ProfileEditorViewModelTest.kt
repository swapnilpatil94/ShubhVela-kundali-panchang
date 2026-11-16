package app.parivaar.panchang.feature.kundli

import app.parivaar.panchang.core.data.DataResult
import app.parivaar.panchang.core.data.ProfileRepository
import app.parivaar.panchang.core.data.Records
import app.parivaar.panchang.core.geo.PlaceSearch
import app.parivaar.panchang.core.model.geo.GeoPlace
import app.parivaar.panchang.core.model.geo.OffsetFlag
import app.parivaar.panchang.core.model.geo.PlaceHit
import app.parivaar.panchang.core.model.profile.Profile
import app.parivaar.panchang.core.model.profile.RecordId
import app.parivaar.panchang.engine.CalcSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class ProfileEditorViewModelTest {
    private class FakeRepo : ProfileRepository {
        val saved = ArrayList<Profile>()
        val deleted = ArrayList<RecordId>()
        override fun observeAll(): Flow<Records<Profile>> = emptyFlow()
        override suspend fun get(id: RecordId): DataResult<Profile> = DataResult.Ok(saved.first { it.id == id })
        override suspend fun upsert(p: Profile): DataResult<Unit> = DataResult.Ok(Unit).also { saved += p }
        override suspend fun delete(id: RecordId): DataResult<Unit> = DataResult.Ok(Unit).also { deleted += id }
        override suspend fun reorder(ids: List<RecordId>): DataResult<Unit> = DataResult.Ok(Unit)
    }

    private val search = object : PlaceSearch {
        override fun search(query: String, limit: Int) = listOf(PlaceHit(KundliTestKit.delhi, query, 0))
        override fun nearest(latDeg: Double, lonDeg: Double, maxKm: Double): GeoPlace? = null
    }

    private fun vm(
        resolver: KundliTestKit.FakeResolver, repo: FakeRepo = FakeRepo(), onDeleted: (Profile) -> Unit = {},
    ) =
        ProfileEditorViewModel(
            repo, search, resolver, { CalcSettings() }, onDeleted, Clock.System,
            CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined,
        )

    private fun ProfileEditorViewModel.fill(time: String = "06:30", unknown: Boolean = false) {
        start(null)
        onName("Synthetic")
        onDate("1990-05-17")
        onTimeUnknown(unknown)
        if (!unknown) onTime(time)
        onPick(KundliTestKit.delhi)
    }

    @Test fun gapTimeBlocksSave() {
        val v = vm(KundliTestKit.FakeResolver(setOf(OffsetFlag.GAP_SHIFTED)))
        v.fill()
        assertTrue(v.state.value.gap)
        assertTrue(EditorError.GAP_TIME in v.state.value.errors)
        assertNull(v.build())
    }

    @Test fun ambiguousShowsTwoChoices() {
        val v = vm(KundliTestKit.FakeResolver(setOf(OffsetFlag.AMBIGUOUS)))
        v.fill()
        val s = v.state.value
        assertTrue(s.ambiguous)
        assertNotNull(s.resolved?.earlierSeconds)
        assertNotNull(s.resolved?.laterSeconds)
        assertTrue(s.resolved!!.earlierSeconds != s.resolved!!.laterSeconds)
    }

    @Test fun unknownTimeSavesNullInstant() {
        val repo = FakeRepo()
        val v = vm(KundliTestKit.FakeResolver(), repo)
        v.fill(unknown = true)
        v.save()
        val p = repo.saved.single()
        assertNull(p.place.birthInstant)
        assertNull(p.birth.time)
        assertTrue(v.state.value.done)
    }

    @Test fun deleteEvictsCache() {
        val repo = FakeRepo()
        val evicted = ArrayList<RecordId>()
        val cache = KundliCache<String>()
        val p = KundliTestKit.profile(7)
        cache.put(BundleKey(p.id, 0, 0, false, null), "b")
        val v = vm(KundliTestKit.FakeResolver(), repo) { evicted += it.id; cache.evict(it.id) }
        v.start(p)
        v.delete()
        assertEquals(listOf(p.id), repo.deleted)
        assertEquals(listOf(p.id), evicted)
        assertEquals(0, cache.size())
    }

    @Test fun passingTimeIsKeptOnResave() {
        val repo = FakeRepo()
        val v = vm(KundliTestKit.FakeResolver(), repo)
        val p = KundliTestKit.profile(8).copy(
            passing = app.parivaar.panchang.core.model.profile.PassingData(
                kotlinx.datetime.LocalDate(2015, 3, 10), kotlinx.datetime.LocalTime(21, 15),
            ),
        )
        v.start(p)
        assertEquals("21:15", v.state.value.passingTimeText)
        v.save()
        assertEquals(p.passing, repo.saved.single().passing)
    }
}
