/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.manga

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.paging.LoadStates
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItemsWithLifecycle
import kotlinx.coroutines.flow.flowOf
import me.him188.ani.app.data.repository.manga.MangaSourceBook
import me.him188.ani.app.data.repository.manga.MangaChapter
import me.him188.ani.app.data.repository.manga.MangaCollectionType
import me.him188.ani.app.data.repository.manga.MangaSource
import me.him188.ani.app.data.repository.manga.MangaSourceSettings
import me.him188.ani.app.ui.comment.CommentState
import me.him188.ani.app.ui.foundation.ImageViewer
import me.him188.ani.app.ui.foundation.ImageViewerBackHandler
import me.him188.ani.app.ui.foundation.rememberImageViewerHandler
import me.him188.ani.app.ui.mediaselect.common.MediaSelectorDialog
import me.him188.ani.app.ui.subject.collection.components.EditCollectionTypeDropDown
import me.him188.ani.app.ui.subject.collection.components.SubjectCollectionAction
import me.him188.ani.app.ui.subject.collection.components.SubjectCollectionActionsForEdit
import me.him188.ani.app.ui.subject.collection.components.SubjectCollectionTypeButtonDefaults
import me.him188.ani.app.ui.subject.details.components.SubjectDetailsDefaults
import me.him188.ani.app.ui.subject.details.components.SubjectCommentColumn
import me.him188.ani.app.ui.subject.details.sections.SubjectCommentsSheet
import me.him188.ani.app.ui.subject.details.sections.ReviewsPreviewSection
import me.him188.ani.app.ui.subject.details.sections.HotReviewsCardContent
import me.him188.ani.app.ui.subject.details.sections.PagedEpisodesGrid
import me.him188.ani.app.ui.subject.details.sections.SectionHeader
import me.him188.ani.app.ui.subject.episode.list.EpisodeListItem
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.topic.UnifiedCollectionType

/** Shared actions let compact, wide and reading hosts use the same controls. */
internal class MangaPageActions(
    val comments: CommentState? = null,
    val setCollection: (MangaCollectionType) -> Unit = {},
    val setProgress: (Int, Int) -> Unit = { _, _ -> },
    val markRead: (MangaChapter) -> Unit = {},
    val writeReview: () -> Unit = {},
    val showSources: () -> Unit = {},
    val showChapters: () -> Unit = {},
    val showCache: () -> Unit = {},
    val sourceSettings: (MangaSourceSettings) -> Unit = {},
    val solveCaptcha: (MangaSource) -> Unit = {},
)
internal val LocalMangaActions = staticCompositionLocalOf { MangaPageActions() }

private fun MangaCollectionType.label() = when (this) {
    MangaCollectionType.WISH -> "想读"
    MangaCollectionType.DOING -> "在读"
    MangaCollectionType.DONE -> "读过"
    MangaCollectionType.ON_HOLD -> "搁置"
    MangaCollectionType.DROPPED -> "抛弃"
}

