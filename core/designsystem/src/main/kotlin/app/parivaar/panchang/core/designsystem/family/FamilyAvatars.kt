package app.parivaar.panchang.core.designsystem.family

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.parivaar.panchang.core.designsystem.PpTheme
import app.parivaar.panchang.core.designsystem.R
import app.parivaar.panchang.core.designsystem.tokens.Gem
import app.parivaar.panchang.core.designsystem.tokens.PpSpace
import app.parivaar.panchang.core.designsystem.tokens.PpType

private val AVATAR = 56.dp

/** Relative luminance above which a gem fill takes dark initials. */
private const val LIGHT_FILL = 0.45f
private const val INK_ON_LIGHT = 0xFF14110D

/** Profile switcher row (DESIGN_V2 `FamilyAvatars`, PNG 16): gem discs with initials, the selected one in gold. */
@Composable
fun FamilyAvatars(
    avatars: List<FamilyAvatar>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val r = PpTheme.ratna
    Row(
        modifier.horizontalScroll(rememberScrollState()).padding(vertical = PpSpace.xs),
        horizontalArrangement = Arrangement.spacedBy(PpSpace.m),
    ) {
        for (a in avatars) {
            val selected = a.key == selectedKey
            val cdRes = if (selected) R.string.family_cd_avatar_selected else R.string.family_cd_avatar
            val cd = stringResource(cdRes, a.label)
            Column(
                Modifier.width(AVATAR + PpSpace.s).clickable(role = Role.Tab) { onSelect(a.key) }
                    .semantics(mergeDescendants = true) { contentDescription = cd },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val fill = Color(Gem.entries[a.tone.mod(Gem.entries.size)].argb)
                Box(
                    Modifier.size(AVATAR)
                        .border(if (selected) 2.dp else 1.dp, if (selected) r.goldFill else r.line, CircleShape)
                        .padding(3.dp)
                        .background(fill, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(a.initials, style = PpType.label, color = onGem(fill))
                }
                Text(
                    a.label, style = PpType.meta, color = if (selected) r.text else r.text2,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Dark or light initials, whichever reads better on the gem fill. */
private fun onGem(c: Color): Color {
    return if (c.luminance() > LIGHT_FILL) Color(INK_ON_LIGHT) else Color.White
}
