/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.settings.tabs.media.source

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalClipboard
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.him188.ani.app.data.repository.novel.NovelSourceSettings
import me.him188.ani.app.data.repository.novel.NovelSourceSettingsStore
import me.him188.ani.app.data.repository.novel.NovelSubscription
import me.him188.ani.app.data.repository.novel.NovelSubscriptionRepository
import me.him188.ani.app.ui.foundation.getClipEntryText
import me.him188.ani.app.ui.foundation.setClipEntryText
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import org.koin.mp.KoinPlatform

@Composable
internal fun SettingsScope.NovelSubscriptionGroup() {
    val store = remember { KoinPlatform.getKoin().get<NovelSourceSettingsStore>() }
    val repository = remember { KoinPlatform.getKoin().get<NovelSubscriptionRepository>() }
    val settings by store.flow.collectAsStateWithLifecycle(initialValue = null)
    val current = settings
    if (current != null) NovelSubscriptionGroupContent(current,
        repository::save, repository::refresh, repository::setEnabled, repository::delete)
}

/** Maps native storage to the shared subscription presentation. */
@Composable
internal fun SettingsScope.NovelSubscriptionGroupContent(
    settings: NovelSourceSettings,
    onSave: suspend (String?, String, String) -> Unit,
    onRefresh: suspend (NovelSubscription) -> Unit,
    onEnabled: suspend (String, Boolean) -> Unit,
    onDelete: suspend (String) -> Unit,
) {
    val clipboard = LocalClipboard.current
    MangaSubscriptionPresentation(
        subscriptions = settings.subscriptions.map { MangaSubscriptionRow(
            it.id, it.name, it.url, it.enabled, it.sources.size, it.unsupported, it.updatedAt, it.error,
        ) },
        label = "小说", tagPrefix = "novel",
        description = "应用运行时每小时更新。支持小说 sub 和兼容的阅读书源 JSON，也可手动添加订阅。",
        editorDescription = "保存后导入书源；来源地址与静态解析规则随订阅刷新。",
        onSave = onSave,
        onRefresh = { id -> settings.subscriptions.find { it.id == id }?.let { onRefresh(it) } },
        onEnabled = onEnabled, onDelete = onDelete,
        onCopy = { clipboard.setClipEntryText(it) }, onPaste = { clipboard.getClipEntryText() },
    )
}
