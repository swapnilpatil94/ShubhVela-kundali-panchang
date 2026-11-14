package app.parivaar.panchang.di

import android.content.Context
import app.parivaar.panchang.core.geo.JavaTimeOffsetResolver
import app.parivaar.panchang.core.geo.OffsetResolver
import app.parivaar.panchang.core.geo.PlaceSearch
import app.parivaar.panchang.core.geo.PlaceStores
import app.parivaar.panchang.core.geo.internal.TzOverlay
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object GeoModule {
    @Provides @Singleton
    fun providePlaceSearch(@ApplicationContext context: Context): PlaceSearch = PlaceStores.create(context)

    @Provides @Singleton
    fun provideTzOverlay(@ApplicationContext context: Context): TzOverlay = PlaceStores.loadOverlay(context)

    @Provides @Singleton
    fun provideOffsetResolver(overlay: TzOverlay): OffsetResolver = JavaTimeOffsetResolver(overlay)
}
