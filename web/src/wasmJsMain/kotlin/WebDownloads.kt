// SPDX-License-Identifier: AGPL-3.0-only
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.*
import kotlinx.coroutines.CancellationException
import me.him188.ani.app.domain.media.cache.engine.MediaCacheEngineKey
import me.him188.ani.app.domain.media.cache.engine.MediaStats
import me.him188.ani.app.tools.toProgress
import me.him188.ani.app.ui.adaptive.AniTopAppBar
import me.him188.ani.app.ui.download.components.*
import me.him188.ani.app.ui.download.subject.SubjectDownloadsSummaryRow
import me.him188.ani.app.ui.foundation.avatar.AvatarImage
import me.him188.ani.app.ui.foundation.session.SelfAvatarContent
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.FileSize.Companion.bytes
import me.him188.ani.datasources.api.topic.UnifiedCollectionType

internal fun browserDownloadGroups(tasks: List<TorrentTask>, collection: Map<Int, UnifiedCollectionType> = emptyMap()): List<SubjectDownloadGroup> =
    tasks.groupBy { if (it.subjectId > 0) "subject:${it.subjectId}" else if (it.subjectTitle.isNotBlank()) "title:${it.subjectTitle}" else "task:${it.id}" }
        .entries.sortedBy { it.key }.mapIndexed { index, (_, grouped) ->
            val first = grouped.first()
            val subjectId = first.subjectId.takeIf { it > 0 } ?: -(index + 1)
            val name = first.subjectTitle.ifBlank { first.name }
            val entries = grouped.mapIndexed { episodeIndex, task ->
                DownloadItem(subjectId, episodeIndex + 1, task.id,
                    runCatching { EpisodeSort(task.episode) }.getOrElse { EpisodeSort(episodeIndex + 1) },
                    name, task.name, task.createdAt,
                    DownloadItem.Stats(task.speed.toLong().coerceAtLeast(0).bytes, task.progress.toFloat().coerceIn(0f, 1f).toProgress(),
                        task.length.takeIf { it > 0 }?.bytes ?: FileSize.Unspecified),
                    when {
                        task.progress >= 1 && task.length > 0 -> DownloadStatus.COMPLETED
                        task.state == "stopped" -> DownloadStatus.PAUSED
                        task.state == "error" -> DownloadStatus.FAILED
                        else -> DownloadStatus.IN_PROGRESS
                    }, engineKey = MediaCacheEngineKey("WebTorrent"), subjectCollectionType = collection[task.subjectId])
            }
            SubjectDownloadGroup(subjectId, name, entries, collection[first.subjectId], first.cover.takeIf { it.isNotBlank() })
        }

internal fun browserDownloadStats(tasks: List<TorrentTask>) = MediaStats(
    uploaded = if (tasks.any { it.uploaded == null }) FileSize.Unspecified else tasks.sumOf { it.uploaded ?: 0 }.bytes,
    downloaded = tasks.sumOf { it.downloaded }.bytes,
    uploadSpeed = tasks.sumOf { it.uploadSpeed }.toLong().bytes,
    downloadSpeed = tasks.sumOf { it.speed }.toLong().bytes,
)