@Composable
internal fun MangaLibraryControls(state: MangaDetailsUiState, modifier: Modifier = Modifier) {
    val actions = LocalMangaActions.current
    var expanded by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(false) }
    val type = state.library?.type
    val unified = type?.let { UnifiedCollectionType.valueOf(it.name) } ?: UnifiedCollectionType.NOT_COLLECTED
    val choices = remember {
        SubjectCollectionActionsForEdit.filter { it.type != UnifiedCollectionType.NOT_COLLECTED }.map { action ->
            SubjectCollectionAction({ Text(MangaCollectionType.valueOf(action.type.name).label()) }, action.icon, action.type)
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            OutlinedButton({ expanded = true }, enabled = state.libraryLoaded && !state.libraryWorking,
                colors = SubjectCollectionTypeButtonDefaults.collectedButtonColors(),
                border = SubjectCollectionTypeButtonDefaults.collectedBorder(),
                modifier = Modifier.fillMaxWidth().testTag("manga-collection-edit")) {
                Text(type?.label() ?: "收藏")
            }
            EditCollectionTypeDropDown(unified, expanded, { expanded = false },
                { actions.setCollection(MangaCollectionType.valueOf(it.type.name)) }, actions = choices, showDelete = false)
        }
        TextButton({ progress = true }, enabled = type != null && !state.libraryWorking,
            modifier = Modifier.testTag("manga-progress-edit")) {
            Text("阅读进度：${state.library?.chapters ?: 0} 话 · ${state.library?.volumes ?: 0} 卷")
        }
    }
    if (progress) {
        var submitted by remember { mutableStateOf(false) }
        var chapters by remember { mutableStateOf(state.library?.chapters?.toString().orEmpty()) }
        var volumes by remember { mutableStateOf(state.library?.volumes?.toString().orEmpty()) }
        AlertDialog(onDismissRequest = { if (!state.libraryWorking) progress = false }, title = { Text("阅读进度") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(chapters, { chapters = it }, label = { Text("已读话数") }, singleLine = true,
                    modifier = Modifier.testTag("manga-progress-chapters"))
                OutlinedTextField(volumes, { volumes = it }, label = { Text("已读卷数") }, singleLine = true)
                state.libraryError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            } }, confirmButton = {
                TextButton({ submitted = true; actions.setProgress(chapters.toInt(), volumes.toInt()) },
                    enabled = !state.libraryWorking && (chapters.toIntOrNull() ?: -1) >= 0 && (volumes.toIntOrNull() ?: -1) >= 0) { Text("保存") }
            }, dismissButton = { TextButton({ progress = false }) { Text("关闭") } })
        LaunchedEffect(state.library, state.libraryWorking) {
            if (submitted && state.library?.chapters == chapters.toIntOrNull() && state.library?.volumes == volumes.toIntOrNull() && !state.libraryWorking && state.libraryError == null) progress = false
        }
    }
}

@Composable
private fun mangaComments(state: MangaDetailsUiState): CommentState {
    LocalMangaActions.current.comments?.let { return it }
    val scope = rememberCoroutineScope()
    return remember(state.comments) { CommentState(flowOf(PagingData.from(state.comments.map { it.toUiComment() }, sourceLoadStates = LoadStates(LoadState.NotLoading(false), LoadState.NotLoading(true), LoadState.NotLoading(true)))),
        mutableStateOf(state.commentsTotal), { _, _, _ -> }, scope) }
}

@Composable
internal fun MangaReviewPreview(state: MangaDetailsUiState, rail: Boolean, onAll: () -> Unit) {
    val comments = mangaComments(state)
    val items = comments.list.collectAsLazyPagingItemsWithLifecycle()
    if (items.itemCount == 0) SectionHeader("评价") {
        TextButton(onAll, Modifier.testTag("manga-all-comments")) { Text("查看全部") }
    }
    else if (rail) HotReviewsCardContent(items, comments.count, onAll)
    else ReviewsPreviewSection(items, comments.count, onAll)
}

@Composable
internal fun MangaReviewFeed(state: MangaDetailsUiState, modifier: Modifier = Modifier, pullToRefreshEnabled: Boolean = true) {
    val comments = mangaComments(state)
    val actions = LocalMangaActions.current
    val uri = LocalUriHandler.current
    val viewer = rememberImageViewerHandler()
    ImageViewerBackHandler(viewer)
    ImageViewer(viewer) { viewer.clear() }
    Column(modifier.fillMaxSize().scrollable(rememberScrollableState { 0f }, Orientation.Vertical)) {
        TextButton(actions.writeReview, Modifier.testTag("manga-write-review")) { Text("写评价") }
        SubjectDetailsDefaults.SubjectCommentColumn(comments, uri::openUri, viewer::viewImage,
            Modifier.fillMaxWidth().weight(1f), pullToRefreshEnabled = pullToRefreshEnabled, onOpenOriginal = { uri.openUri("https://bgm.tv/subject/${state.subjectId}/comments") })
    }
}

@Composable
internal fun MangaReviewsSheet(state: MangaDetailsUiState, onDismiss: () -> Unit) {
    val comments = mangaComments(state)
    val uri = LocalUriHandler.current
    val viewer = rememberImageViewerHandler()
    ImageViewerBackHandler(viewer)
    ImageViewer(viewer) { viewer.clear() }
    SubjectCommentsSheet(comments, uri::openUri, viewer::viewImage, LocalMangaActions.current.writeReview, onDismiss,
        onOpenOriginal = { uri.openUri("https://bgm.tv/subject/${state.subjectId}/comments") })
}

