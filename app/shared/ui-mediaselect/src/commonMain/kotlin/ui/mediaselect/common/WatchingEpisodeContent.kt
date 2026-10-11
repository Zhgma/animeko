/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.mediaselect.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.media_selector_episode_label
import me.him188.ani.app.ui.lang.media_selector_episode_label_with_ep
import me.him188.ani.app.ui.lang.media_selector_mode_auto
import me.him188.ani.app.ui.lang.media_selector_mode_bt
import me.him188.ani.app.ui.lang.media_selector_mode_manual
import me.him188.ani.app.ui.lang.media_selector_mode_menu
import me.him188.ani.app.ui.lang.media_selector_watching_prefix
import me.him188.ani.app.ui.mediaselect.WatchingEpisode
import org.jetbrains.compose.resources.stringResource


/**
 * surfaceContainer 圆角 12 卡片, 左侧图标, 文本「正在观看 <b>第 25 话 OVA</b>」(前缀 onSurfaceVariant, 集号 + 名 onSurface 加粗; 集号见 [episodeLabel]). [episode] 为 null 时不占位.
 */
@Composable
fun WatchingEpisodeCard(
    episode: WatchingEpisode?,
    modifier: Modifier = Modifier,
) {
    if (episode == null) return
    Card(
        modifier = modifier.testTag("media_selector_watching"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Tv,
                contentDescription = null,
                Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(watchingEpisodeText(episode), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * 横屏对话框顶栏右侧的纯文本版本, 同样的文案与加粗. [episode] 为 null 时不占位.
 */
@Composable
fun WatchingEpisodeText(
    episode: WatchingEpisode?,
    modifier: Modifier = Modifier,
) {
    if (episode == null) return
    Text(
        watchingEpisodeText(episode),
        modifier.testTag("media_selector_watching"),
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * 「第 14 话」; 有 EP 时「第 02 (14) 话」: 本季集号在前, 序号在括号里. 资源标题多按本季集号命名, 用户先对的是它.
 */
@Composable
fun WatchingEpisode.episodeLabel(): String =
    if (ep == null) {
        stringResource(Lang.media_selector_episode_label, sort)
    } else {
        stringResource(Lang.media_selector_episode_label_with_ep, ep, sort)
    }

@Composable
private fun watchingEpisodeText(episode: WatchingEpisode): AnnotatedString {
    val prefix = stringResource(Lang.media_selector_watching_prefix)
    val episodeLabel = episode.episodeLabel()
    val prefixColor = MaterialTheme.colorScheme.onSurfaceVariant
    val emphasisColor = MaterialTheme.colorScheme.onSurface
    return buildAnnotatedString {
        withStyle(SpanStyle(color = prefixColor)) {
            append(prefix)
            append(' ')
        }
        withStyle(SpanStyle(color = emphasisColor, fontWeight = FontWeight.Medium)) {
            append(episodeLabel)
            if (episode.name.isNotBlank()) {
                append(' ')
                append(episode.name)
            }
        }
    }
}

