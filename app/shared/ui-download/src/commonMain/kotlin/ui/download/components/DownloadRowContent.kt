/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.download.components

import me.him188.ani.app.ui.foundation.animation.AniAnimatedVisibility
import androidx.compose.runtime.getValue
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.him188.ani.app.tools.getOrZero
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.cache_episode_download_failed
import me.him188.ani.app.ui.lang.cache_episode_status_paused
import org.jetbrains.compose.resources.stringResource

@Composable
fun DownloadRowContent(
    episode: DownloadItem,
    title: String,
    supportingText: String,
    selectionMode: Boolean,
    selected: Boolean,
    onToggleSelected: () -> Unit,
    onEnterSelection: () -> Unit,
    onShowMenu: () -> Unit,
    primaryAction: @Composable () -> Unit,
    moreActions: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
) {
    val containerColor by animateColorAsState(
        if (selectionMode && selected) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent,
    )
    Surface(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(
                onClick = {
                    if (selectionMode) {
                        onToggleSelected()
                    } else {
                        onShowMenu()
                    }
                },
                onLongClick = onEnterSelection,
            ),
        shape = shape,
        color = containerColor,
    ) {
        Column(
            Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 设计稿: 多选模式下复选框在行首, 行尾单项操作隐藏.
                if (selectionMode) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { onToggleSelected() },
                    )
                }

                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        supportingText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (!selectionMode) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        primaryAction()
                        Box { moreActions() }
                    }
                }
            }

            AniAnimatedVisibility(!episode.isFinished) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val progress by animateFloatAsState(episode.progress.getOrZero())
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.weight(1f),
                        strokeCap = StrokeCap.Round,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val statusText = when {
                            episode.isFailed -> stringResource(Lang.cache_episode_download_failed)
                            episode.isPaused -> stringResource(Lang.cache_episode_status_paused)
                            else -> episode.speedText
                        }
                        statusText?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (episode.isFailed) MaterialTheme.colorScheme.error else Color.Unspecified,
                            )
                        }
                        episode.progressText?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
                    }
                }
            }
        }
    }
}
