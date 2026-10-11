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
import me.him188.ani.app.ui.foundation.dialogs.PlatformPopupProperties
import me.him188.ani.app.ui.foundation.widgets.SelectableDropdownMenuItem
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.media_selector_episode_label
import me.him188.ani.app.ui.lang.media_selector_episode_label_with_ep
import me.him188.ani.app.ui.lang.media_selector_mode_auto
import me.him188.ani.app.ui.lang.media_selector_mode_bt
import me.him188.ani.app.ui.lang.media_selector_mode_manual
import me.him188.ani.app.ui.lang.media_selector_mode_menu
import me.him188.ani.app.ui.lang.media_selector_watching_prefix
import me.him188.ani.app.ui.mediaselect.MediaSelectorMode
import me.him188.ani.app.ui.mediaselect.WatchingEpisode
import org.jetbrains.compose.resources.stringResource

/**
 * 顶栏右侧的模式下拉 chip: 标签 = 当前模式名 + ArrowDropDown, 32dp 标准 AssistChip (无选中态, 作菜单锚点);
 * 菜单项 自动匹配 / (手动查找) / (BT 资源), 用 [SelectableDropdownMenuItem], 选中项带 Check.
 *
 * @param showBt 会话内有 BT 源时为 true (`sourceResults.btSources.isNotEmpty()`); false 时菜单不出现 BT 项.
 * @param showManual 宿主没有手动查找状态时传 false, 菜单不出现 MANUAL 项.
 */
@Composable
fun MediaSelectorModeChip(
    mode: MediaSelectorMode,
    onModeChange: (MediaSelectorMode) -> Unit,
    modifier: Modifier = Modifier,
    showBt: Boolean = true,
    showManual: Boolean = true,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val menuText = stringResource(Lang.media_selector_mode_menu)
    Box(modifier) {
        AssistChip(
            onClick = { expanded = true },
            label = { Text(mode.displayName(), maxLines = 1, softWrap = false) },
            modifier = Modifier.testTag(MediaSelectorChromeTestTags.MODE_CHIP),
            enabled = enabled,
            trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = menuText) },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            properties = PlatformPopupProperties(clippingEnabled = false),
        ) {
            for (item in MediaSelectorMode.entries) {
                if (item == MediaSelectorMode.BT && !showBt) continue
                if (item == MediaSelectorMode.MANUAL && !showManual) continue
                SelectableDropdownMenuItem(
                    selected = item == mode,
                    text = { Text(item.displayName()) },
                    onClick = {
                        expanded = false
                        onModeChange(item)
                    },
                    modifier = Modifier.testTag(MediaSelectorChromeTestTags.modeItem(item)),
                )
            }
        }
    }
}

/**
 * 模式名文案.
 */
@Composable
fun MediaSelectorMode.displayName(): String = when (this) {
    MediaSelectorMode.AUTO -> stringResource(Lang.media_selector_mode_auto)
    MediaSelectorMode.MANUAL -> stringResource(Lang.media_selector_mode_manual)
    MediaSelectorMode.BT -> stringResource(Lang.media_selector_mode_bt)
}

object MediaSelectorChromeTestTags {
    const val MODE_CHIP = "media_selector_mode_chip"
    fun modeItem(mode: MediaSelectorMode) = "media_selector_mode_item_${mode.name}"
    const val WATCHING = "media_selector_watching"
}
