package app.parivaar.panchang.di

import android.content.Context
import app.parivaar.panchang.core.geo.JavaTimeOffsetResolver
import app.parivaar.panchang.core.geo.OffsetResolver
import app.parivaar.panchang.core.geo.PlaceSearch
import app.parivaar.panchang.core.geo.PlaceStores
import app.parivaar.panchang.core.model.geo.AmbiguityChoice
import app.parivaar.panchang.core.model.geo.ManualOffset
import app.parivaar.panchang.core.model.geo.TzContext
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import javax.inject.Singleton
import kotlin.time.Instant

/**
 * Both geo bindings are lazy: injecting them (even directly) does no disk work. The places DB copy and the timezone
 * overlay parse happen on the first call, which callers must make on `Dispatchers.IO`. Prefer injecting them as
 * `dagger.Lazy<PlaceSearch>` / `dagger.Lazy<OffsetResolver>` so even the wrapper is built on demand.
 */
@Module
@InstallIn(SingletonComponent::class)
object GeoModule {
    @Provides @Singleton
    fun providePlaceSearch(@ApplicationContext context: Context): PlaceSearch =
        LazyPlaceSearch { PlaceStores.create(context) }

    @Provides @Singleton
    fun provideOffsetResolver(@ApplicationContext context: Context): OffsetResolver =
        LazyOffsetResolver { JavaTimeOffsetResolver(PlaceStores.loadOverlay(context)) }
}

internal class LazyPlaceSearch(create: () -> PlaceSearch) : PlaceSearch {
    private val delegate by lazy(create)

    override fun search(query: String, limit: Int) = delegate.search(query, limit)

    override fun nearest(latDeg: Double, lonDeg: Double, maxKm: Double) = delegate.nearest(latDeg, lonDeg, maxKm)
}

internal class LazyOffsetResolver(create: () -> OffsetResolver) : OffsetResolver {
    private val delegate by lazy(create)

    override fun offsetAt(instant: Instant, ctx: TzContext, manual: ManualOffset?) =
        delegate.offsetAt(instant, ctx, manual)

    override fun civilDayOffset(date: LocalDate, ctx: TzContext, manual: ManualOffset?) =
        delegate.civilDayOffset(date, ctx, manual)

    override fun localToInstant(
        local: LocalDateTime,
        ctx: TzContext,
        choice: AmbiguityChoice,
        manual: ManualOffset?,
    ) = delegate.localToInstant(local, ctx, choice, manual)
}
