/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.subject.collection

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import me.him188.ani.app.ui.lang.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Collection card geometry shared by native state holders and browser state holders. */
@Composable
fun SubjectCollectionCard(
    onClick: () -> Unit,
    cover: @Composable () -> Unit,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 148.dp,
    shape: Shape = MaterialTheme.shapes.small,
    colors: CardColors = CardDefaults.cardColors(),
) {
    Card(onClick, modifier.clip(shape).fillMaxWidth().height(height), shape = shape, colors = colors) {
        Row(Modifier.weight(1f, fill = false)) {
            cover()
            Box(Modifier.weight(1f)) { content() }
        }
    }
}

/** Shared card contents; repositories provide progress, menus and playback actions. */
@Composable
fun SubjectCollectionItemLayout(
    title: String,
    airingLabel: @Composable () -> Unit,
    onShowEpisodeList: () -> Unit,
    playButton: @Composable () -> Unit,
    menu: @Composable () -> Unit,
    onShowMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(start = 12.dp).fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, overflow = TextOverflow.Ellipsis,
                maxLines = 1, modifier = Modifier.weight(1f))
            Box {
                IconButton(onShowMenu, Modifier.fillMaxHeight().testTag("SubjectCollectionItemMoreButton")) {
                    Icon(Icons.Outlined.MoreVert, "修改收藏状态", Modifier.size(24.dp))
                }
                menu()
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) { airingLabel() }
        Spacer(Modifier.weight(1f))
        Row(Modifier.padding(12.dp).align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onShowEpisodeList) { Text(stringResource(Lang.video_player_select_episode)) }
            Box(Modifier.width(IntrinsicSize.Min)) { playButton() }
        }
    }
}
