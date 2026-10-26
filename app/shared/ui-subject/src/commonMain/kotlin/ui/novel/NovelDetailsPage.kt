/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.novel

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.repository.manga.MangaCollection
import me.him188.ani.app.data.repository.novel.NovelSource
import me.him188.ani.app.data.repository.novel.NovelChapter
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.foundation.layout.LocalPlatformWindow
import me.him188.ani.app.ui.foundation.layout.setRequestFullScreen
import me.him188.ani.app.ui.foundation.layout.setSystemBarVisible
import me.him188.ani.app.ui.foundation.navigation.BackHandler
import me.him188.ani.app.ui.manga.*
import me.him188.ani.app.ui.mediaselect.common.MediaSelectorDialog
import me.him188.ani.app.ui.subject.relations.SubjectRelationGraphScreen
import me.him188.ani.app.ui.subject.relations.SubjectRelationGraphUiState
import me.him188.ani.app.ui.rating.EditableRatingDialogsHost

@Composable
fun NovelDetailsRoute(
    book: MangaCollection, onBack: () -> Unit, modifier: Modifier = Modifier,
    startReading: Boolean = false, initialChapterId: String? = null,
    onReadRequested: ((String?) -> Unit)? = null,
) {
    val context = LocalContext.current
    val social = remember(book.subjectId, context) { MangaDetailsViewModel(book, context, enableReading = false) }
    val vm = remember(book.subjectId, context) { NovelViewModel(book.subjectId, context, book.title, book.coverUrl) }
    DisposableEffect(vm, social) { onDispose { vm.close(); social.close() } }
    val state by vm.state.collectAsStateWithLifecycle()
    val details by social.state.collectAsStateWithLifecycle()
    val downloads by vm.downloads.state.collectAsStateWithLifecycle()
    var initialReadingOpened by remember(book.subjectId, startReading, initialChapterId) { mutableStateOf(false) }
    LaunchedEffect(startReading, initialChapterId, state.chapters) {
        if (startReading && !initialReadingOpened && state.chapters.isNotEmpty()) {
            initialReadingOpened = true
            val chapter = state.chapters.firstOrNull { it.id == initialChapterId }
            if (chapter != null) vm.open(chapter) else vm.resume()
        }
    }
    val read: () -> Unit = { if (onReadRequested != null) onReadRequested(null) else vm.resume() }
    val openChapter: (NovelChapter) -> Unit = { chapter ->
        if (onReadRequested != null) onReadRequested(chapter.id) else vm.open(chapter)
    }
    LaunchedEffect(details.details) {
        details.details?.let { vm.updateAliases(listOf(it.title, it.originalTitle) + it.aliases) }
    }
    val scope = rememberCoroutineScope()
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var sources by rememberSaveable { mutableStateOf(false) }
    var chapters by rememberSaveable { mutableStateOf(false) }
    var files by rememberSaveable { mutableStateOf(false) }
    var cache by rememberSaveable { mutableStateOf(false) }
    var showRelations by rememberSaveable { mutableStateOf(false) }
    val openRelated = rememberOpenBookRelation(book.subjectId, details.relations, { showRelations = false }, social::resolveRelation)
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val window = LocalPlatformWindow.current
    val currentFullscreen by rememberUpdatedState(fullscreen)
    LaunchedEffect(fullscreen) {
        context.setRequestFullScreen(window, fullscreen, lockLandscape = false)
        context.setSystemBarVisible(window, !fullscreen)
    }
    DisposableEffect(context, window) { onDispose {
        if (currentFullscreen) {
            context.setSystemBarVisible(window, true)
            scope.launch(NonCancellable) { context.setRequestFullScreen(window, false) }
        }
    } }
    val closeReader = { fullscreen = false; vm.closeReader() }
    BackHandler {
        if (showRelations) showRelations = false
        else if (fullscreen) fullscreen = false
        else if (state.chapter != null) closeReader()
        else onBack()
    }
    // Android's MIME registry does not recognize AZW3; validate the extension after selection.
    val picker = rememberFilePickerLauncher(type = FileKitType.File()) { file ->
        if (file != null) scope.launch {
            try { withContext(Dispatchers.IO) { vm.import(file.name, file.readBytes()) } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { vm.reportImportError(e.message ?: "无法读取文件") }
        }
    }
    val actions = MangaPageActions(comments = social.commentState, setCollection = social::setCollection,
        setProgress = social::setProgress, writeReview = social::requestEditRating,
        showSources = { sources = true }, showChapters = { chapters = true }, showCache = { cache = true })
    CompositionLocalProvider(LocalMangaActions provides actions, LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        EditableRatingDialogsHost(details.rating, social)
        val information: @Composable (Modifier, Boolean) -> Unit = { scroll, refresh ->
            if (book.subjectId > 0) NovelDetailsContent(
                book, details, state, onBack = if (state.chapter != null) closeReader else onBack,
                onRetry = social::loadDetails, onRead = read, onChapter = openChapter,
                onSources = { sources = true }, onChapters = { chapters = true },
                onImport = { picker.launch() }, onFiles = { files = true }, onCache = { cache = true },
                modifier = scroll, ratingActions = social,
                onRelations = { fullscreen = false; showRelations = true }, onRetryRelations = social::loadRelations,
                onRelatedSubject = openRelated,
            ) else Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                SecondaryTabRow(tab) {
                    Tab(tab == 0, { tab = 0 }, text = { Text("详情") })
                    if (book.subjectId > 0) Tab(tab == 1, { tab = 1 }, text = { Text("评论") })
                }
                if (tab == 1) MangaReviewFeed(details, Modifier.then(scroll), refresh)
                else LazyColumn(Modifier.fillMaxSize().then(scroll), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { Text(details.details?.title ?: book.title, style = MaterialTheme.typography.headlineSmall) }
                    item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AsyncImage(details.details?.cover ?: book.coverUrl, "封面", Modifier.width(100.dp).height(140.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(state.book?.let { "${state.sourceSettings.title(it.source)} · ${it.title}" } ?: "请选择书源或导入电子书")
                            Button(read, enabled = state.chapters.isNotEmpty() && !state.loading, modifier = Modifier.testTag("novel-read")) { Text("继续阅读") }
                            TextButton({ picker.launch() }, Modifier.testTag("novel-import"), enabled = !state.loading) { Text("导入电子书") }
                        }
                    } }
                    item { FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton({ sources = true }) { Text("选择书源") }
                        TextButton({ chapters = true }, enabled = state.chapters.isNotEmpty()) { Text("目录（${state.chapters.size}）") }
                        TextButton({ cache = true }) { Text("缓存") }
                        if (state.localBooks.isNotEmpty()) TextButton({ files = true }) { Text("已导入文件") }
                        if (state.chapter != null) TextButton(closeReader) { Text("退出阅读") }
                    } }
                    if (state.loading || (book.subjectId > 0 && details.loading)) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
                    details.error?.let { item { MangaInlineError(it, social::loadDetails) } }
                    if (state.readAhead.isNotBlank()) item { TextButton({ state.chapter?.let(vm::open) }) { Text(state.readAhead) } }
                    if (book.subjectId > 0) item { MangaLibraryControls(details, chapterUnit = "章") }
                    details.libraryError?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
                    if (book.subjectId > 0) item { MangaRatingEntry(details, social) }
                    details.ratingError?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
                    details.details?.let { info ->
                        item { Text(info.summary.ifBlank { "暂无简介" }) }
                        items(info.info) { (label, value) -> Text("$label：$value") }
                    }
                }
            }
        }
        if (showRelations) {
            SubjectRelationGraphScreen(SubjectRelationGraphUiState(mangaRelationGraph(book, details), null),
                social::loadRelations, { openRelated(it.subjectId) }, modifier,
                navigationIcon = {
                    IconButton({ showRelations = false }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回小说详情") }
                })
        } else if (state.chapter != null) {
            val chapter = state.chapter!!
            val index = state.chapters.indexOf(chapter)
            MangaReaderLayout(fullscreen, modifier, reader = { _ ->
                key(chapter.id) {
                    if (state.blocks.isNotEmpty()) NovelReader(state.blocks, state.initialParagraph, state.initialOffset,
                        chapter.title, state.settings, vm::settings, vm::rememberPosition,
                        onCompleted = {
                            val progress = details.library
                            val number = chapter.number
                            if (progress != null && number != null && number > progress.chapters) social.setProgress(number, progress.volumes)
                        }, onBack = { if (fullscreen) fullscreen = false else closeReader() },
                        onChapters = { chapters = true }, onSources = { sources = true },
                        onPrevious = state.chapters.getOrNull(index - 1)?.let { { vm.open(it) } },
                        onNext = state.chapters.getOrNull(index + 1)?.let { { vm.open(it) } },
                        fullscreen = fullscreen, onFullscreen = { fullscreen = it })
                    else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (state.loading) CircularProgressIndicator()
                            state.error?.let { MangaInlineError(it) { vm.open(chapter) } }
                            TextButton(closeReader) { Text("返回小说详情") }
                        }
                    }
                }
            }, details = information)
        } else if (book.subjectId > 0) information(modifier, true)
        else Scaffold(modifier, topBar = {
            TopAppBar(title = { Text("小说详情") }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回追文") } })
        }) { padding -> Box(Modifier.padding(padding)) { information(Modifier, true) } }
    }
    if (files) MediaSelectorDialog({ files = false }) { _ ->
        LazyColumn(contentPadding = PaddingValues(20.dp)) {
            item { Text("已导入的电子书", style = MaterialTheme.typography.titleLarge) }
            items(state.localBooks, key = { it.id }) { local ->
                TextButton({ vm.selectLocal(local); files = false }, Modifier.fillMaxWidth()) { Text(local.title, Modifier.fillMaxWidth()) }
            }
        }
    }
    if (chapters) MediaSelectorDialog({ chapters = false }) { _ ->
        var query by remember { mutableStateOf("") }
        Column(Modifier.padding(20.dp)) {
            OutlinedTextField(query, { query = it }, label = { Text("查找章节") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            LazyColumn {
                items(state.chapters.filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }, key = { it.id }) { chapter ->
                    TextButton({ chapters = false; openChapter(chapter) }, Modifier.fillMaxWidth()) { Text(chapter.title, Modifier.fillMaxWidth()) }
                }
            }
        }
    }
    if (sources) NovelSourcesDialog(state, book.title, { sources = false }, vm::search,
        { vm.select(it); sources = false }, vm::autoSelect, vm::saveSourceSettings, vm::loginZlibrary, vm::loadSourceSearches)
    if (cache) MediaSelectorDialog({ cache = false }) { _ ->
        var delete by remember { mutableStateOf<NovelDownload?>(null) }
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("小说缓存", style = MaterialTheme.typography.titleLarge)
            FlowRow {
                TextButton({ state.book?.let { vm.downloads.download(it, state.chapters) } }, enabled = state.book != null && state.book?.source != NovelSource.LOCAL) { Text("缓存全部章节") }
                TextButton({ state.book?.let { book -> state.chapter?.let { vm.downloads.download(book, listOf(it)) } } }, enabled = state.chapter != null && state.book?.source != NovelSource.LOCAL) { Text("缓存本章") }
                TextButton({ if (downloads.running) vm.downloads.pause() else vm.downloads.resume() }) { Text(if (downloads.running) "暂停" else "继续／重试") }
            }
            if (state.book?.source == NovelSource.LOCAL) Text("导入的电子书已保存在本机，可离线阅读")
            LazyColumn {
                items(downloads.entries, key = { "${it.book.source}:${it.book.id}:${it.chapter.id}" }) { entry ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(entry.chapter.title); Text(if (entry.file != null) "已缓存" else entry.error ?: "等待下载") }
                        TextButton({ delete = entry }, enabled = !downloads.running) { Text("删除") }
                    }
                }
            }
        }
        delete?.let { entry -> AlertDialog(onDismissRequest = { delete = null }, title = { Text("删除这一章的离线缓存？") },
            confirmButton = { TextButton({ vm.downloads.remove(entry); delete = null }) { Text("删除") } },
            dismissButton = { TextButton({ delete = null }) { Text("取消") } }) }
    }
    ZlibraryLoginDialog(vm.zlibraryLogin)
}