@Composable fun WebDownloads(session: WebResult, onAccount: () -> Unit) {
    val media = rememberBrowserMedia()
    var menu by remember { mutableStateOf(false) }
    var add by remember { mutableStateOf(false) }
    var help by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    var removing by remember { mutableStateOf<List<String>>(emptyList()) }
    var viewing by rememberSaveable { mutableStateOf<String?>(null) }
    var fileTask by remember { mutableStateOf<String?>(null) }
    val selection = rememberDownloadSelectionState()
    val filters = rememberDownloadFilterAndSortState()
    var collection by remember(session.userId) { mutableStateOf<Map<Int, UnifiedCollectionType>>(emptyMap()) }
    val subjectIds = media.state.tasks.map { it.subjectId }.filter { it > 0 }.toSet()
    LaunchedEffect(session.userId, session.signedIn, subjectIds) {
        collection = if (!session.signedIn) emptyMap() else subjectIds.mapNotNull { id ->
            try {
                val subject = animekoCollectionRequest("libraryRead", buildJsonObject { put("id", id) }).subject
                val type = subject?.animeCollection?.collectionType?.name
                id to when (type) { "WISH" -> UnifiedCollectionType.WISH; "DOING" -> UnifiedCollectionType.DOING
                    "DONE" -> UnifiedCollectionType.DONE; "ON_HOLD" -> UnifiedCollectionType.ON_HOLD
                    "DROPPED" -> UnifiedCollectionType.DROPPED; else -> UnifiedCollectionType.NOT_COLLECTED }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { null }
        }.toMap()
    }
    val groups = remember(media.state.tasks, collection) { browserDownloadGroups(media.state.tasks, collection) }
    val filtered = filters.applyFilterAndSortGrouped(groups)
    val current = groups.firstOrNull { it.entries.any { entry -> entry.id == viewing } }
    val chosen = groups.flatMap { it.entries }.filter { it.id in selection.selectedIds }
    LaunchedEffect(media.state.tasks) {
        selection.overrideSelected(selection.selectedIds.intersect(media.state.tasks.map { it.id }.toSet()))
    }
    fun batch(action: String, entries: List<DownloadItem>) {
        media.actionBatch(action, entries.map { it.id })
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val availableWidth = maxWidth
        val wide = availableWidth >= 760.dp
        val detailOnly = !wide && current != null && !selection.inSelection
        Scaffold(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest, topBar = {
            AniTopAppBar(title = { Text(if (selection.inSelection) "已选 ${chosen.size} 项" else if (detailOnly) current!!.subjectName else "下载", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (selection.inSelection || detailOnly) IconButton({ if (selection.inSelection) selection.clear() else viewing = null }) {
                        Icon(if (selection.inSelection) Icons.Rounded.Close else Icons.AutoMirrored.Rounded.ArrowBack, "返回")
                    }
                }, actions = {
                    IconButton({ if (selection.inSelection) {
                        val ids = filtered.flatMap { it.entries }.map { it.id }.toSet()
                        selection.overrideSelected(if (ids.all { it in selection.selectedIds }) emptySet() else ids)
                    } else selection.enterSelectionWith(emptySet()) }, enabled = groups.isNotEmpty()) {
                        Icon(if (selection.inSelection) Icons.Rounded.SelectAll else Icons.Rounded.Checklist, if (selection.inSelection) "全选" else "多选")
                    }
                    Box {
                        IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, "更多") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem({ Text("添加下载任务") }, { menu = false; add = true })
                            DropdownMenuItem({ Text("导入种子") }, { menu = false; media.action("torrentFile") }, enabled = !media.busy)
                            DropdownMenuItem({ Text("浏览器下载说明") }, { menu = false; help = true })
                        }
                    }
                }, avatar = { size -> if (!detailOnly && !selection.inSelection) SelfAvatarContent(false, session.signedIn, size, onAccount) { AvatarImage(session.avatar.takeIf { it.isNotBlank() }, it) } })
        }, bottomBar = {
            if (selection.inSelection) DownloadSelectionFloatingToolbar(
                chosen.any { it.isPaused || it.isFailed } && !media.busy, chosen.any { it.status == DownloadStatus.IN_PROGRESS } && !media.busy,
                chosen.isNotEmpty() && !media.busy,
                { batch("torrentResume", chosen.filter { it.isPaused || it.isFailed }) },
                { batch("torrentStop", chosen.filter { it.status == DownloadStatus.IN_PROGRESS }) },
                { removing = chosen.map { it.id } }, WindowInsets(0))
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                MediaStatus(media)
                Row(Modifier.weight(1f)) {
                    if (!detailOnly) LazyColumn(if (wide) Modifier.width(if (availableWidth >= 1200.dp) 400.dp else 360.dp).fillMaxHeight() else Modifier.fillMaxSize()) {
                        item { if (selection.inSelection) Text("已选 ${chosen.size} 项 · 共 ${chosen.sumOf { it.totalSize.takeUnless { it.isUnspecified }?.inBytes ?: 0 }.bytes}", Modifier.padding(24.dp))
                            else DownloadOverallStats({ browserDownloadStats(media.state.tasks) }, Modifier.fillMaxWidth().padding(horizontal = 24.dp)) }
                        item { DownloadFilterAndSortBar(filters, groups.flatMap { it.entries }.mapNotNull { it.engineKey }.distinct(), Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), MaterialTheme.colorScheme.surfaceContainerLowest) }
                        items(filtered, key = { it.key }) { group ->
                            val toggle = { selection.toggleSelection(*group.entries.map { it.id }.toTypedArray()) }
                            SubjectDownloadGroupCard(group, wide && current?.key == group.key, selection.inSelection,
                                group.entries.all { it.id in selection.selectedIds }, toggle,
                                { selection.enterSelectionWith(selection.selectedIds + group.entries.map { it.id }) },
                                { if (selection.inSelection) toggle() else viewing = group.entries.first().id },
                                modifier = Modifier.padding(horizontal = if (wide) 8.dp else 0.dp), showChevron = !wide,
                                shape = if (wide) MaterialTheme.shapes.large else RectangleShape)
                        }
                    }
                    if (wide || detailOnly) Surface(Modifier.weight(1f).fillMaxHeight(), shape = if (wide) MaterialTheme.shapes.extraLarge else RectangleShape,
                        color = if (wide) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerLowest) {
                        if (current == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("选择一个条目查看具体下载") }
                        else LazyColumn(contentPadding = PaddingValues(vertical = if (wide) 16.dp else 0.dp)) {
                            if (wide) item { Text(current.subjectName, Modifier.padding(horizontal = 24.dp, vertical = 12.dp), style = MaterialTheme.typography.headlineSmall) }
                            item { SubjectDownloadsSummaryRow(current.entries, null, selection.inSelection, chosen.filter { it.subjectId == current.subjectId },
                                { batch("torrentStop", current.entries.filter { it.status == DownloadStatus.IN_PROGRESS }) },
                                { batch("torrentResume", current.entries.filter { it.isPaused || it.isFailed }) }) }
                            items(current.entries, key = { it.id }) { entry ->
                                val task = media.state.tasks.first { it.id == entry.id }
                                var actions by remember { mutableStateOf(false) }
                                DownloadRowContent(entry,
                                    if (task.episode.isBlank()) task.name else "第 ${task.episode} 话 · ${task.name}",
                                    listOfNotNull(entry.detailedSizeText, "WebTorrent", if (entry.isFinished) "已完成" else null).joinToString(" · "),
                                    selection.inSelection, entry.id in selection.selectedIds,
                                    { selection.toggleSelection(entry.id) }, { selection.enterSelectionWith(selection.selectedIds + entry.id) },
                                    { actions = true }, primaryAction = {
                                        if (!entry.isFailed) IconButton({ when { entry.isFinished -> fileTask = task.id; entry.isPaused -> media.action("torrentResume", mediaId(task.id)); else -> media.action("torrentStop", mediaId(task.id)) } }, enabled = !media.busy) {
                                            Icon(if (entry.status == DownloadStatus.IN_PROGRESS) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (entry.isFinished) "播放 / 保存文件" else if (entry.isPaused) "继续下载" else "暂停下载", tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }, moreActions = {
                                        IconButton({ actions = true }) { Icon(Icons.Rounded.MoreVert, "更多操作") }
                                        DropdownMenu(actions, { actions = false }) {
                                            DropdownMenuItem({ Text("文件与传输详情") }, { actions = false; fileTask = task.id })
                                            if (entry.isFailed) DropdownMenuItem({ Text("重试") }, { actions = false; media.action("torrentResume", mediaId(task.id)) }, enabled = !media.busy)
                                            DropdownMenuItem({ Text("移除", color = MaterialTheme.colorScheme.error) }, { actions = false; removing = listOf(task.id) }, enabled = !media.busy)
                                        }
                                    })
                            }
                        }
                    }
                }
            }
        }
    }
    if (add) AlertDialog(onDismissRequest = { if (!media.busy) add = false }, title = { Text("添加下载任务") }, text = {
        Column { OutlinedTextField(url, { url = it }, label = { Text("磁力链接、种子网址或 Info Hash") }, modifier = Modifier.fillMaxWidth()); MediaStatus(media) }
    }, confirmButton = { TextButton({ media.action("torrentAdd", buildJsonObject { put("url", url) }) { add = false; url = "" } }, enabled = url.isNotBlank() && !media.busy) { Text("添加") } }, dismissButton = { TextButton({ add = false }) { Text("取消") } })
    if (help) AlertDialog(onDismissRequest = { help = false }, title = { Text("浏览器下载") }, text = { Text("下载保存在当前浏览器，可连接 WebRTC 节点与 Web Seed。关闭网页会暂停传输；再次打开可继续任务。保存到本机后可在浏览器外使用文件。") }, confirmButton = { TextButton({ help = false }) { Text("关闭") } })
    if (removing.isNotEmpty()) AlertDialog(onDismissRequest = { if (!media.busy) removing = emptyList() }, title = { Text("移除 ${removing.size} 个下载任务？") }, text = { Text("浏览器中的任务与下载缓存将被移除。已保存到本机的文件不会删除。") }, confirmButton = { TextButton({ media.actionBatch("torrentRemove", removing) { removing = emptyList(); selection.clear() } }, enabled = !media.busy) { Text("移除") } }, dismissButton = { TextButton({ removing = emptyList() }, enabled = !media.busy) { Text("取消") } })
    media.state.tasks.firstOrNull { it.id == fileTask }?.let { task ->
        ModalBottomSheet(onDismissRequest = { fileTask = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 600.dp), contentPadding = PaddingValues(16.dp)) { item { TorrentTaskCard(task, media) { removing = listOf(task.id) } } }
        }
    }
}

