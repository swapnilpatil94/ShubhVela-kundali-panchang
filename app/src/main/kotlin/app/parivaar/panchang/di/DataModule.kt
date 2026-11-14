package app.parivaar.panchang.di

import android.content.Context
import app.parivaar.panchang.core.data.BondRepository
import app.parivaar.panchang.core.data.DataGraph
import app.parivaar.panchang.core.data.DataVault
import app.parivaar.panchang.core.data.DataWiper
import app.parivaar.panchang.core.data.FamilyTransfer
import app.parivaar.panchang.core.data.PlaceRepository
import app.parivaar.panchang.core.data.ProfileRepository
import app.parivaar.panchang.core.data.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Building the graph does no disk or Keystore work; the vault opens only when `DataGraph.start()` is launched from
 * a background scope, so first injection never blocks the main thread.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides @Singleton
    fun provideDataGraph(@ApplicationContext context: Context): DataGraph = DataGraph.create(context)

    @Provides fun provideDataVault(g: DataGraph): DataVault = g.vault

    @Provides fun provideProfileRepository(g: DataGraph): ProfileRepository = g.profiles

    @Provides fun providePlaceRepository(g: DataGraph): PlaceRepository = g.places

    @Provides fun provideBondRepository(g: DataGraph): BondRepository = g.bonds

    @Provides fun provideSettingsRepository(g: DataGraph): SettingsRepository = g.settings

    @Provides fun provideFamilyTransfer(g: DataGraph): FamilyTransfer = g.transfer

    @Provides fun provideDataWiper(g: DataGraph): DataWiper = g.wiper
}
