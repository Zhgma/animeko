/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.mediaselect.common

import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.media_selector_search_hint
import org.jetbrains.compose.resources.stringResource

/**
 * 手动查找与 BT 页的关键字搜索框: 标准 [SearchBarDefaults.InputField], 形状同收起状态的 SearchBar (inputFieldShape).
 * 底色用 surfaceContainerHighest 而不是 SearchBar 默认的 surfaceContainerHigh: 播放器侧边栏本身就是 surfaceContainerHigh, 用默认色搜索框会与侧边栏融成一片;
 * 底部弹窗、详情页侧边栏与容器是 surfaceContainerLow, 两种底色上都能看出框.
 * 高度保持 InputField 自身的 56dp: 它的文字区是按 56dp 排的, 固定成更矮会把文字裁掉一半.
 * 调用方在 [modifier] 里先固定宽度: InputField 在其后接 `sizeIn(minWidth = 360.dp)`, 不先固定会撑破 300–400dp 的侧边栏.
 */
@Composable
fun MediaSelectorSearchField(
    keyword: String,
    onKeywordChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    SearchBarDefaults.InputField(
        query = keyword,
        onQueryChange = { onKeywordChange(it.trim('\n')) },
        onSearch = { onSearch() },
        expanded = false,
        onExpandedChange = {},
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest, SearchBarDefaults.inputFieldShape),
        enabled = enabled,
        placeholder = { Text(stringResource(Lang.media_selector_search_hint)) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
    )
}

