/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.playback

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.repository.player.ReadingHistoryEntry
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults

@Composable
internal fun HistoryPageControls(tab: Int, onTab: (Int) -> Unit, syncEnabled: Boolean, onSyncEnabled: (Boolean) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text("同步到 Animeko 服务端", style = MaterialTheme.typography.titleSmall)
                Text("仅同步番剧播放位置；漫画和小说保存在本机", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(syncEnabled, onSyncEnabled, Modifier.testTag("historyCloudSync"))
        }
        @Suppress("DEPRECATION")
        TabRow(selectedTabIndex = tab) {
            listOf("番剧", "漫画", "小说").forEachIndexed { index, title ->
                Tab(tab == index, { onTab(index) }, text = { Text(title) })
            }
        }
    }
}

@Composable
internal fun ReadingHistoryList(entries: List<ReadingHistoryEntry>, onOpen: (ReadingHistoryEntry) -> Unit,
    onDelete: (ReadingHistoryEntry) -> Unit,
) {
    var deleting by remember { mutableStateOf<ReadingHistoryEntry?>(null) }
    deleting?.let { entry ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("删除阅读记录") },
            text = { Text("删除《${entry.title}》的这条阅读记录？已下载内容和收藏不受影响。") },
            confirmButton = { TextButton({ onDelete(entry); deleting = null }) { Text("删除") } },
            dismissButton = { TextButton({ deleting = null }) { Text("取消") } })
    }
    if (entries.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("暂无阅读记录") }
    } else {
        LazyColumn(Modifier.fillMaxSize().testTag("readingHistoryList"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(entries, key = { _, entry -> entry.key }) { index, entry ->
                val progress = if (entry.manga != null) {
                    "第 ${entry.position + 1} 页" + if (entry.totalPages > 0) " / ${entry.totalPages} 页" else ""
                } else "第 ${entry.position + 1} 段"
                PlaybackHistoryListItem(
                    item = PlaybackHistoryUiItem(index, entry.subjectId, null, entry.title, entry.coverUrl,
                        "${entry.chapterTitle} · ${entry.sourceTitle}", (entry.position + 1).toLong(),
                        entry.totalPages.takeIf { it > 0 }?.toLong(), entry.updatedAtMillis, progress),
                    selected = false, selectionMode = false,
                    topAppBarContainerColor = AniThemeDefaults.pageContentBackgroundColor,
                    onClick = { onOpen(entry) }, onLongClick = { deleting = entry },
                    modifier = Modifier.widthIn(max = 960.dp).fillMaxWidth(),
                    trailingContent = {
                        IconButton({ deleting = entry }) { Icon(Icons.Rounded.Delete, "删除阅读记录") }
                    },
                )
            }
        }
    }
}
