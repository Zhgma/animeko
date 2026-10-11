/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.manga

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.repository.manga.MangaChapter
import me.him188.ani.app.ui.mediaselect.common.MediaSelectorDialog

@Composable
internal fun MangaCacheDialog(state: MangaDetailsUiState, onDismiss: () -> Unit,
    onDownload: (List<MangaChapter>) -> Unit, onPause: () -> Unit,
    onRetry: (MangaOfflineChapter) -> Unit, onRead: (MangaOfflineChapter) -> Unit,
    onDelete: (MangaOfflineChapter) -> Unit,
) {
    var selected by remember(state.book) { mutableStateOf(emptySet<String>()) }
    var removing by remember { mutableStateOf<MangaOfflineChapter?>(null) }
    MediaSelectorDialog(onDismiss) { _ ->
        Column(Modifier.fillMaxSize().padding(16.dp).testTag("manga-cache-manager")) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("漫画缓存", style = MaterialTheme.typography.titleLarge)
                TextButton(onDismiss) { Text("关闭") }
            }
            if (state.offlineWorking) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                TextButton(onPause) { Text("暂停下载") }
            }
            state.offlineError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.offline, key = { "${it.book.source}:${it.book.id}:${it.chapter.id}" }) { entry ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("${entry.book.source.title} · ${entry.chapter.title}")
                            Text("${entry.files.size} / ${entry.total} 页 · ${if (entry.complete) "可离线阅读" else "未完成"}")
                            entry.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            Row {
                                TextButton({ onRead(entry); onDismiss() }, enabled = entry.complete && !state.offlineWorking) { Text("阅读") }
                                if (!entry.complete) TextButton({ onRetry(entry) }, enabled = !state.offlineWorking) { Text("继续 / 重试") }
                                TextButton({ removing = entry }, enabled = !state.offlineWorking) { Text("删除") }
                            }
                        }
                    }
                }
                item {
                    HorizontalDivider()
                    Text("选择章节缓存 · ${state.book?.source?.title.orEmpty()}", style = MaterialTheme.typography.titleMedium)
                    TextButton({ selected = if (selected.size == state.chapters.size) emptySet() else state.chapters.map { it.id }.toSet() }) { Text("全选 / 取消全选") }
                }
                items(state.chapters, key = { "select:${it.id}" }) { chapter ->
                    Row {
                        Checkbox(chapter.id in selected, { checked -> selected = if (checked) selected + chapter.id else selected - chapter.id })
                        Text(chapter.title, Modifier.padding(top = 12.dp))
                    }
                }
            }
            Button({ onDownload(state.chapters.filter { it.id in selected }) }, enabled = selected.isNotEmpty() && !state.offlineWorking,
                modifier = Modifier.fillMaxWidth()) { Text("缓存所选 ${selected.size} 话 / 卷") }
        }
    }
    removing?.let { entry ->
        AlertDialog(onDismissRequest = { removing = null }, title = { Text("删除缓存") }, text = { Text(entry.chapter.title) },
            confirmButton = { TextButton({ onDelete(entry); removing = null }) { Text("删除") } },
            dismissButton = { TextButton({ removing = null }) { Text("取消") } })
    }
}
