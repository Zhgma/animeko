/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.manga

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import me.him188.ani.app.ui.comment.CommentItem
import me.him188.ani.app.ui.comment.CommentMapperContext
import me.him188.ani.app.ui.comment.CommentMenuHandlers
import me.him188.ani.app.ui.comment.UIComment
import me.him188.ani.app.data.models.UserInfo
import me.him188.ani.app.ui.foundation.ImageViewer
import me.him188.ani.app.ui.foundation.ImageViewerBackHandler
import me.him188.ani.app.ui.foundation.rememberImageViewerHandler
import me.him188.ani.app.ui.rating.EditableRatingActions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.models.subject.RatingInfo
import me.him188.ani.app.data.models.subject.SubjectCollectionStats
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.models.subject.Tag
import me.him188.ani.app.data.repository.manga.MangaChapter
import me.him188.ani.app.data.repository.manga.MangaCollection
import me.him188.ani.app.data.repository.manga.MangaSource
import me.him188.ani.app.data.repository.manga.MangaSourceBook
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.subject.details.SubjectDetailsSingleColumnPage
import me.him188.ani.app.ui.subject.details.components.RatingHistogram
import me.him188.ani.app.ui.subject.details.layout.MultiColumnScaffold
import me.him188.ani.app.ui.subject.details.layout.RailCard
import me.him188.ani.app.ui.subject.details.layout.SubjectDetailsLayoutParams
import me.him188.ani.app.ui.subject.details.sections.SectionHeader
import me.him188.ani.app.ui.subject.details.sections.SubjectCollectionStatsRow
import me.him188.ani.app.ui.subject.details.sections.SubjectInfoTable
import me.him188.ani.app.ui.subject.details.sections.SubjectRatingSummary
import me.him188.ani.app.ui.subject.details.sections.SubjectSummarySection
import me.him188.ani.app.ui.subject.details.sections.SubjectTagsSection