@Composable fun MediaStatus(media: BrowserMediaController) {
    if (media.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
    media.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp)) }
}
@Composable private fun TorrentTaskCard(task: TorrentTask, media: BrowserMediaController, onRemove: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val active = task.state in listOf("metadata", "downloading", "done")
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (task.subjectTitle.isNotBlank()) Text(task.subjectTitle + if (task.episode.isBlank()) "" else " · 第 ${task.episode} 话（手动选择）", style = MaterialTheme.typography.labelMedium)
        Text(task.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(when(task.state) { "metadata" -> "等待种子元数据"; "downloading" -> "下载中"; "done" -> "已完成 · 可做种"; "stopped" -> "已停止"; else -> "发生错误" })
        LinearProgressIndicator(progress = { task.progress.toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        Text("${(task.progress * 100).toInt()}% · ${mediaBytes(task.downloaded)} / ${mediaBytes(task.length)} · ${task.peers} 个节点", style = MaterialTheme.typography.bodySmall)
        Text("↓ ${mediaBytes(task.speed)}/s    ↑ ${mediaBytes(task.uploadSpeed)}/s", style = MaterialTheme.typography.bodySmall)
        if (task.error.isNotBlank()) Text(task.error, color = MaterialTheme.colorScheme.error)
        if (task.warning.isNotBlank()) Text(task.warning, style = MaterialTheme.typography.bodySmall)
        FlowRow {
            TextButton({ media.action(if (active) "torrentStop" else "torrentResume", mediaId(task.id)) }, enabled = !media.busy) { Text(if (active) "停止" else "继续") }
            TextButton({ expanded = !expanded }) { Text("文件 (${task.files.size})") }
            TextButton(onRemove, enabled = !media.busy) { Text("移除") }
        }
        if (expanded) task.files.forEach { file ->
            HorizontalDivider()
            Row { Checkbox(file.selected, { checked -> media.action("torrentSelect", buildJsonObject { put("id",task.id); put("index",file.index); put("selected",checked) }) }, enabled = active && !media.busy)
                Column(Modifier.weight(1f)) { Text(file.name, maxLines = 2, overflow = TextOverflow.Ellipsis); Text("${mediaBytes(file.length)} · ${(file.progress * 100).toInt()}%") } }
            FlowRow {
                TextButton({ media.action("torrentSave", buildJsonObject { put("id",task.id); put("index",file.index) }) }, enabled = active && file.progress >= 1 && !media.busy) { Text("保存到本机") }
                if (file.name.substringAfterLast('.').lowercase() in listOf("mp4","webm","ogg","ogv","mp3","m4a","wav")) TextButton({ media.action("torrentPlay", buildJsonObject { put("id",task.id); put("index",file.index) }) }, enabled = active && !media.busy) { Text("播放") }
            }
        }
    } }
}
