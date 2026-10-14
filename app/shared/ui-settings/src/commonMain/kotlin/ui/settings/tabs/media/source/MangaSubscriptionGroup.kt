/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.settings.tabs.media.source

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.him188.ani.app.data.repository.manga.MangaSourceSettings
import me.him188.ani.app.data.repository.manga.MangaSourceSettingsStore
import me.him188.ani.app.data.repository.manga.MangaSubscription
import me.him188.ani.app.data.repository.manga.MangaSubscriptionRepository
import me.him188.ani.app.tools.formatDateTime
import me.him188.ani.app.ui.foundation.getClipEntryText
import me.him188.ani.app.ui.foundation.setClipEntryText
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import org.koin.mp.KoinPlatform

@Composable
internal fun SettingsScope.MangaSubscriptionGroup() {
    val store = remember { KoinPlatform.getKoin().get<MangaSourceSettingsStore>() }
    val repository = remember { KoinPlatform.getKoin().get<MangaSubscriptionRepository>() }
    val settings by store.flow.collectAsStateWithLifecycle(initialValue = null)
    val current = settings
    if (current != null) MangaSubscriptionGroupContent(current,
        repository::save, repository::refresh, repository::setEnabled, repository::delete)
}

/** Maps native storage to the shared subscription presentation. */
@Composable
internal fun SettingsScope.MangaSubscriptionGroupContent(
    settings: MangaSourceSettings,
    onSave: suspend (String?, String, String) -> Unit,
    onRefresh: suspend (MangaSubscription) -> Unit,
    onEnabled: suspend (String, Boolean) -> Unit,
    onDelete: suspend (String) -> Unit,
) {
    val clipboard = LocalClipboard.current
    MangaSubscriptionPresentation(
        subscriptions = settings.subscriptions.map { MangaSubscriptionRow(
            it.id, it.name, it.url, it.enabled, it.sources.size, it.unsupported, it.updatedAt, it.error,
        ) },
        onSave = onSave,
        onRefresh = { id -> settings.subscriptions.find { it.id == id }?.let { onRefresh(it) } },
        onEnabled = onEnabled, onDelete = onDelete,
        onCopy = { clipboard.setClipEntryText(it) }, onPaste = { clipboard.getClipEntryText() },
    )
}
