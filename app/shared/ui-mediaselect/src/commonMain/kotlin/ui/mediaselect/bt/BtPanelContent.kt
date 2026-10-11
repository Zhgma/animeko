/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.mediaselect.bt

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.media_selector_bt_filter
import me.him188.ani.app.ui.lang.media_selector_bt_filter_any
import me.him188.ani.app.ui.lang.media_selector_bt_filter_done
import me.him188.ani.app.ui.lang.media_selector_bt_filter_reset
import me.him188.ani.app.ui.lang.media_selector_bt_source_all
import me.him188.ani.app.ui.lang.media_selector_bt_source_count
import me.him188.ani.app.ui.lang.media_selector_bt_source_failed
import me.him188.ani.app.ui.lang.media_selector_bt_source_querying
import me.him188.ani.app.ui.lang.media_selector_filter_alliance
import me.him188.ani.app.ui.lang.media_selector_filter_resolution
import me.him188.ani.app.ui.lang.media_selector_filter_subtitle
import me.him188.ani.app.ui.lang.media_selector_retry
import me.him188.ani.app.ui.lang.media_selector_sources
import org.jetbrains.compose.resources.stringResource


/**
 * 紧凑列表两块面板共用的页面内底部面板: 自画 scrim (点击 = [onDismiss]) + 底部对齐、顶部圆角的 Surface, 铺在 [BtResourcesPage] 的根 Box 上, 不是 ModalBottomSheet.
 * ModalBottomSheet 在 Android 上是独立 window, 不继承全屏播放器已隐藏的系统栏, 放在全屏布局层里会把状态栏 / 导航栏拉回来;
 * 页面内叠层在全屏纯布局层、窄屏详情页的 ModalBottomSheet、桌面 Dialog 里行为一致, 也没有 sheet 叠 sheet 时返回键与 scrim 的归属问题.
 * 返回键由 [BtResourcesPage] 统一处理. [visible] 翻转为 false 时播放退出动画, 调用方在动画期间保持组合.
 * Surface 高度由 `weight(1f, fill = false)` 限制在顶部 56dp 留白之下, 内容用 verticalScroll 时有界.
 */
@Composable
fun BtInlineSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    sheetTestTag: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrimColor = BottomSheetDefaults.ScrimColor
    AnimatedVisibility(visible, modifier, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize()) {
            Canvas(
                Modifier.fillMaxSize().clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            ) {
                drawRect(color = scrimColor)
            }
            Column(Modifier.fillMaxSize().padding(top = 56.dp), verticalArrangement = Arrangement.Bottom) {
                Surface(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .animateEnterExit(enter = slideInVertically { it }, exit = slideOutVertically { it })
                        // Surface 上的点击不落到 scrim
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        )
                        .testTag(sheetTestTag),
                    shape = BottomSheetDefaults.ExpandedShape,
                    color = BottomSheetDefaults.ContainerColor,
                ) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            BottomSheetDefaults.DragHandle()
                        }
                        content()
                    }
                }
            }
        }
    }
}

/**
 * 面板行与菜单项共用的内容: 图标 + 名称 / 状态 + (重试) + 单选.
 * [enabled] 为 false 时灰显但仍可点击 (点击 = 重启该源).
 */
@Composable
fun BtSourceItem(
    icon: @Composable () -> Unit,
    title: String,
    status: (@Composable () -> Unit)?,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    retry: (@Composable () -> Unit)?,
    asMenuItem: Boolean,
    modifier: Modifier,
) {
    val alpha = if (enabled) 1f else 0.38f
    val text: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (status != null) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                    Box(Modifier.alpha(alpha)) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                            status()
                        }
                    }
                }
            }
        }
    }
    val trailing: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            retry?.invoke()
            RadioButton(selected = selected, onClick = null, enabled = enabled)
        }
    }
    if (asMenuItem) {
        DropdownMenuItem(
            text = { Box(Modifier.alpha(alpha)) { text() } },
            onClick = onClick,
            modifier = modifier,
            leadingIcon = { Box(Modifier.alpha(alpha)) { icon() } },
            trailingIcon = trailing,
        )
    } else {
        val interactionSource = remember { MutableInteractionSource() }
        Row(
            modifier
                .fillMaxWidth()
                .selectable(
                    selected = selected,
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    role = Role.RadioButton,
                    onClick = onClick,
                )
                .height(56.dp)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.alpha(alpha)) { icon() }
            Box(Modifier.weight(1f).alpha(alpha)) { text() }
            trailing()
        }
    }
}

/**
 * 一组单选 chip.
 */
@Composable
fun BtFilterGroup(
    title: String,
    values: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    label: (String) -> String = { it }
,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(Lang.media_selector_bt_filter_any)) },
            )
            for (value in values) {
                FilterChip(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    label = { Text(label(value), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
    }
}
