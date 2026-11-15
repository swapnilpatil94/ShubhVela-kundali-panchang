@file:Suppress("MaxLineLength")

package app.parivaar.panchang.feature.calendar.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import app.parivaar.panchang.core.designsystem.PpTheme
import app.parivaar.panchang.core.designsystem.tokens.PpType
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.parivaar.panchang.core.designsystem.tokens.PpSpace

/** Shared shell of the B4 screens: a back action, a heading, then scrolling content. No ads (timing screens). */
@Composable
internal fun ScreenFrame(
    title: String,
    backLabel: String,
    onBack: () -> Unit,
    eyebrow: String? = null,
    content: @Composable () -> Unit,
) {
    val r = PpTheme.ratna
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(r.bg0, r.bg1)))) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = PpSpace.s, vertical = PpSpace.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = PpSpace.minTouchTarget)) { Text(backLabel, color = r.text2) }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = PpSpace.l),
            verticalArrangement = Arrangement.spacedBy(PpSpace.m),
        ) {
            if (eyebrow != null) Text(eyebrow, style = PpType.eyebrow, color = r.gold)
            Text(title, style = PpType.displayL, color = r.text, modifier = Modifier.semantics { heading() })
            content()
            Spacer(Modifier.height(PpSpace.xxl))
        }
    }
}
