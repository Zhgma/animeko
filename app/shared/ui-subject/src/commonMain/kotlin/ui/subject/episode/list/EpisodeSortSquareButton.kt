/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.list

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier


@Composable
internal fun EpisodeSortSquareButton(
    item: EpisodeListItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: EpisodeListColors = EpisodeListDefaults.colors(),
) {
    EpisodeStateButton(
        text = item.sort.toString(),
        isDoneOrDropped = item.isDoneOrDropped,
        isBroadcast = item.isBroadcast,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        colors = colors,
    )
}
