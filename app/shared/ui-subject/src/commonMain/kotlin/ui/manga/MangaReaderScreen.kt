/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.manga

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.ui.semantics.onClick
import kotlinx.coroutines.delay
import me.him188.ani.app.data.models.preference.DarkMode
import me.him188.ani.app.ui.foundation.theme.AniTheme
import me.him188.ani.app.ui.subject.episode.EpisodeScreenLayout
import me.him188.ani.app.ui.subject.episode.EpisodeScreenLayoutMode
import me.him188.ani.app.ui.subject.details.sections.SubjectRatingSummary
import me.him188.ani.app.ui.subject.details.sections.SubjectSummarySection
import me.him188.ani.app.ui.subject.details.sections.SubjectInfoTable
import me.him188.ani.app.videoplayer.ui.VideoScaffold
import me.him188.ani.app.videoplayer.ui.PlayerControllerState

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import me.him188.ani.app.ui.mediaselect.common.MediaSelectorDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import me.him188.ani.app.data.repository.manga.MangaChapter
import me.him188.ani.app.data.repository.manga.MangaPage
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.rating.EditableRatingActions

/** Uses the video page layout with a vertically scrolling manga surface. */
@Composable
internal fun MangaReaderScreen(
    state: MangaDetailsUiState, onBack: () -> Unit, onChapter: (MangaChapter) -> Unit,
    onPage: (String, Int) -> Unit, loadImage: suspend (MangaPage) -> String,
    onReaderLayout: (Boolean, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    fullscreen: Boolean = false, onFullscreen: (Boolean) -> Unit = {},
    onComments: () -> Unit = {}, onRetryDownloads: () -> Unit = {},
    ratingActions: EditableRatingActions = EditableRatingActions.Noop,
    onRelations: () -> Unit = {}, onRetryRelations: () -> Unit = {}, onRelatedSubject: (Int) -> Unit = {},
) {
    val chapter = state.chapter ?: return
    val actions = LocalMangaActions.current
    var showChapters by remember { mutableStateOf(false) }
    var detailsTab by rememberSaveable { mutableIntStateOf(0) }
    var readerHeightFraction by rememberSaveable { mutableFloatStateOf(1f) }
    val siblings = state.chapters.filter { it.group == chapter.group }
    val currentIndex = siblings.indexOfFirst { it.id == chapter.id }
    BoxWithConstraints(modifier.fillMaxSize().testTag("manga-reader")) {
        val landscape = maxWidth > maxHeight
        val expanded = maxWidth >= 840.dp || (maxWidth >= 600.dp && maxHeight < 480.dp)
        val mode = when {
            fullscreen -> EpisodeScreenLayoutMode.VIDEO_ONLY
            expanded -> EpisodeScreenLayoutMode.WIDE
            else -> EpisodeScreenLayoutMode.COMPACT
        }
        val collapsible = mode == EpisodeScreenLayoutMode.COMPACT && !landscape
        val expandedReaderHeight = maxHeight * 0.7f
        val expandedReaderHeightPx = with(LocalDensity.current) { expandedReaderHeight.toPx() }
        // Only the details list dispatches to this connection. Reading gestures stay independent.
        val detailsScroll = remember(collapsible, expandedReaderHeightPx) {
            object : NestedScrollConnection {
                fun resize(delta: Float): Offset {
                    if (!collapsible || expandedReaderHeightPx <= 0f) return Offset.Zero
                    val previous = readerHeightFraction * expandedReaderHeightPx
                    val next = (previous + delta).coerceIn(0f, expandedReaderHeightPx)
                    readerHeightFraction = next / expandedReaderHeightPx
                    return Offset(0f, next - previous)
                }

                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                    if (available.y < 0f) resize(available.y) else Offset.Zero

                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                    if (available.y > 0f) resize(available.y) else Offset.Zero
            }
        }
        EpisodeScreenLayout(mode, video = {
            AniTheme(darkModeOverride = DarkMode.DARK) {
                Box(Modifier.then(when {
                    collapsible -> Modifier.fillMaxWidth().height(expandedReaderHeight * readerHeightFraction)
                    mode == EpisodeScreenLayoutMode.COMPACT -> Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                    else -> Modifier.fillMaxSize()
                }).clipToBounds().background(Color(0xff101014)).testTag("manga-reading-pane")) {
                    if (state.pages.isNotEmpty()) key(chapter.id, state.pages) {
                        MangaReaderPages(state.pages, state.initialPage,
                            onPage = { onPage(chapter.id, it) }, loadImage = loadImage,
                            onPreviousChapter = siblings.getOrNull(currentIndex - 1)?.let { { onChapter(it) } },
                            onNextChapter = siblings.getOrNull(currentIndex + 1)?.let { { onChapter(it) } },
                            initialRightToLeft = state.memory?.rightToLeft ?: true,
                            initialFirstPageSingle = state.memory?.firstPageSingle ?: false,
                            onReaderLayout = onReaderLayout, modifier = Modifier.fillMaxSize(),
                            title = "${state.book?.title.orEmpty()} · ${chapter.title}", onBack = onBack,
                            fullscreen = fullscreen, onFullscreen = onFullscreen,
                            onChapters = { showChapters = true }, onSources = actions.showSources,
                            onReadCompleted = { actions.markRead(chapter) }, spreadOverride = landscape)
                    } else {
                        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                            if (state.pagesLoading) CircularProgressIndicator()
                            state.pagesError?.let { MangaInlineError(it) { onChapter(chapter) } }
                            TextButton(onBack) { Text("返回漫画详情") }
                        }
                    }
                }
            }
        }, secondary = {
            MangaReaderDetails(state, onComments, { showChapters = true }, onBack, onRetryDownloads, detailsTab, { detailsTab = it }, ratingActions, onRelations, onRetryRelations, onRelatedSubject,
                scrollModifier = Modifier.nestedScroll(detailsScroll), pullToRefreshEnabled = !collapsible || readerHeightFraction >= 1f)
        }, modifier = Modifier.fillMaxSize().testTag("manga-player-layout-$mode"))
    }
    if (showChapters) MediaSelectorDialog({ showChapters = false }) { _ ->
        LazyColumn(contentPadding = PaddingValues(24.dp)) {
            item { TextButton({ showChapters = false }) { Text("关闭目录") } }
            item { MangaChapterGrid(state, { showChapters = false; onChapter(it) }, rows = 5) }
        }
    }
}

