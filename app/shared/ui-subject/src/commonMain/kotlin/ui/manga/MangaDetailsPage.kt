/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.manga

import me.him188.ani.app.ui.reading.rememberReaderStatusPreference

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import me.him188.ani.app.ui.rating.EditableRatingDialogsHost
import me.him188.ani.app.ui.subject.relations.SubjectRelationGraphScreen
import me.him188.ani.app.ui.subject.relations.SubjectRelationGraphUiState
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import me.him188.ani.app.ui.foundation.layout.LocalPlatformWindow
import me.him188.ani.app.ui.foundation.layout.setRequestFullScreen
import me.him188.ani.app.ui.foundation.layout.setSystemBarVisible
import androidx.compose.runtime.CompositionLocalProvider
import me.him188.ani.app.ui.mediaselect.common.MediaSelectorDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.him188.ani.app.data.repository.manga.MangaChapter
import me.him188.ani.app.data.repository.manga.MangaCollection
import me.him188.ani.app.data.repository.manga.MangaSource
import me.him188.ani.app.data.repository.manga.MangaSourceBook
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.ui.foundation.navigation.BackHandler
import me.him188.ani.app.ui.subject.details.sections.SectionHeader

@Composable
fun MangaDetailsRoute(manga: MangaCollection, onBack: () -> Unit, modifier: Modifier = Modifier,
    initialChapterId: String? = null, onReadChapter: ((MangaChapter) -> Unit)? = null,
) {
    val context = LocalContext.current
    val (showReaderStatus, onShowReaderStatusChange) = rememberReaderStatusPreference()
    val vm = remember(manga.subjectId, context) { MangaDetailsViewModel(manga, context) }
    DisposableEffect(vm) { onDispose(vm::close) }
    val state by vm.state.collectAsStateWithLifecycle()
    var initialChapterOpened by remember(manga.subjectId, initialChapterId) { mutableStateOf(false) }
    LaunchedEffect(initialChapterId, state.chapters) {
        if (!initialChapterOpened && initialChapterId != null) {
            state.chapters.firstOrNull { it.id == initialChapterId }?.let {
                initialChapterOpened = true
                vm.openChapter(it)
            }
        }
    }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    val window = LocalPlatformWindow.current
    val scope = rememberCoroutineScope()
    val currentFullscreen by rememberUpdatedState(fullscreen)
    LaunchedEffect(fullscreen) {
        context.setRequestFullScreen(window, fullscreen, lockLandscape = false)
        context.setSystemBarVisible(window, !fullscreen)
    }
    DisposableEffect(context, window) {
        onDispose {
            if (currentFullscreen) {
                context.setSystemBarVisible(window, true)
                scope.launch(NonCancellable) { context.setRequestFullScreen(window, false) }
            }
        }
    }
    var showRelations by rememberSaveable { mutableStateOf(false) }
    val openRelated = rememberOpenBookRelation(manga.subjectId, state.relations, { showRelations = false }, vm::resolveRelation)
    var sourceOpen by rememberSaveable { mutableStateOf(false) }
    var cacheOpen by rememberSaveable { mutableStateOf(false) }
    var chaptersOpen by rememberSaveable { mutableStateOf(false) }
    val actions = MangaPageActions(vm.commentState, vm::setCollection, vm::setProgress, vm::markChapterRead,
        vm::requestEditRating, { sourceOpen = true }, { chaptersOpen = true }, { cacheOpen = true }, vm::saveSourceSettings, vm::solveCaptcha)
    CompositionLocalProvider(LocalMangaActions provides actions) {
    EditableRatingDialogsHost(state.rating, vm)
    state.libraryError?.let { message ->
        AlertDialog(onDismissRequest = vm::dismissLibraryError, title = { Text("收藏与进度") }, text = { Text(message) },
            confirmButton = { TextButton({ vm.dismissLibraryError(); vm.loadLibrary() }) { Text("重试加载") } },
            dismissButton = { TextButton(vm::dismissLibraryError) { Text("关闭") } })
    }
    state.ratingError?.let { message ->
        AlertDialog(onDismissRequest = vm::dismissRatingError, title = { Text("评价") }, text = { Text(message) },
            confirmButton = { TextButton(vm::dismissRatingError) { Text("关闭") } })
    }
    val closeReader = { fullscreen = false; vm.closeReader() }
    BackHandler {
        if (showRelations) showRelations = false
        else if (fullscreen) fullscreen = false
        else if (state.chapter != null) closeReader()
        else onBack()
    }
    if (showRelations) {
        SubjectRelationGraphScreen(SubjectRelationGraphUiState(mangaRelationGraph(manga, state), null),
            vm::loadRelations, { openRelated(it.subjectId) }, modifier, navigationIcon = {
                IconButton({ showRelations = false }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回漫画详情") }
            })
    } else if (state.chapter != null) {
        MangaReaderScreen(state, onBack = { if (fullscreen) fullscreen = false else closeReader() }, onChapter = vm::openChapter,
            onPage = vm::rememberPage, loadImage = vm::image, onReaderLayout = vm::rememberReaderLayout, modifier = modifier,
            fullscreen = fullscreen, onFullscreen = { fullscreen = it }, onComments = vm::loadComments, onRetryDownloads = vm::retryDownloads, ratingActions = vm,
            showReaderStatus = showReaderStatus, onShowReaderStatusChange = onShowReaderStatusChange,
            onRelations = { fullscreen = false; showRelations = true }, onRetryRelations = vm::loadRelations, onRelatedSubject = openRelated)
    } else {
        MangaDetailsContent(manga, state, vm.defaultKeyword(), onBack, vm::loadDetails, vm::loadComments,
            vm::search, vm::selectBook, onReadChapter ?: vm::openChapter, modifier, vm,
            onRelations = { showRelations = true }, onRetryRelations = vm::loadRelations, onRelatedSubject = openRelated, onAutoSelect = vm::autoSelectSource)
    }
    if (sourceOpen) MangaSourcesDialog(state, vm.defaultKeyword(), { sourceOpen = false }, vm::search,
        { vm.selectBook(it); sourceOpen = false }, { vm.autoSelectSource() })
    if (chaptersOpen) MediaSelectorDialog({ chaptersOpen = false }) { _ ->
        LazyColumn(contentPadding = PaddingValues(24.dp)) {
            item { TextButton({ chaptersOpen = false }) { Text("关闭目录") } }
            item { MangaChapterGrid(state, { chaptersOpen = false; (onReadChapter ?: vm::openChapter)(it) }, rows = 5) }
        }
    }
    if (cacheOpen) MangaCacheDialog(state, { cacheOpen = false }, { vm.cacheChapters(it) }, vm::cancelOffline,
        { vm.cacheChapters(listOf(it.chapter), it.book) }, vm::openOffline, vm::deleteOffline)
    }
}

@Composable
internal fun MangaChapterList(
    state: MangaDetailsUiState, initialKeyword: String,
    onSearch: (MangaSource, String) -> Unit, onBook: (MangaSourceBook) -> Unit,
    onChapter: (MangaChapter) -> Unit, onSubscriptions: () -> Unit,
    onAutoSelect: () -> Unit = {},
) {
    var keyword by rememberSaveable { mutableStateOf(initialKeyword) }
    var changeSource by rememberSaveable { mutableStateOf(false) }
    var descending by rememberSaveable { mutableStateOf(true) }
    var group by rememberSaveable { mutableStateOf("章节") }
    val selectedGroup = group.takeIf { selected -> state.chapters.any { it.group == selected } } ?: state.chapters.firstOrNull()?.group.orEmpty()
    val chapters = state.chapters.filter { it.group == selectedGroup }.let { if (descending) it.reversed() else it }
    LazyColumn(Modifier.testTag("manga-chapters"), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionHeader(state.book?.let { "${it.source.title} · ${it.title}" } ?: "选择漫画源") {
                TextButton(onSubscriptions) { Text("订阅链接") }
                TextButton({ changeSource = !changeSource }) { Text(if (changeSource) "收起" else "切换来源") }
            }
        }
        if (state.book == null || changeSource || state.autoSelecting) {
            item {
                MangaSourcePanel(state, keyword, { keyword = it }, onSearch,
                    { onBook(it); changeSource = false }, onAutoSelect)
            }
        }
        state.sourceError?.let { error -> item { MangaInlineError(error) {
            if (state.memory?.sourcePinned == true) state.book?.let(onBook) else onAutoSelect()
        } } }
        if (state.chaptersLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (state.chapters.isNotEmpty()) {
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.chapters.map { it.group }.distinct().forEach { name ->
                        FilterChip(selectedGroup == name, { group = name }, label = { Text(name) })
                    }
                    TextButton({ descending = !descending }) { Text(if (descending) "最新在前" else "最早在前") }
                    Text("${chapters.size} ${if (selectedGroup == "单行本") "卷" else "话"}", Modifier.padding(12.dp))
                }
            }
            items(chapters, key = { it.id }) { chapter ->
                Card({ onChapter(chapter) }, Modifier.fillMaxWidth().testTag("manga-chapter-${chapter.id}")) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(chapter.title, Modifier.weight(1f))
                        Text(if (state.memory?.chapterId == chapter.id) "续读 · 第 ${state.memory.page + 1} 页"
                            else chapter.pageCount.takeIf { it > 0 }?.let { "$it 页" }.orEmpty(), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
internal fun MangaInlineError(message: String, onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(message, color = MaterialTheme.colorScheme.error)
        OutlinedButton(onRetry) { Text("重试") }
    }
}
