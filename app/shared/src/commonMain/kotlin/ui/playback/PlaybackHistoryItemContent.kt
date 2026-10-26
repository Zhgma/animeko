package me.him188.ani.app.ui.playback

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.him188.ani.app.tools.formatDateTime
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.lang.*
import org.jetbrains.compose.resources.stringResource
import kotlin.math.floor

@Immutable
data class PlaybackHistoryUiItem(
    val episodeId: Int,
    val subjectId: Int?,
    val episodeSort: Float?,
    val subjectName: String?,
    val subjectImageUrl: String?,
    val episodeName: String?,
    val positionMillis: Long,
    val durationMillis: Long?,
    val updatedAtMillis: Long,
    val progressLabel: String? = null,
)


@Composable
fun PlaybackHistoryListItem(
    item: PlaybackHistoryUiItem,
    selected: Boolean,
    selectionMode: Boolean,
    topAppBarContainerColor: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingContent: @Composable () -> Unit = {},
) {
    val subjectName = item.subjectName?.takeIf { it.isNotBlank() }
        ?: stringResource(Lang.playback_history_unknown_subject)
    val coverContentDescription = stringResource(Lang.playback_history_cover, subjectName)

    Surface(
        modifier
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .testTag("${PlaybackHistoryTestTags.ITEM_PREFIX}${item.episodeId}"),
        shape = MaterialTheme.shapes.large,
        tonalElevation = if (selected) 6.dp else 1.dp,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else topAppBarContainerColor,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlaybackHistoryCover(
                imageUrl = item.subjectImageUrl,
                contentDescription = coverContentDescription,
                modifier = Modifier.size(width = 72.dp, height = 96.dp),
            )
            Column(
                Modifier.weight(1f).animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    subjectName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag(PlaybackHistoryTestTags.SUBJECT_NAME),
                )
                Text(
                    episodeText(item),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag(PlaybackHistoryTestTags.EPISODE_NAME),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (item.updatedAtMillis > 0) formatDateTime(item.updatedAtMillis) else "历史阅读位置",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.testTag(PlaybackHistoryTestTags.DATE),
                    )
                    Text(
                        item.progressLabel ?: progressText(item),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.testTag(PlaybackHistoryTestTags.PROGRESS_TEXT),
                    )
                }
                val progress = item.durationMillis?.takeIf { it > 0L }?.let {
                    item.positionMillis.toFloat() / it
                }
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.testTag("${PlaybackHistoryTestTags.CHECKBOX_PREFIX}${item.episodeId}"),
                )
            }
            trailingContent()
        }
    }
}

@Composable
private fun PlaybackHistoryCover(
    imageUrl: String?,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    if (imageUrl.isNullOrBlank()) {
        Box(
            modifier
                .aspectRatio(3f / 4f)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.History,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        AsyncImage(
            model = imageUrl,
            contentDescription = contentDescription,
            modifier = modifier
                .aspectRatio(3f / 4f)
                .clip(MaterialTheme.shapes.medium),
            contentScale = ContentScale.Crop,
        )
    }
}


@Composable
private fun episodeText(item: PlaybackHistoryUiItem): String {
    val episodeLabel = item.episodeSort?.let { sort ->
        stringResource(Lang.playback_history_episode_label, sort.formatEpisodeSort())
    }
    val episodeName = item.episodeName?.takeIf { it.isNotBlank() }
        ?: stringResource(Lang.playback_history_unknown_episode)
    return if (episodeLabel == null) episodeName else "$episodeLabel · $episodeName"
}

@Composable
private fun progressText(item: PlaybackHistoryUiItem): String {
    val position = item.positionMillis.formatDuration()
    val duration = item.durationMillis?.takeIf { it > 0L }?.formatDuration()
        ?: stringResource(Lang.playback_history_progress_unknown_duration)
    return "$position / $duration"
}


private fun Float.formatEpisodeSort(): String {
    return if (this == floor(this)) this.toInt().toString() else toString()
}

private fun Long.formatDuration(): String {
    val totalSeconds = (this / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = totalSeconds % 3600 / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "${hours}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "${minutes}:${seconds.toString().padStart(2, '0')}"
    }
}

object PlaybackHistoryTestTags {
    const val LIST = "playbackHistoryList"
    const val ITEM_PREFIX = "playbackHistoryItem-"
    const val CHECKBOX_PREFIX = "playbackHistoryCheckbox-"
    const val SYNC_PENDING_LIST = "playbackHistorySyncPendingList"
    const val SYNC_PENDING_ITEM_PREFIX = "playbackHistorySyncPendingItem-"
    const val SUBJECT_NAME = "playbackHistorySubjectName"
    const val EPISODE_NAME = "playbackHistoryEpisodeName"
    const val DATE = "playbackHistoryDate"
    const val PROGRESS_TEXT = "playbackHistoryProgressText"
}