@Composable
private fun MangaReaderDetails(state: MangaDetailsUiState, onComments: () -> Unit,
    onChapters: () -> Unit, onBack: () -> Unit, onRetryDownloads: () -> Unit, tab: Int, onTab: (Int) -> Unit,
    ratingActions: EditableRatingActions, onRelations: () -> Unit, onRetryRelations: () -> Unit, onRelatedSubject: (Int) -> Unit,
    scrollModifier: Modifier = Modifier, pullToRefreshEnabled: Boolean = true,
) {
    val actions = LocalMangaActions.current
    Surface(Modifier.fillMaxSize().testTag("manga-reader-details")) {
        Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
            SecondaryTabRow(tab) {
                Tab(tab == 0, { onTab(0) }, text = { Text("详情") }, modifier = Modifier.testTag("manga-reader-details-tab"))
                Tab(tab == 1, { onTab(1) }, text = { Text("评论 (${actions.comments?.count ?: state.commentsTotal})") }, modifier = Modifier.testTag("manga-reader-comments-tab"))
            }
            if (tab == 1) MangaReviewFeed(state, Modifier.then(scrollModifier).testTag("manga-reader-details-scroll"), pullToRefreshEnabled)
            else LazyColumn(Modifier.fillMaxSize().then(scrollModifier).testTag("manga-reader-details-scroll"),
                contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (tab == 0) {
                    item { Text(state.details?.title ?: state.book?.title.orEmpty(), style = MaterialTheme.typography.titleLarge) }
                    item { Text("${state.chapter?.title.orEmpty()} · ${state.book?.source?.title.orEmpty()}") }
                    item { Row {
                        TextButton(onChapters) { Text("章节目录") }
                        TextButton(onBack) { Text("作品详情") }
                        TextButton(actions.showSources, Modifier.testTag("manga-reader-source")) { Text("数据源") }
                        TextButton(actions.showCache) { Text("缓存") }
                    } }
                    item { MangaLibraryControls(state) }
                    item {
                        Column(Modifier.testTag("manga-download-status"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            state.downloads.forEachIndexed { index, download ->
                                Text("${if (index == 0) "本话" else "下一话"} · ${download.title}：" +
                                    if (download.total > 0 && download.completed == download.total) "已缓存 ${download.total} 页"
                                    else "缓存 ${download.completed} / ${if (download.total > 0) download.total else "…"} 页",
                                    style = MaterialTheme.typography.bodySmall)
                                download.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                            }
                            if (state.downloads.any { it.error != null }) TextButton(onRetryDownloads) { Text("重试缓存") }
                        }
                    }
                    state.details?.let { details ->
                        item { SubjectRatingSummary(details.rating, onClick = ratingActions::requestEditRating) }
                        item { MangaRatingEntry(state, ratingActions) }
                        item { MangaRelatedSection(state, onRelations, onRetryRelations, onRelatedSubject) }
                        item { SubjectSummarySection(details.summary.ifBlank { "暂无简介" }) }
                        item { SubjectInfoTable(details.info) }
                    }
                }
            }
        }
    }
}

