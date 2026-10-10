/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 桌面端宽屏布局在全屏时也由侧边栏开关决定是否显示次要内容. */
internal fun episodeScreenLayoutMode(
    isFullscreen: Boolean,
    showExpandedUI: Boolean,
    sidebarVisible: Boolean,
    isDesktop: Boolean,
): EpisodeScreenLayoutMode = when {
    isFullscreen && (!isDesktop || !showExpandedUI) -> EpisodeScreenLayoutMode.VIDEO_ONLY
    !showExpandedUI -> EpisodeScreenLayoutMode.COMPACT
    sidebarVisible -> EpisodeScreenLayoutMode.WIDE
    else -> EpisodeScreenLayoutMode.VIDEO_ONLY
}

/**
 * 宽屏布局的侧边栏开关. 窗口和全屏各记一份: 全屏是为了专心看视频, 默认不显示侧边栏;
 * 在全屏里展开侧边栏也不会改变退出全屏后的布局.
 */
@Stable
internal class EpisodeSidebarState(
    private val isFullscreen: () -> Boolean,
) {
    private var visibleInWindow by mutableStateOf(true)
    private var visibleInFullscreen by mutableStateOf(false)

    var isVisible: Boolean
        get() = if (isFullscreen()) visibleInFullscreen else visibleInWindow
        set(value) {
            if (isFullscreen()) visibleInFullscreen = value else visibleInWindow = value
        }
}