/** Manga data stays independent; page geometry and presentation use the subject detail components. */
@Composable
internal fun MangaDetailsContent(
    manga: MangaCollection, state: MangaDetailsUiState, initialKeyword: String,
    onBack: () -> Unit, onRetry: () -> Unit, onComments: () -> Unit,
    onSearch: (MangaSource, String) -> Unit, onBook: (MangaSourceBook) -> Unit,
    onChapter: (MangaChapter) -> Unit, modifier: Modifier = Modifier,
    ratingActions: EditableRatingActions = EditableRatingActions.Noop,
    onRelations: () -> Unit = {}, onRetryRelations: () -> Unit = {},
    onRelatedSubject: (Int) -> Unit = {},
    onAutoSelect: () -> Unit = {},
) {
    var commentsSheet by rememberSaveable { mutableStateOf(false) }
    val pager = rememberPagerState { 2 }
    val tabScope = rememberCoroutineScope()
    val actions = LocalMangaActions.current
    val details = state.details
    val counts = details?.collections.orEmpty()
    val stats = SubjectCollectionStats(counts["wish"] ?: 0, counts["doing"] ?: 0,
        counts["collect"] ?: 0, counts["on_hold"] ?: 0, counts["dropped"] ?: 0)
    val rating = details?.rating ?: RatingInfo.Empty
    // A display-only adapter for the compact header; never stored or sent to anime services.
    val headerInfo = SubjectInfo.Empty.copy(subjectId = manga.subjectId,
        name = details?.originalTitle ?: manga.originalTitle, nameCn = details?.title ?: manga.title,
        imageLarge = details?.cover ?: manga.coverUrl, ratingInfo = rating)
    val uriHandler = LocalUriHandler.current
    val openExternal = { uriHandler.openUri("https://bgm.tv/subject/${manga.subjectId}") }
    val navigation: @Composable () -> Unit = {
        IconButton(onBack, Modifier.testTag("manga-details-back")) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回追漫")
        }
    }
    val read: @Composable () -> Unit = {
        Button({
            val chapter = state.chapters.firstOrNull { it.id == state.memory?.chapterId }
            if (chapter != null) onChapter(chapter) else if (state.book == null) actions.showSources() else actions.showChapters()
        }, Modifier.fillMaxWidth().testTag("manga-start-reading")) {
            Text(if (state.memory?.chapterId != null) "继续阅读" else "选择章节")
        }
    }
    val information: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("作品信息", style = MaterialTheme.typography.titleMedium)
            SubjectInfoTable(details?.info.orEmpty())
        }
    }
    val tags: @Composable () -> Unit = {
        SubjectTagsSection(details?.tags.orEmpty().map { Tag(it, 0) }, onClickTag = { })
    }
    val loadState: @Composable () -> Unit = {
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.usingBangumiCache) Text("正在使用 Bangumi 缓存，可继续选源阅读", style = MaterialTheme.typography.bodySmall)
        state.error?.let { MangaInlineError(it, onRetry) }
    }
    val related: @Composable () -> Unit = {
        MangaRelatedSection(state, onRelations, onRetryRelations, onRelatedSubject)
    }
    val chapters: @Composable () -> Unit = {
        MangaChapterGrid(state, onChapter)
    }
    BoxWithConstraints(modifier.fillMaxSize().testTag("manga-details")) {
        val layout = SubjectDetailsLayoutParams.calculate(maxWidth)
        if (layout.isMultiColumn) {
            MultiColumnScaffold(layout, Modifier.testTag("manga-layout-${layout.kind}"),
                navigationIcon = navigation, onClickOpenExternal = openExternal, topBarTitle = headerInfo.displayName) {
                Column(Modifier.width(layout.sidebarWidth).testTag("manga-detail-sidebar"),
                    verticalArrangement = Arrangement.spacedBy(layout.sidebarItemSpacing)) {
                    AsyncImage(headerInfo.imageLarge, headerInfo.displayName,
                        Modifier.fillMaxWidth().aspectRatio(849f / 1200f).clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop)
                    read()
                    MangaLibraryControls(state)
                    SubjectCollectionStatsRow(stats, doingLabel = "在读", wishLabel = "想读")
                    HorizontalDivider()
                    information()
                    tags()
                }
                Column(Modifier.weight(1f).testTag("manga-detail-main"),
                    verticalArrangement = Arrangement.spacedBy(layout.sectionSpacing)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(headerInfo.displayName, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(headerInfo.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("漫画", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    loadState()
                    if (!layout.showRail) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            SubjectRatingSummary(rating, onClick = ratingActions::requestEditRating)
                            if (layout.showInlineRatingHistogram) {
                                Spacer(Modifier.weight(1f))
                                RatingHistogram(rating, Modifier.width(274.dp))
                            }
                        }
                    }
                    SubjectSummarySection(details?.summary.orEmpty().ifBlank { "暂无简介" })
                    chapters()
                    if (!layout.showRail) MangaRatingEntry(state, ratingActions)
                    related()
                    if (!layout.showRail) MangaReviewPreview(state, rail = false, onAll = { commentsSheet = true })
                }
                if (layout.showRail) {
                    Column(Modifier.width(layout.railWidth).testTag("manga-detail-rail"),
                        verticalArrangement = Arrangement.spacedBy(layout.railItemSpacing)) {
                        RailCard {
                            SectionHeader("评分") { MangaRatingEntry(state, ratingActions) }
                            SubjectRatingSummary(rating, onClick = ratingActions::requestEditRating)
                            RatingHistogram(rating, Modifier.fillMaxWidth().padding(top = 16.dp))
                        }
                        RailCard { MangaReviewPreview(state, rail = true, onAll = { commentsSheet = true }) }
                    }
                }
            }
        } else {
            SubjectDetailsSingleColumnPage(headerInfo, seasonTags = { Text("漫画") },
                collectionData = { SubjectCollectionStatsRow(stats, doingLabel = "在读", wishLabel = "想读") },
                collectionActions = { MangaLibraryControls(state) },
                rating = { SubjectRatingSummary(rating, onClick = ratingActions::requestEditRating) }, selectEpisodeButton = { read() },
                modifier = Modifier.testTag("manga-layout-COMPACT"), navigationIcon = navigation,
                onClickOpenExternal = openExternal,
                tabRow = { _, _ ->
                    SecondaryTabRow(pager.currentPage) {
                        listOf("详情", "评论").forEachIndexed { index, title ->
                            Tab(pager.currentPage == index, { tabScope.launch { pager.animateScrollToPage(index) } }, text = { Text(title) })
                        }
                    }
                }) { contentPadding ->
                HorizontalPager(pager, Modifier.fillMaxSize().padding(contentPadding)) { page ->
                    if (page == 1) MangaReviewFeed(state)
                    else {
                        val listState = rememberLazyListState()
                        LazyColumn(Modifier.fillMaxSize().nestedScrollWorkaround(listState), state = listState,
                            contentPadding = PaddingValues(layout.contentHorizontalPadding),
                            verticalArrangement = Arrangement.spacedBy(layout.sectionSpacing)) {
                            item { loadState() }
                            item { chapters() }
                            item { MangaRatingEntry(state, ratingActions) }
                            item { related() }
                            item { SubjectSummarySection(details?.summary.orEmpty().ifBlank { "暂无简介" }) }
                            item { tags() }
                            item { information() }
                            item { RatingHistogram(rating, Modifier.fillMaxWidth()) }
                            item { TextButton({ commentsSheet = true }, Modifier.testTag("manga-all-comments")) { Text("查看评论") } }
                        }
                    }
                }
            }
        }
    }
    if (commentsSheet) MangaReviewsSheet(state) { commentsSheet = false }
}
