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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import me.him188.ani.app.ui.mediaselect.MediaSelectorMode
import me.him188.ani.app.ui.mediaselect.common.MediaSelectorTopBar
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
import me.him188.ani.app.ui.subject.collection.components.SubjectCollectionAction
import me.him188.ani.app.ui.subject.collection.components.SubjectCollectionActionsForEdit
import me.him188.ani.app.ui.subject.collection.components.SubjectCollectionTypeButton
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
internal fun MangaLibraryControls(
    state: MangaDetailsUiState, modifier: Modifier = Modifier,
    showCollection: Boolean = true, showProgress: Boolean = true, fillWidth: Boolean = true,
) {
    val actions = LocalMangaActions.current
    var progress by remember { mutableStateOf(false) }
    val type = state.library?.type
    val unified = type?.let { UnifiedCollectionType.valueOf(it.name) } ?: UnifiedCollectionType.NOT_COLLECTED
    val choices = remember {
        SubjectCollectionActionsForEdit.filter { it.type != UnifiedCollectionType.NOT_COLLECTED }.map { action ->
            SubjectCollectionAction({ Text(MangaCollectionType.valueOf(action.type.name).label()) }, action.icon, action.type)
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showCollection) {
            SubjectCollectionTypeButton(unified,
                { actions.setCollection(MangaCollectionType.valueOf(it.name)) },
                modifier = (if (fillWidth) Modifier.fillMaxWidth() else Modifier).testTag("manga-collection-edit"),
                enabled = state.libraryLoaded && !state.libraryWorking,
                currentLabel = type?.label() ?: "收藏", actions = choices, showDelete = false)
        }
        if (showProgress) TextButton({ progress = true }, enabled = type != null && !state.libraryWorking,
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
internal fun MangaReviewFeed(state: MangaDetailsUiState, modifier: Modifier = Modifier, pullToRefreshEnabled: Boolean = true,
    showWriteButton: Boolean = true) {
    val comments = mangaComments(state)
    val actions = LocalMangaActions.current
    val uri = LocalUriHandler.current
    val viewer = rememberImageViewerHandler()
    ImageViewerBackHandler(viewer)
    ImageViewer(viewer) { viewer.clear() }
    Column(modifier.fillMaxSize().scrollable(rememberScrollableState { 0f }, Orientation.Vertical)) {
        if (showWriteButton) TextButton(actions.writeReview, Modifier.testTag("manga-write-review")) { Text("写评价") }
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
    val anchor = mangaChapterGridAnchor(state)
    var group by rememberSaveable(state.subjectId, state.book?.source, state.book?.id, anchor?.group) { mutableStateOf("") }
    var descending by rememberSaveable(state.subjectId, state.book?.source, state.book?.id) { mutableStateOf(true) }
    val selected = group.takeIf { g -> state.chapters.any { it.group == g } } ?: anchor?.group ?: state.chapters.firstOrNull()?.group
    val chapters = state.chapters.filter { it.group == selected }.let { if (descending) it.reversed() else it }
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
            TextButton({ descending = !descending }, Modifier.testTag("manga-chapter-sort")) {
                Text(if (descending) "倒序" else "正序")
            }
        }
        key(state.subjectId, state.book?.source, state.book?.id, selected, descending) {
            PagedEpisodesGrid(items, chapters.indexOfFirst { it.id == anchor?.id }.takeIf { it >= 0 },
                { onChapter(chapters[it.episodeId]) }, { actionChapter = chapters[it.episodeId] }, rowsPerPage = rows, showImages = false,
                header = { pager -> SectionHeader("章节") {
                    pager?.invoke()
                    TextButton(actions.showCache, Modifier.testTag("manga-cache-open")) { Text("缓存") }
                    TextButton(actions.showSources) { Text("数据源") }
                } })
        }
    }
    actionChapter?.let { chapter ->
        AlertDialog(onDismissRequest = { actionChapter = null }, title = { Text(chapter.title) },
            text = { Text("更新 Bangumi 阅读进度或管理离线缓存") },
            confirmButton = { TextButton({ actions.markRead(chapter); actionChapter = null }, enabled = state.library != null && chapter.group in listOf("章节", "单行本") && chapter.number?.let { it.isFinite() && it >= 1 && it % 1.0 == 0.0 } == true) { Text("标记读过") } },
            dismissButton = { TextButton({ actionChapter = null; actions.showCache() }) { Text("缓存") } })
    }
}

/** Resolve the reading position against this source's catalog, before choosing a group and page. */
internal fun mangaChapterGridAnchor(state: MangaDetailsUiState): MangaChapter? {
    state.chapters.firstOrNull { it.id == state.chapter?.id }?.let { return it }
    val memory = state.memory?.takeIf {
        state.book == null || it.book.source == state.book.source && it.book.id == state.book.id
    }
    state.chapters.firstOrNull { it.id == memory?.chapterId }?.let { return it }
    val progress = state.library ?: return null
    return state.chapters.firstOrNull { it.group == "章节" && progress.chapters > 0 && it.number == progress.chapters.toDouble() }
        ?: state.chapters.firstOrNull { it.group == "单行本" && progress.volumes > 0 && it.number == progress.volumes.toDouble() }
}

@Composable
internal fun MangaSourcesDialog(state: MangaDetailsUiState, keyword: String, onDismiss: () -> Unit,
    onSearch: (MangaSource, String) -> Unit, onBook: (MangaSourceBook) -> Unit,
    onAuto: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf(keyword) }
    var manage by rememberSaveable { mutableStateOf(false) }
    var mode by rememberSaveable { mutableStateOf(MediaSelectorMode.AUTO) }
    val actions = LocalMangaActions.current
    val uri = LocalUriHandler.current
    MediaSelectorDialog(onDismiss) { _ ->
        Column(Modifier.fillMaxSize()) {
            MediaSelectorTopBar(if (manage) "管理来源" else "数据源", mode, { mode = it; manage = false }, onDismiss, showBt = false,
                extraActions = {
                    IconButton({ manage = !manage }, Modifier.testTag("manga-source-manage")) {
                        Icon(Icons.Rounded.Settings, if (manage) "返回选源" else "管理来源")
                    }
                })
            if (!manage) MangaSourcePanel(state, query, { query = it }, onSearch, onBook, onAuto,
                modifier = Modifier.weight(1f).then(if (mode == MediaSelectorMode.AUTO) Modifier.verticalScroll(rememberScrollState()) else Modifier),
                manual = mode == MediaSelectorMode.MANUAL, onManualSearch = { mode = MediaSelectorMode.MANUAL })
            else LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.sourceSettings.all, key = { it.name }) { source ->
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
