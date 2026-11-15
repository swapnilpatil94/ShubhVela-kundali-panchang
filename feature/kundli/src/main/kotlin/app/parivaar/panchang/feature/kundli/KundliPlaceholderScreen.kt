package app.parivaar.panchang.feature.kundli

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.parivaar.panchang.core.designsystem.components.EmptyState
import app.parivaar.panchang.core.designsystem.components.PpTopBar
import app.parivaar.panchang.core.designsystem.R as DsR

/** Placeholder tab root: a top bar plus a "Coming soon" state. No sample or fake data. */
@Composable
fun KundliPlaceholderScreen(
    subtitle: String?,
    avatarInitials: String?,
    onAvatar: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        PpTopBar(stringResource(DsR.string.tab_kundli), subtitle, avatarInitials, onAvatar, onSearch)
        EmptyState(stringResource(R.string.kundli_placeholder_title), stringResource(R.string.kundli_placeholder_body))
    }
}
