/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.navigation
import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
enum class SettingsTab {
    PROFILE,

    APPEARANCE,
    THEME,
    UPDATE,

    PLAYER,
    MEDIA_SOURCE,
    MEDIA_SELECTOR,
    SERVER,

    PROXY,
    BT,

    //    CACHE, // 自动缓存, 4.10 删除
    STORAGE,

    SETTINGS_BACKUP,

    ABOUT,
    LOG,
    DEBUG,
    ;

    companion object {
        /**
         * 在 PC 上右侧默认显示的 tab.
         */
        val Default = APPEARANCE
    }
}