@Composable
internal fun MangaChapterGrid(state: MangaDetailsUiState, onChapter: (MangaChapter) -> Unit, rows: Int = 2) {
    var group by rememberSaveable { mutableStateOf("") }
    val selected = group.takeIf { g -> state.chapters.any { it.group == g } } ?: state.chapter?.group ?: state.chapters.firstOrNull()?.group
    val chapters = state.chapters.filter { it.group == selected }
    val actions = LocalMangaActions.current
    var actionChapter by remember { mutableStateOf<MangaChapter?>(null) }
    val items = chapters.mapIndexed { index, chapter ->
        val count = if (chapter.group == "单行本") state.library?.volumes else state.library?.chapters
        EpisodeListItem(index, EpisodeSort(chapter.number?.toString().orEmpty()), null, chapter.title, chapter.title,
            if (count != null && chapter.number?.let { it <= count } == true) UnifiedCollectionType.DONE else UnifiedCollectionType.NOT_COLLECTED,
            true, playProgress = if (chapter.id == state.memory?.chapterId && chapter.pageCount > 0)
                (state.memory.page + 1).toFloat() / chapter.pageCount else null)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.chapters.map { it.group }.distinct().forEach { name -> FilterChip(selected == name, { group = name }, label = { Text(name) }) }
        }
        PagedEpisodesGrid(items, chapters.indexOfFirst { it.id == (state.chapter?.id ?: state.memory?.chapterId) }.takeIf { it >= 0 },
            { onChapter(chapters[it.episodeId]) }, { actionChapter = chapters[it.episodeId] }, rowsPerPage = rows, showImages = false,
            header = { pager -> SectionHeader("章节") {
                pager?.invoke()
                TextButton(actions.showCache, Modifier.testTag("manga-cache-open")) { Text("缓存") }
                TextButton(actions.showSources) { Text("数据源") }
            } })
    }
    actionChapter?.let { chapter ->
        AlertDialog(onDismissRequest = { actionChapter = null }, title = { Text(chapter.title) },
            text = { Text("更新 Bangumi 阅读进度或管理离线缓存") },
            confirmButton = { TextButton({ actions.markRead(chapter); actionChapter = null }, enabled = state.library != null && chapter.group in listOf("章节", "单行本") && chapter.number?.let { it.isFinite() && it >= 1 && it % 1.0 == 0.0 } == true) { Text("标记读过") } },
            dismissButton = { TextButton({ actionChapter = null; actions.showCache() }) { Text("缓存") } })
    }
}

@Composable
internal fun MangaSourcesDialog(state: MangaDetailsUiState, keyword: String, onDismiss: () -> Unit,
    onSearch: (MangaSource, String) -> Unit, onBook: (MangaSourceBook) -> Unit,
    onAuto: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf(keyword) }
    var manage by rememberSaveable { mutableStateOf(false) }
    val actions = LocalMangaActions.current
    val uri = LocalUriHandler.current
    MediaSelectorDialog(onDismiss) { _ ->
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("数据源", style = MaterialTheme.typography.titleLarge)
                TextButton({ manage = !manage }) { Text(if (manage) "返回选源" else "管理来源") }
                TextButton(onDismiss) { Text("关闭") }
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!manage) item { MangaSourcePanel(state, query, { query = it }, onSearch, onBook, onAuto) }
                else items(state.sourceSettings.all, key = { it.name }) { source ->
                    Column {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(source.title)
                            Switch(source !in state.sourceSettings.disabled, { enabled ->
                                actions.sourceSettings(state.sourceSettings.copy(disabled = if (enabled) state.sourceSettings.disabled - source else state.sourceSettings.disabled + source))
                            }, modifier = Modifier.testTag("manga-source-enabled-${source.name}"))
                        }
                        Row {
                            TextButton({
                                val order = state.sourceSettings.all.toMutableList()
                                val index = order.indexOf(source)
                                if (index > 0) { order.removeAt(index); order.add(index - 1, source); actions.sourceSettings(state.sourceSettings.copy(order = order)) }
                            }, enabled = state.sourceSettings.all.first() != source, modifier = Modifier.testTag("manga-source-up-${source.name}")) { Text("上移") }
                            TextButton({ uri.openUri(source.home) }) { Text("打开网站") }
                            state.sourceSettings.subscriptionUrl(source)?.let { url ->
                                TextButton({ uri.openUri(url) }) { Text("订阅链接") }
                            }
                        }
                    }
                }
            }
        }
    }
}
