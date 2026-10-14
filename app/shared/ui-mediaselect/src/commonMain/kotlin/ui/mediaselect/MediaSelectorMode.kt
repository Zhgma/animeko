/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.mediaselect

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import me.him188.ani.datasources.api.source.MediaSourceKind

/**
 * 选择器的展示模式. 会话内保持 (放在 EpisodeViewModel), 不持久化, 不改 `MediaSelectorSettings.preferKind`.
 */
@Serializable
enum class MediaSelectorMode {
    AUTO,
    MANUAL,
    BT,
}

object MediaSelectorLayoutDefaults {
    /**
     * 容器宽度达到此值: BT 页用表格, 手动查找用双栏.
     */
    val WideContentMinWidth: Dp = 720.dp

    /**
     * 容器可用高度低于此值时铺满; 侧边栏可用高度低于此值时手动查找与 BT 改用容器, 见 [needsContainer].
     */
    val CompactDialogMaxHeight: Dp = 480.dp

    val DialogMaxWidth: Dp = 960.dp
    val DialogMaxHeight: Dp = 700.dp
    val DialogMargin: Dp = 64.dp
}

/**
 * 侧边栏可用高度为 [sideSheetHeight] 时, 这个模式是否关掉侧边栏、改用容器.
 * 太矮时除去标题、搜索框与筛选行, 手动查找与 BT 的列表只剩一两行; 自动匹配的源行矮, 总留在侧边栏.
 */
fun MediaSelectorMode.needsContainer(sideSheetHeight: Dp): Boolean =
    this != MediaSelectorMode.AUTO && sideSheetHeight < MediaSelectorLayoutDefaults.CompactDialogMaxHeight

/** Initial presentation follows the persisted preference; changing modes is session-local. */
fun initialMediaSelectorMode(preferKind: MediaSourceKind?, hasBt: Boolean): MediaSelectorMode =
    if (preferKind == MediaSourceKind.BitTorrent && hasBt) MediaSelectorMode.BT else MediaSelectorMode.AUTO
