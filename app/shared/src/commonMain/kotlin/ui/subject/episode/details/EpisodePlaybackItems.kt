// SPDX-License-Identifier: AGPL-3.0-only
package me.him188.ani.app.ui.subject.episode.details
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.LongClickProgressFill
import me.him188.ani.app.ui.foundation.icons.PlayingIcon
import me.him188.ani.app.ui.subject.episode.list.*

@Composable
fun EpisodePlaybackCard(
    sort: String, name: String, still: String?, isWatched: Boolean, isDone: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    playProgress: Float? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    // 播放中且有剧照时的描边宽度, 进度条按它内缩
    val stillBorder = if (still != null && isPlaying) 2.dp else 0.dp
    val sortColor = when {
        still != null -> EpisodeStillDefaults.contentColor
        isPlaying -> MaterialTheme.colorScheme.primary
        isWatched -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        else -> LocalContentColor.current
    }
    val nameColor = when {
        still != null -> EpisodeStillDefaults.secondaryContentColor
        isWatched -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) {
                MaterialTheme.colorScheme.secondaryContainer
            } else if (isWatched) {
                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
        border = if (still != null && isPlaying) BorderStroke(stillBorder, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier
            .height(72.dp)
            .aspectRatio(16f / 9)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Box(Modifier.fillMaxSize()) {
            if (still != null) {
                EpisodeStillBackground(
                    imageUrl = still,
                    highlighted = isPlaying,
                    modifier = Modifier.matchParentSize(),
                )
            }
            LongClickProgressFill(
                interactionSource = interactionSource,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                modifier = Modifier.matchParentSize(),
            )
            EpisodeCellLabel(
                sort = sort,
                name = name,
                sortColor = sortColor,
                nameColor = nameColor,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                playingIndicator = if (isPlaying) {
                    { PlayingIcon(width = 20.dp, height = 12.dp, color = sortColor) }
                } else {
                    null
                },
            )
            if (isDone) {
                EpisodeWatchedBadge(
                    onStill = still != null,
                    onClick = onLongClick,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                )
            } else if (playProgress != null) {
                EpisodePlayProgressBar(
                    progress = playProgress,
                    onStill = still != null,
                    // 播放中的有图卡片有 primary 描边, 进度条缩到描边内侧, 不被盖住
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = stillBorder, end = stillBorder, bottom = stillBorder),
                )
            }
        }
    }
}

@Composable
fun EpisodePlaybackListItem(
    sort: String, name: String,
    isPlaying: Boolean,
    isWatched: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = MaterialTheme.shapes.small
    val containerColor = when {
        isPlaying -> MaterialTheme.colorScheme.primaryContainer
        isWatched -> MaterialTheme.colorScheme.surfaceContainer
        else -> MaterialTheme.colorScheme.surfaceContainer
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(containerColor)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        LongClickProgressFill(
            interactionSource = interactionSource,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
            modifier = Modifier.matchParentSize(),
        )
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = {
                Text(
                    text = "$sort  $name",
                    color = when {
                        isPlaying -> MaterialTheme.colorScheme.primary
                        isWatched -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        else -> LocalContentColor.current
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            trailingContent = {
                if (isPlaying) {
                    PlayingIcon()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