/** Page indices remain in reading order; visual left/right order is applied by the row. */
internal fun mangaReaderRows(pageCount: Int, spread: Boolean, firstPageSingle: Boolean): List<List<Int>> {
    if (!spread) return List(pageCount) { listOf(it) }
    val start = if (firstPageSingle && pageCount > 0) 1 else 0
    return buildList {
        if (start == 1) add(listOf(0))
        addAll((start until pageCount).toList().chunked(2))
    }
}

@Composable
internal fun MangaReaderPages(
    pages: List<MangaPage>, initialPage: Int, onPage: (Int) -> Unit, loadImage: suspend (MangaPage) -> String,
    onPreviousChapter: (() -> Unit)?, onNextChapter: (() -> Unit)?, modifier: Modifier = Modifier,
    initialRightToLeft: Boolean = true, initialFirstPageSingle: Boolean = false,
    onReaderLayout: (Boolean, Boolean) -> Unit = { _, _ -> },
    spreadOverride: Boolean? = null,
    title: String = "", onBack: () -> Unit = {}, onChapters: () -> Unit = {}, onSources: () -> Unit = {},
    onReadCompleted: () -> Unit = {},
    fullscreen: Boolean = false, onFullscreen: (Boolean) -> Unit = {},
) {
    if (pages.isEmpty()) return
    var rightToLeft by rememberSaveable { mutableStateOf(initialRightToLeft) }
    var firstPageSingle by rememberSaveable { mutableStateOf(initialFirstPageSingle) }
    var currentPage by rememberSaveable { mutableIntStateOf(initialPage.coerceIn(pages.indices)) }
    var slider by remember { mutableStateOf<Float?>(null) }
    var decodedPages by remember(pages) { mutableStateOf(emptySet<Int>()) }
    val currentReadCompleted by rememberUpdatedState(onReadCompleted)
    val controller = remember(fullscreen) { PlayerControllerState() }
    var showSettings by remember { mutableStateOf(false) }
    var interaction by remember { mutableIntStateOf(0) }
    val toggleControls: () -> Unit = { controller.toggleFullVisible(); interaction++ }
    LaunchedEffect(controller.visibility, controller.alwaysOn, slider, interaction, showSettings) {
        if (controller.visibility.bottomBar && !controller.alwaysOn && slider == null && !showSettings) {
            delay(5000)
            controller.toggleFullVisible(false)
        }
    }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    BoxWithConstraints(modifier) {
        val spread = spreadOverride ?: (maxWidth > maxHeight)
        val rows = remember(pages.size, spread, firstPageSingle) { mangaReaderRows(pages.size, spread, firstPageSingle) }
        val listState = remember(rows) { LazyListState(rows.indexOfFirst { currentPage in it }.coerceAtLeast(0)) }
        val currentRow = listState.firstVisibleItemIndex.coerceIn(rows.indices)
        LaunchedEffect(listState) {
            snapshotFlow { listState.firstVisibleItemIndex }.distinctUntilChanged().collect { row ->
                currentPage = rows[row.coerceIn(rows.indices)].first()
                onPage(currentPage)
            }
        }
        LaunchedEffect(currentRow, rows, decodedPages) {
            if (currentRow == rows.lastIndex && rows.last().all { it in decodedPages }) {
                delay(2000)
                currentReadCompleted()
            }
        }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
        fun move(delta: Int) {
            if (listState.isScrollInProgress) return
            val target = (currentRow + delta).coerceIn(rows.indices)
            if (target == currentRow) return
            interaction++
            scope.launch {
                val layout = listState.layoutInfo
                val targetOffset = layout.visibleItemsInfo.firstOrNull { it.index == target }?.offset
                    ?: if (delta > 0) layout.visibleItemsInfo.firstOrNull { it.index == currentRow }?.let {
                        it.offset + it.size + layout.mainAxisItemSpacing
                    } else null
                if (targetOffset != null) {
                    listState.animateScrollBy(targetOffset.toFloat(), tween(320, easing = FastOutSlowInEasing))
                } else {
                    listState.animateScrollToItem(target)
                }
            }
        }
        val tapPage: (Float) -> Unit = { fraction ->
            if (fullscreen && (fraction < 1f / 3f || fraction > 2f / 3f)) {
                controller.toggleFullVisible(false)
                if (!listState.isScrollInProgress && currentRow < rows.lastIndex) move(1)
            } else toggleControls()
        }
        Box(Modifier.fillMaxSize().focusRequester(focusRequester).onPreviewKeyEvent {
            if (it.type != KeyEventType.KeyDown) false
            else when (it.key) {
                Key.DirectionDown, Key.PageDown -> { move(1); true }
                Key.DirectionUp, Key.PageUp -> { move(-1); true }
                Key.Spacebar -> { toggleControls(); true }
                Key.F -> { onFullscreen(!fullscreen); true }
                Key.Escape -> { if (fullscreen) onFullscreen(false) else controller.toggleFullVisible(false); true }
                else -> false
            }
        }.focusable()) {
            VideoScaffold(expanded = true, maintainAspectRatio = false, controllerState = controller,
                modifier = Modifier.fillMaxSize(), contentWindowInsets = WindowInsets.safeDrawing,
                topBar = {
                    IconButton(onBack, Modifier.testTag("manga-reader-back")) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回漫画详情") }
                    Text(title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall)
                    TextButton(onChapters) { Text("目录") }
                    TextButton(onSources, Modifier.testTag("manga-reader-source-overlay")) { Text("数据源") }
                    IconButton({ showSettings = true }, Modifier.testTag("manga-reader-settings")) { Icon(Icons.Rounded.Settings, "阅读设置") }
                    IconButton({ onFullscreen(!fullscreen) }, Modifier.testTag("manga-reader-fullscreen")) {
                        Icon(if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen, if (fullscreen) "退出全屏" else "全屏")
                    }

                },
                video = {
            BoxWithConstraints(Modifier.fillMaxSize().pointerInput(controller) { detectTapGestures(onTap = { toggleControls() }) }
                .semantics { onClick(label = "显示或隐藏阅读控制栏") { toggleControls(); true } }) {
            val viewportHeight = maxHeight
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag("manga-reader-scroll").semantics { onClick { toggleControls(); true } },
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                itemsIndexed(rows, key = { _, row -> row.joinToString("-") }) { rowIndex, row ->
                    var imageRowHeight by remember { mutableStateOf<Int?>(null) }
                    val density = LocalDensity.current
                    Column(Modifier.fillMaxWidth().heightIn(min = if (rowIndex == rows.lastIndex) viewportHeight else 0.dp)
                        .testTag("manga-reader-row-$rowIndex")) {
                    Row(Modifier.fillMaxWidth().onSizeChanged { imageRowHeight = it.height }, verticalAlignment = Alignment.Top) {
                        val visualOrder = if (rightToLeft) row.reversed() else row
                        val needsEmptySlot = spread && row.size == 1 && !(firstPageSingle && rowIndex == 0)
                        if (needsEmptySlot && rightToLeft) Spacer(Modifier.weight(1f))
                        visualOrder.forEachIndexed { slot, index ->
                            MangaReaderImage(pages[index], index + 1, currentRow == rowIndex, loadImage,
                                Modifier.weight(1f).testTag("manga-reader-page-slot-${index + 1}"), onTap = { fraction ->
                                    val slots = if (needsEmptySlot) 2 else row.size
                                    val offset = if (needsEmptySlot && rightToLeft) 1 else 0
                                    tapPage((offset + slot + fraction) / slots)
                                }, onDecoded = { decodedPages = decodedPages + index })
                        }
                        if (needsEmptySlot && !rightToLeft) Spacer(Modifier.weight(1f))
                    }
                    if (rowIndex == rows.lastIndex) {
                        val minimumGap = if (fullscreen) 96.dp else 0.dp
                        val gapHeight = imageRowHeight?.let { (viewportHeight - with(density) { it.toDp() }).coerceAtLeast(minimumGap) } ?: minimumGap
                        if (gapHeight > 0.dp) Box(Modifier.fillMaxWidth().height(gapHeight)
                            .testTag("manga-reader-bottom-gap")
                            .then(if (fullscreen && onNextChapter != null) Modifier.clickable(
                                interactionSource = null, indication = null, onClickLabel = "下一话", onClick = onNextChapter,
                            ) else Modifier))
                    }
                    }
                }
            }
            }
                }, bottomBar = {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp).testTag("manga-reader-controls")) {
                    if (pages.size > 1) Slider(value = slider ?: currentPage.toFloat(),
                        onValueChange = { slider = it; interaction++ }, valueRange = 0f..pages.lastIndex.toFloat(),
                        onValueChangeFinished = {
                            val target = slider?.toInt(); slider = null
                            if (target != null) scope.launch { listState.scrollToItem(rows.indexOfFirst { target in it }.coerceAtLeast(0)) }
                        }, modifier = Modifier.testTag("manga-reader-slider"))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        TextButton({ onPreviousChapter?.invoke() }, enabled = onPreviousChapter != null, modifier = Modifier.testTag("manga-previous-chapter")) { Text("上一话") }
                        TextButton({ move(-1) }, enabled = currentRow > 0, modifier = Modifier.testTag("manga-previous-page")) { Text(if (spread) "上一组" else "上一页") }
                        val row = rows[currentRow]
                        Text(if (row.size == 1) "${row.first() + 1} / ${pages.size}" else "${row.first() + 1}–${row.last() + 1} / ${pages.size}", Modifier.testTag("manga-reader-page"))
                        TextButton({ move(1) }, enabled = currentRow < rows.lastIndex, modifier = Modifier.testTag("manga-next-page")) { Text(if (spread) "下一组" else "下一页") }
                        TextButton({ onNextChapter?.invoke() }, enabled = onNextChapter != null, modifier = Modifier.testTag("manga-next-chapter")) { Text("下一话") }
                    }
                }
            })

        }
    }
    if (showSettings) AlertDialog(onDismissRequest = { showSettings = false }, title = { Text("阅读设置") },
        text = { Column {
            TextButton({ rightToLeft = !rightToLeft; onReaderLayout(rightToLeft, firstPageSingle) },
                Modifier.testTag("manga-reading-order")) { Text(if (rightToLeft) "先右后左" else "先左后右") }
            TextButton({ firstPageSingle = !firstPageSingle; onReaderLayout(rightToLeft, firstPageSingle) },
                Modifier.testTag("manga-spread-offset")) { Text(if (firstPageSingle) "配对：首张单独" else "配对：从首张开始") }
        } }, confirmButton = { TextButton({ showSettings = false }, Modifier.testTag("manga-reader-settings-close")) { Text("完成") } })
}

