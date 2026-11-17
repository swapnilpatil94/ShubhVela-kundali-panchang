@file:Suppress("MatchingDeclarationName")

package app.parivaar.panchang.shell

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.parivaar.panchang.core.data.BondRepository
import app.parivaar.panchang.core.data.DataResult
import app.parivaar.panchang.core.model.bond.Bond
import app.parivaar.panchang.core.model.profile.RecordId
import app.parivaar.panchang.feature.bonds.BondStore
import app.parivaar.panchang.feature.bonds.BondsEvent
import app.parivaar.panchang.feature.bonds.BondsPage
import app.parivaar.panchang.feature.bonds.BondsTab
import app.parivaar.panchang.feature.bonds.BondsViewModel

/** The encrypted bond repository as the Bonds store (rows stay sealed by `BondRepositoryImpl`). */
class RepoBondStore(private val repo: BondRepository) : BondStore {
    override suspend fun save(b: Bond): Boolean = repo.upsert(b) is DataResult.Ok

    override suspend fun delete(id: RecordId): Boolean = repo.delete(id) is DataResult.Ok
}

/** Bonds tab host: system back walks the tab's pages back to the list. No ads (M12). */
@Composable
fun BondsTabHost(vm: BondsViewModel, subtitle: String?, onAvatar: () -> Unit, onSearch: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    BackHandler(enabled = state.page != BondsPage.LIST) { vm.onEvent(BondsEvent.Back) }
    BondsTab(state, vm::onEvent, subtitle, null, onAvatar, onSearch)
}
