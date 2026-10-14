/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.mediaselect.manual

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.media_selector_load_failed
import me.him188.ani.app.ui.lang.media_selector_manual_captcha_unsupported
import me.him188.ani.app.ui.lang.media_selector_manual_default_channel
import me.him188.ani.app.ui.lang.media_selector_manual_episodes_count
import me.him188.ani.app.ui.lang.media_selector_manual_no_result
import me.him188.ani.app.ui.lang.media_selector_manual_no_sources
import me.him188.ani.app.ui.lang.media_selector_manual_remember_selection
import me.him188.ani.app.ui.lang.media_selector_manual_result_count
import me.him188.ani.app.ui.lang.media_selector_retry
import me.him188.ani.app.ui.lang.media_selector_sources
import me.him188.ani.app.ui.settings.rendering.MediaSourceIcon
import me.him188.ani.app.ui.settings.rendering.MediaSourceTierTag
import me.him188.ani.datasources.api.source.BrowseChannel
import me.him188.ani.datasources.api.source.BrowseEpisode
import me.him188.ani.datasources.api.source.BrowseSubject
import org.jetbrains.compose.resources.stringResource

/**
 * 「数据源」「线路」「共 N 条结果」这类小标签.
 */

/**
 * 源选择框: 带「数据源」标签的只读框显示当前源, 点开下拉菜单竖向列出所有源 (图标 + 名称 + tier), 当前源打勾. 无源时显示提示文本.
 *
 * 源的数量没有上限, 而手动查找常在 300–400dp 宽的侧边栏里: 横排时一行只露出两三个源, 鼠标也难以横向滚动.
 * 竖排的菜单能一次看到更多源, 并用滚轮翻动.
 *
 * @param isPlaceholder 状态尚未就绪 ([ManualBrowsePresentation.isPlaceholder]): 空列表只是还没发射, 画一个空的选择框占位, 不显示「没有支持浏览的数据源」.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualSourceSelector(
    sources: List<ManualBrowseSource>,
    selectedSourceId: String?,
    onSelect: (instanceId: String) -> Unit,
    modifier: Modifier = Modifier,
    isPlaceholder: Boolean = false,
) {
    ManualSourceSelectorContent(sources, selectedSourceId, onSelect,
        id={it.instanceId}, name={it.info.displayName}, icon={ManualSourceIcon(it)},
        tier={source, selected -> source.info.tier?.let { MediaSourceTierTag(it,
            containerColor=if(selected) MaterialTheme.colorScheme.surfaceContainerLowest else MaterialTheme.colorScheme.secondaryContainer) } },
        modifier=modifier, isPlaceholder=isPlaceholder)

}

/**
 * 选中项用 secondaryContainer 底色 + 对勾; tier 标签与对勾占固定的尾部位置, 各行的标签上下对齐.
 */
@Composable
private fun ManualSourceMenuItem(
    source: ManualBrowseSource,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    DropdownMenuItem(
        text = { Text(source.info.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        onClick = onClick,
        modifier = Modifier
            .ifThen(selected) { background(colorScheme.secondaryContainer) }
            .semantics { this.selected = selected }
            .testTag(ManualBrowsePageTestTags.sourceItem(source.instanceId)),
        leadingIcon = { ManualSourceIcon(source) },
        trailingIcon = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                source.info.tier?.let { tier ->
                    MediaSourceTierTag(
                        tier,
                        containerColor = if (selected) colorScheme.surfaceContainerLowest else colorScheme.secondaryContainer,
                    )
                }
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    if (selected) {
                        Icon(Icons.Rounded.Check, contentDescription = null)
                    }
                }
            }
        },
        colors = if (selected) {
            MenuDefaults.itemColors(
                textColor = colorScheme.onSecondaryContainer,
                trailingIconColor = colorScheme.onSecondaryContainer,
            )
        } else {
            MenuDefaults.itemColors()
        },
        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
    )
}

@Composable
private fun ManualSourceIcon(source: ManualBrowseSource) {
    MediaSourceIcon(source.info, Modifier.size(24.dp).clip(MaterialTheme.shapes.extraSmall))
}