@Composable
internal fun MangaReaderImage(page: MangaPage, number: Int, active: Boolean,
    loadImage: suspend (MangaPage) -> String, modifier: Modifier = Modifier, onTap: (Float) -> Unit = {}, onDecoded: () -> Unit = {},
) {
    val currentOnTap by rememberUpdatedState(onTap)
    var uri by remember(page) { mutableStateOf<String?>(null) }
    var error by remember(page) { mutableStateOf<String?>(null) }
    var loaded by remember(page) { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    var transform by remember(page) { mutableStateOf(MangaImageTransform()) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var aspect by remember(page) { mutableFloatStateOf(if (page.width > 0 && page.height > 0) page.width.toFloat() / page.height else 0.707f) }
    LaunchedEffect(page, revision) {
        error = null; uri = null; loaded = false
        try { uri = loadImage(page) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "图片加载失败" }
    }
    LaunchedEffect(active) { if (!active) transform = MangaImageTransform() }
    val gestures = rememberTransformableState { centroid: Offset, zoom: Float, pan: Offset, _: Float ->
        transform = transform.transform(viewport, centroid, zoom, pan)
    }
    val gestureModifier = if (uri != null) Modifier.pointerInput(page) {
            detectTapGestures(onTap = { currentOnTap((it.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)) }, onDoubleTap = { position ->
                transform = if (transform.scale > 1f) MangaImageTransform()
                else transform.transform(viewport, position, 2.5f)
            })

        }.transformable(gestures, canPan = { transform.scale > 1f }) else Modifier
    // Gestures use the untransformed viewport so an existing zoom never rescales touch coordinates.
    Box(modifier) {
    Box(Modifier.fillMaxWidth().aspectRatio(aspect).onSizeChanged { viewport = it }
        .testTag("manga-page-viewport-$number")
        .semantics { this[MangaImageScale] = transform.scale; this[MangaImageTranslation] = transform.translation }
        .clipToBounds().background(Color(0xff101014)).then(gestureModifier), contentAlignment = Alignment.Center) {
        val imageUri = uri
        if (imageUri != null) key(revision) {
            AsyncImage(imageUri, "漫画第 $number 页", Modifier.fillMaxSize().testTag("manga-page-image-$number")
                .semantics { stateDescription = if (loaded) "decoded" else "loading" }
                .graphicsLayer { scaleX = transform.scale; scaleY = transform.scale
                    translationX = transform.translation.x; translationY = transform.translation.y },
                contentScale = ContentScale.Fit, onSuccess = { loaded = true; onDecoded(); if (it.width > 0 && it.height > 0) aspect = it.width.toFloat() / it.height },
                onError = { error = "图片解码失败，请重试" })
        }
        if (error != null) Surface(Modifier.padding(24.dp), shape = MaterialTheme.shapes.medium) {
            Box(Modifier.padding(20.dp)) { MangaInlineError(error.orEmpty()) { revision++ } }
        } else if (!loaded) CircularProgressIndicator()
    }
    }
}
