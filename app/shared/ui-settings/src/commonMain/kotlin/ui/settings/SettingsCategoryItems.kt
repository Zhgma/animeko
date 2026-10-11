/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SettingsApplications
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.vector.ImageVector
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings
import me.him188.ani.app.ui.lang.settings_category_app_ui
import me.him188.ani.app.ui.lang.settings_category_data_playback
import me.him188.ani.app.ui.lang.settings_category_network_storage
import me.him188.ani.app.ui.lang.settings_category_others
import me.him188.ani.app.ui.lang.settings_tab_about
import me.him188.ani.app.ui.lang.settings_tab_account
import me.him188.ani.app.ui.lang.settings_tab_appearance
import me.him188.ani.app.ui.lang.settings_tab_bt
import me.him188.ani.app.ui.lang.settings_tab_danmaku
import me.him188.ani.app.ui.lang.settings_tab_debug
import me.him188.ani.app.ui.lang.settings_tab_log
import me.him188.ani.app.ui.lang.settings_tab_media_selector
import me.him188.ani.app.ui.lang.settings_tab_media_source
import me.him188.ani.app.ui.lang.settings_tab_player
import me.him188.ani.app.ui.lang.settings_tab_proxy
import me.him188.ani.app.ui.lang.settings_tab_settings_backup
import me.him188.ani.app.ui.lang.settings_tab_storage
import me.him188.ani.app.ui.lang.settings_tab_theme
import me.him188.ani.app.ui.lang.settings_tab_update
import me.him188.ani.app.ui.settings.rendering.P2p
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ColumnScope.SettingsCategoryItems(showDebug: Boolean = false, item: @Composable (SettingsTab) -> Unit) {
    SettingsCategoryTitle(stringResource(Lang.settings_category_app_ui))
    item(SettingsTab.APPEARANCE)
    item(SettingsTab.THEME)
    SettingsCategoryTitle(stringResource(Lang.settings_category_data_playback))
    item(SettingsTab.PLAYER)
    item(SettingsTab.MEDIA_SOURCE)
    item(SettingsTab.MEDIA_SELECTOR)
    SettingsCategoryTitle(stringResource(Lang.settings_category_network_storage))
    item(SettingsTab.SERVER)
    item(SettingsTab.PROXY)
    item(SettingsTab.BT)
    item(SettingsTab.STORAGE)
    SettingsCategoryTitle(stringResource(Lang.settings_category_others))
    item(SettingsTab.UPDATE)
    item(SettingsTab.LOG)
    item(SettingsTab.ABOUT)
    if (showDebug) item(SettingsTab.DEBUG)
    item(SettingsTab.SETTINGS_BACKUP)
}

@Composable
fun SettingsCategoryTitle(text: String) {
    Text(text, Modifier.padding(horizontal = 16.dp).padding(top = 20.dp, bottom = 12.dp),
        color = MaterialTheme.colorScheme.primary)
}

@Stable
fun settingsTabIcon(tab: SettingsTab): ImageVector {
    return when (tab) {
        SettingsTab.PROFILE -> Icons.Outlined.AccountCircle
        SettingsTab.APPEARANCE -> Icons.Outlined.SettingsApplications
        SettingsTab.THEME -> Icons.Outlined.Palette
        SettingsTab.UPDATE -> Icons.Outlined.Update
        SettingsTab.PLAYER -> Icons.Outlined.SmartDisplay
        SettingsTab.MEDIA_SOURCE -> Icons.Outlined.Subscriptions
        SettingsTab.MEDIA_SELECTOR -> Icons.Outlined.FilterList
        SettingsTab.SERVER -> Icons.Outlined.Public
        SettingsTab.PROXY -> Icons.Outlined.VpnKey
        SettingsTab.BT -> Icons.Filled.P2p
//        SettingsTab.CACHE -> Icons.Rounded.Download // Icons.Outlined.Download 太 sharp 了
        SettingsTab.STORAGE -> Icons.Outlined.Storage
        SettingsTab.SETTINGS_BACKUP -> Icons.Outlined.Settings
        SettingsTab.ABOUT -> Icons.Outlined.Info
        SettingsTab.LOG -> Icons.Outlined.Feedback
        SettingsTab.DEBUG -> Icons.Outlined.Science
    }
}

@Stable
@Composable
fun settingsTabName(tab: SettingsTab): String {
    return when (tab) {
        SettingsTab.PROFILE -> stringResource(Lang.settings_tab_account)
        SettingsTab.APPEARANCE -> stringResource(Lang.settings_tab_appearance)
        SettingsTab.THEME -> stringResource(Lang.settings_tab_theme)
        SettingsTab.PLAYER -> stringResource(Lang.settings_tab_player)
        SettingsTab.MEDIA_SOURCE -> stringResource(Lang.settings_tab_media_source)
        SettingsTab.MEDIA_SELECTOR -> stringResource(Lang.settings_tab_media_selector)
        SettingsTab.SERVER -> stringResource(Lang.settings_tab_danmaku)
        SettingsTab.PROXY -> stringResource(Lang.settings_tab_proxy)
        SettingsTab.BT -> stringResource(Lang.settings_tab_bt)
//        SettingsTab.CACHE -> stringResource(Lang.settings_tab_cache)
        SettingsTab.STORAGE -> stringResource(Lang.settings_tab_storage)
        SettingsTab.SETTINGS_BACKUP -> stringResource(Lang.settings_tab_settings_backup)
        SettingsTab.LOG -> stringResource(Lang.settings_tab_log)
        SettingsTab.UPDATE -> stringResource(Lang.settings_tab_update)
        SettingsTab.ABOUT -> stringResource(Lang.settings_tab_about)
        SettingsTab.DEBUG -> stringResource(Lang.settings_tab_debug)
    }
}

typealias SettingsTab = me.him188.ani.app.navigation.SettingsTab
