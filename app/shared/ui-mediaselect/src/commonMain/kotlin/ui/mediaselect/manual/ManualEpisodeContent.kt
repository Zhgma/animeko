// SPDX-License-Identifier: AGPL-3.0-only
package me.him188.ani.app.ui.mediaselect.manual

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.lang.*
import me.him188.ani.datasources.api.source.BrowseChannel
import me.him188.ani.datasources.api.source.BrowseEpisode
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ManualSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        modifier,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}


internal fun shouldShowChannelRow(channels: List<BrowseChannel>): Boolean =
    !(channels.size == 1 && channels.single().name == null)

@Composable
internal fun BrowseChannel.displayLabel(): String =
    label ?: name ?: stringResource(Lang.media_selector_manual_default_channel)

@Composable
internal fun ManualChannelChips(
    channels: List<BrowseChannel>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(8.dp),
) {
    LazyRow(
        modifier,
        contentPadding = contentPadding,
        horizontalArrangement = horizontalArrangement,
    ) {
        itemsIndexed(channels) { index, channel ->
            InputChip(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                label = { Text(channel.displayLabel(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.testTag(ManualBrowsePageTestTags.channelChip(index)),
            )
        }
    }
}

/**
 * 剧集网格. 头部整行: 左「剧集 N 项」, 右「记住选择」+ 开关. 点一项 = [onClick] (直接播放). 调用方给 [modifier] 加 `weight(1f)`.
 *
 * @param rememberSelection 开关的值; null 时不显示开关.
 */
@Composable
internal fun ManualEpisodeGrid(
    episodes: List<BrowseEpisode>,
    selectedIndex: Int?,
    onClick: (Int) -> Unit,
    rememberSelection: Boolean?,
    onRememberSelectionChange: (Boolean) -> Unit,
    columns: Int,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
) {
    LazyVerticalGrid(
        GridCells.Fixed(columns),
        modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ManualSectionLabel(
                    stringResource(Lang.media_selector_manual_episodes_count, episodes.size),
                    Modifier.weight(1f),
                )
                if (rememberSelection != null) {
                    Row(
                        Modifier
                            .toggleable(
                                value = rememberSelection,
                                role = Role.Switch,
                                onValueChange = onRememberSelectionChange,
                            )
                            .testTag(ManualBrowsePageTestTags.REMEMBER_SWITCH),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(Lang.media_selector_manual_remember_selection),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Switch(checked = rememberSelection, onCheckedChange = null)
                    }
                }
            }
        }
        itemsIndexed(episodes) { index, episode ->
            ManualEpisodeButton(
                episode.name,
                selected = index == selectedIndex,
                onClick = { onClick(index) },
                Modifier.testTag(ManualBrowsePageTestTags.episode(index)),
            )
        }
    }
}

/**
 * 44dp 高圆角 10 的按钮; 选中 = primaryContainer 填充 + 2dp primary 边框, 未选中 = 1dp outline 描边.
 */
@Composable
private fun ManualEpisodeButton(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        },
    ) {
        Box(Modifier.fillMaxSize().padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Medium else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}


object ManualBrowsePageTestTags {
    const val ROOT = "manual_browse_page"
    const val SOURCE_SELECTOR = "manual_source_selector"
    fun sourceItem(instanceId: String) = "manual_source_item_$instanceId"
    const val SEARCH_FIELD = "manual_search_field"
    fun result(index: Int) = "manual_result_$index"
    const val BACK = "manual_back"
    fun channelChip(index: Int) = "manual_channel_$index"
    fun episode(index: Int) = "manual_episode_$index"
    const val REMEMBER_SWITCH = "manual_remember_switch"
    const val RETRY = "manual_retry"
}
