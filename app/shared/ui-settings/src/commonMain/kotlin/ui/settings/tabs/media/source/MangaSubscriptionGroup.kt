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
import me.him188.ani.app.data.persistent.dataStores
import me.him188.ani.app.data.repository.manga.MangaSourceSettings
import me.him188.ani.app.data.repository.manga.MangaSourceSettingsStore
import me.him188.ani.app.data.repository.manga.MangaSubscription
import me.him188.ani.app.data.repository.manga.MangaSubscriptionRepository
import me.him188.ani.app.domain.foundation.HttpClientProvider
import me.him188.ani.app.domain.foundation.get
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.tools.formatDateTime
import me.him188.ani.app.ui.foundation.getClipEntryText
import me.him188.ani.app.ui.foundation.setClipEntryText
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import org.koin.mp.KoinPlatform

@Composable
internal fun SettingsScope.MangaSubscriptionGroup() {
    val context = LocalContext.current
    val provider = remember { KoinPlatform.getKoin().get<HttpClientProvider>() }
    val store = remember(context) { MangaSourceSettingsStore(context.dataStores.preferencesStore) }
    val repository = remember(store, provider) { MangaSubscriptionRepository(store, provider.get()) }
    val settings by store.flow.collectAsStateWithLifecycle(initialValue = null)
    val current = settings
    if (current != null) MangaSubscriptionGroupContent(current,
        repository::save, repository::refresh, repository::setEnabled, repository::delete)
}

/** Uses the same settings group, list items and menus as anime subscriptions. */
@Composable
internal fun SettingsScope.MangaSubscriptionGroupContent(
    settings: MangaSourceSettings,
    onSave: suspend (String?, String, String) -> Unit,
    onRefresh: suspend (MangaSubscription) -> Unit,
    onEnabled: suspend (String, Boolean) -> Unit,
    onDelete: suspend (String) -> Unit,
) {
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    fun runAction(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "操作失败，请重试" }
            finally { busy = false }
        }
    }
    Group(title = { Text("漫画源订阅") }, modifier = Modifier.testTag("manga-subscriptions"),
        description = { Text("支持 Venera 源链接和 index.json。导入本版本已适配的来源；其他来源会提示暂不支持。") },
        actions = {
            IconButton({ editingId = null; error = null; showEditor = true }, enabled = !busy,
                modifier = Modifier.testTag("manga-subscription-add")) { Icon(Icons.Rounded.Add, "新增漫画订阅") }
            if (busy) CircularProgressIndicator(Modifier.size(24.dp))
            else IconButton({ runAction { settings.subscriptions.filter { it.enabled }.forEach { onRefresh(it) } } },
                modifier = Modifier.testTag("manga-subscription-refresh-all")) { Icon(Icons.Rounded.Refresh, "刷新漫画订阅") }
        }) {
        if (settings.subscriptions.isEmpty()) Text("尚未添加漫画订阅", Modifier.padding(16.dp))
        settings.subscriptions.forEachIndexed { index, subscription ->
            var menu by remember(subscription.id) { mutableStateOf(false) }
            Item(headlineContent = { Text(subscription.name) }, modifier = Modifier.testTag("manga-subscription-${subscription.id}"),
                supportingContent = {
                    Column {
                        Text(subscription.url)
                        Text("${subscription.sources.size} 个已支持来源" +
                            if (subscription.unsupported.isNotEmpty()) " · ${subscription.unsupported.size} 个暂不支持" else "")
                        Text(subscription.updatedAt?.let { "上次更新：${formatDateTime(it)}" } ?: "尚未刷新订阅")
                        if (subscription.unsupported.isNotEmpty()) Text("暂不支持：${subscription.unsupported.joinToString()}" )
                        subscription.error?.let { Text("更新失败，保留上次来源：$it", color = MaterialTheme.colorScheme.error) }
                    }
                }, trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(subscription.enabled, { runAction { onEnabled(subscription.id, it) } }, enabled = !busy,
                            modifier = Modifier.testTag("manga-subscription-enabled-${subscription.id}"))
                        IconButton({ menu = true }, enabled = !busy,
                            modifier = Modifier.testTag("manga-subscription-menu-${subscription.id}")) {
                            Icon(Icons.Rounded.MoreVert, "管理 ${subscription.name}")
                        }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text("编辑") }, onClick = {
                                menu = false; editingId = subscription.id; error = null; showEditor = true
                            }, modifier = Modifier.testTag("manga-subscription-edit"))
                            DropdownMenuItem(text = { Text("刷新") }, onClick = {
                                menu = false; runAction { onRefresh(subscription) }
                            })
                            DropdownMenuItem(text = { Text("复制链接") }, onClick = {
                                menu = false; runAction { clipboard.setClipEntryText(subscription.url) }
                            })
                            DropdownMenuItem(text = { Text("删除", color = MaterialTheme.colorScheme.error) }, onClick = {
                                menu = false; deletingId = subscription.id
                            }, modifier = Modifier.testTag("manga-subscription-delete"))
                        }
                    }
                })
            if (index != settings.subscriptions.lastIndex) HorizontalDividerItem()
        }
        if (!showEditor) error?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
    }
    if (showEditor) {
        val editing = settings.subscriptions.find { it.id == editingId }
        var name by rememberSaveable(editingId) { mutableStateOf(editing?.name.orEmpty()) }
        var url by rememberSaveable(editingId) { mutableStateOf(editing?.url.orEmpty()) }
        AlertDialog(onDismissRequest = { if (!busy) showEditor = false },
            title = { Text(if (editingId == null) "新增漫画订阅" else "编辑漫画订阅") },
            text = {
                Column {
                    OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth().testTag("manga-subscription-name"),
                        label = { Text("名称") }, enabled = !busy, singleLine = true)
                    OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth().testTag("manga-subscription-url"),
                        label = { Text("订阅链接") }, enabled = !busy, singleLine = true,
                        trailingIcon = { IconButton({ scope.launch { clipboard.getClipEntryText()?.let { url = it } } }, enabled = !busy) {
                            Icon(Icons.Rounded.ContentPaste, "粘贴链接")
                        } })
                    Text("保存时检查链接并导入来源。源解析规则随应用更新。", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall)
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("manga-subscription-error")) }
                }
            }, confirmButton = {
                TextButton({ runAction { onSave(editingId, name, url); showEditor = false } },
                    enabled = !busy && name.isNotBlank() && url.isNotBlank(), modifier = Modifier.testTag("manga-subscription-save")) {
                    Text(if (busy) "正在检查…" else "保存")
                }
            }, dismissButton = { TextButton({ showEditor = false }, enabled = !busy) { Text("取消") } })
    }
    settings.subscriptions.find { it.id == deletingId }?.let { subscription ->
        AlertDialog(onDismissRequest = { if (!busy) deletingId = null }, title = { Text("删除漫画订阅") },
            text = { Text("删除“${subscription.name}”及其提供的来源？其他订阅仍提供的来源会保留，阅读记录和离线漫画不受影响。") },
            confirmButton = { TextButton({ runAction { onDelete(subscription.id); deletingId = null } }, enabled = !busy,
                modifier = Modifier.testTag("manga-subscription-confirm-delete")) { Text("删除") } },
            dismissButton = { TextButton({ deletingId = null }, enabled = !busy) { Text("取消") } })
    }
}
