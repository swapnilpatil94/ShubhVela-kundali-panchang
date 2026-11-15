package app.parivaar.panchang.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import app.parivaar.panchang.core.designsystem.surfaces.GlassSurface
import app.parivaar.panchang.core.designsystem.surfaces.LocalGlassBackdrop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import app.parivaar.panchang.core.designsystem.R
import app.parivaar.panchang.core.designsystem.PpTheme
import app.parivaar.panchang.core.designsystem.tokens.PpSpace
import app.parivaar.panchang.core.designsystem.tokens.PpType

@Composable
fun PpTopBar(
    title: String,
    subtitle: String?,
    avatarInitials: String?,
    onAvatar: () -> Unit,
    onSearch: () -> Unit,
) {
    // Not chrome: nothing scrolls under it, so a plain header (glass is for chrome only).
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(start = PpSpace.screen, end = PpSpace.s, top = PpSpace.s, bottom = PpSpace.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PpSpace.xs),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title, style = PpType.displayM, color = PpTheme.ratna.text,
                modifier = Modifier.semantics { heading() },
            )
            if (subtitle != null) {
                Text(
                    subtitle, style = PpType.meta, color = PpTheme.ratna.text2,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onSearch) {
            Icon(painterResource(R.drawable.ic_search), contentDescription = stringResource(R.string.cd_search))
        }
        val menuLabel = stringResource(R.string.cd_profile_menu)
        Box(
            modifier = Modifier.size(PpSpace.minTouchTarget)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                .clickable(onClickLabel = menuLabel, role = Role.Button, onClick = onAvatar)
                .semantics { contentDescription = menuLabel },
            contentAlignment = Alignment.Center,
        ) {
            if (avatarInitials.isNullOrBlank()) {
                Icon(painterResource(R.drawable.ic_settings), contentDescription = null)
            } else {
                Text(avatarInitials, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            }
        }
    }
}

