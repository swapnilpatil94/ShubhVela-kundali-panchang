package app.parivaar.panchang.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.delay
import app.parivaar.panchang.core.designsystem.R
import app.parivaar.panchang.core.designsystem.tokens.LocalReducedMotion
import app.parivaar.panchang.core.designsystem.tokens.PpSpace

private const val PROGRESS_DELAY_MS = 300L

@Composable
private fun StateColumn(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier.fillMaxWidth().padding(PpSpace.xxl).semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PpSpace.m),
    ) { content() }
}

@Composable
private fun TitleBody(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
    Text(
        body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: Pair<String, () -> Unit>? = null,
) {
    StateColumn(modifier) {
        TitleBody(title, body)
        action?.let { PpButton(it.first, it.second, PpButtonStyle.Tonal) }
    }
}

/** The progress indicator appears only after 300 ms, so quick loads do not flash. */
@Composable
fun LoadingState(label: String, modifier: Modifier = Modifier) {
    var showProgress by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(PROGRESS_DELAY_MS)
        showProgress = true
    }
    val reduced = LocalReducedMotion.current
    val progressName = stringResource(R.string.loading_progress)
    StateColumn(modifier) {
        if (showProgress && !reduced) {
            CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = progressName })
        }
        Text(label, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}

@Composable
fun ErrorState(
    title: String,
    body: String,
    actions: List<Pair<String, () -> Unit>>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(PpSpace.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PpSpace.m),
    ) {
        Column(
            Modifier.semantics(mergeDescendants = true) {},
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PpSpace.m),
        ) { TitleBody(title, body) }
        actions.forEachIndexed { i, a ->
            PpButton(a.first, a.second, if (i == 0) PpButtonStyle.Primary else PpButtonStyle.Outline)
        }
    }
}
