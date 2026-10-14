// SPDX-License-Identifier: AGPL-3.0-only
package me.him188.ani.app.ui.subject.episode.list
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.FilledTonalCombinedClickButton

@Composable
fun EpisodeSortSquareButtonContent(text: String, containerColor: Color, onClick: () -> Unit,
    onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalCombinedClickButton(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp),
        shape = MaterialTheme.shapes.small,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        colors = ButtonDefaults.elevatedButtonColors(
            containerColor = containerColor,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}


/** Shared native/browser state-to-color presentation, including unpublished episodes. */
@Composable
fun EpisodeStateButton(text: String, isDoneOrDropped: Boolean, isBroadcast: Boolean,
    onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier,
    colors: EpisodeListColors = EpisodeListDefaults.colors()) {
    EpisodeSortSquareButtonContent(text, colors.containerColor(isDoneOrDropped,isBroadcast), onClick, onLongClick, modifier)
}
