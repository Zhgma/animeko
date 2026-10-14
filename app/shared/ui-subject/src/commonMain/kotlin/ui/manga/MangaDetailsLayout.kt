/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.manga

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.remember
import me.him188.ani.app.ui.foundation.ImageViewer
import me.him188.ani.app.ui.foundation.ImageViewerBackHandler
import me.him188.ani.app.ui.foundation.rememberImageViewerHandler
import me.him188.ani.app.ui.rating.EditableRatingActions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ImageBitmap
import com.kmpalette.rememberPaletteState
import me.him188.ani.app.ui.foundation.AniImageLoadSuccess
import me.him188.ani.app.ui.foundation.LocalSubjectAppearanceSettings
import me.him188.ani.app.data.models.subject.preferredDisplayName
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.layout.rememberNestedScrollableColumnState
import me.him188.ani.app.ui.foundation.theme.LocalThemeSettings
import me.him188.ani.app.ui.foundation.theme.MaterialThemeFromPaletteAndImage
import me.him188.ani.app.ui.rating.Rating
import me.him188.ani.app.ui.subject.details.SubjectDetailsContentTabRow
import me.him188.ani.app.ui.subject.details.SubjectDetailsTab
import me.him188.ani.app.ui.subject.details.components.SubjectDetailsDefaults
import me.him188.ani.app.ui.subject.details.components.CollectionData
import me.him188.ani.app.ui.subject.details.components.SelectEpisodeButtons
import me.him188.ani.app.ui.subject.details.layout.SubjectDetailsCover
import me.him188.ani.app.ui.subject.details.layout.SubjectDetailsTitleBlock
import me.him188.ani.app.ui.subject.details.layout.SubjectDetailsGradientBackground
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.models.subject.RatingInfo
import me.him188.ani.app.data.models.subject.SubjectCollectionStats
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.models.subject.Tag
import me.him188.ani.app.data.repository.manga.MangaChapter
import me.him188.ani.app.data.repository.manga.MangaCollection
import me.him188.ani.app.data.repository.manga.MangaSource
import me.him188.ani.app.data.repository.manga.MangaSourceBook
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
    val imageViewer = rememberImageViewerHandler()
    ImageViewerBackHandler(imageViewer)
    val coverUrl = headerInfo.imageLarge.takeIf { it.isNotBlank() }
    val openCover: (() -> Unit)? = coverUrl?.let { url -> { imageViewer.viewImage(url) } }
    val themeSettings = LocalThemeSettings.current
    var bitmap by remember(coverUrl) { mutableStateOf<ImageBitmap?>(null) }
    val palette = rememberPaletteState()
    val coverLoaded: (AniImageLoadSuccess) -> Unit = { success -> success.bitmap?.let { bitmap = it }; Unit }
    LaunchedEffect(themeSettings, bitmap) {
        bitmap?.let {
            if (themeSettings.useDynamicSubjectPageTheme || themeSettings.enableAnimatedGradientSubjectPage) palette.generate(it)
        }
    }
    val nestedScrollState = rememberNestedScrollableColumnState()
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
    MaterialThemeFromPaletteAndImage(
        if (themeSettings.useDynamicSubjectPageTheme) palette.palette else null,
        if (themeSettings.useDynamicSubjectPageTheme) bitmap else null,
    ) {
        BoxWithConstraints(modifier.fillMaxSize().testTag("manga-details")) {
            val layout = SubjectDetailsLayoutParams.calculate(maxWidth)
            if (layout.isMultiColumn) {
                MultiColumnScaffold(layout, Modifier.testTag("manga-layout-${layout.kind}"),
                    navigationIcon = navigation, onClickOpenExternal = openExternal,
                    topBarTitle = headerInfo.preferredDisplayName(LocalSubjectAppearanceSettings.current.useOriginalTitle),
                    backgroundOverlay = { SubjectDetailsGradientBackground(if (themeSettings.enableAnimatedGradientSubjectPage) palette.palette else null) }) {
                    Column(Modifier.width(layout.sidebarWidth).testTag("manga-detail-sidebar"),
                        verticalArrangement = Arrangement.spacedBy(layout.sidebarItemSpacing)) {
                        SubjectDetailsCover(headerInfo, onCoverImageSuccess = coverLoaded, onClickCover = openCover)
                        read()
                        MangaLibraryControls(state, showProgress = false)
                        SubjectCollectionStatsRow(stats, doingLabel = "在读", wishLabel = "想读")
                        HorizontalDivider()
                        information()
                        tags()
                    }
                    Column(Modifier.weight(1f).testTag("manga-detail-main"),
                        verticalArrangement = Arrangement.spacedBy(layout.sectionSpacing)) {
                        SubjectDetailsTitleBlock(headerInfo) {
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
                        if (!details?.summary.isNullOrBlank()) SubjectSummarySection(details!!.summary)
                        chapters()
                        MangaLibraryControls(state, showCollection = false)
                        related()
                        if (!layout.showRail) MangaReviewPreview(state, rail = false, onAll = { commentsSheet = true })
                    }
                    if (layout.showRail) {
                        Column(Modifier.width(layout.railWidth).testTag("manga-detail-rail"),
                            verticalArrangement = Arrangement.spacedBy(layout.railItemSpacing)) {
                            RailCard {
                                SectionHeader("评分") { MangaRatingEntry(state, ratingActions) }
                                SubjectRatingSummary(rating, Modifier.padding(top = 8.dp),
                                    scoreStyle = MaterialTheme.typography.headlineMedium, onClick = ratingActions::requestEditRating)
                                RatingHistogram(rating, Modifier.fillMaxWidth().padding(top = 16.dp))
                            }
                            RailCard { MangaReviewPreview(state, rail = true, onAll = { commentsSheet = true }) }
                        }
                    }
                }
            } else {
                SubjectDetailsSingleColumnPage(headerInfo, seasonTags = { Text("漫画") },
                    collectionData = { SubjectDetailsDefaults.CollectionData(stats, summary = "${stats.collect} 人收藏 / ${stats.doing} 人在读") },
                    collectionActions = { MangaLibraryControls(state, showProgress = false, fillWidth = false) },
                    rating = { Rating(rating, state.rating.selfRatingInfo.score, ratingActions::requestEditRating,
                        clickEnabled = state.rating.enableEdit && !state.rating.isUpdating) },
                    selectEpisodeButton = {
                        SubjectDetailsDefaults.SelectEpisodeButtons(actions.showChapters, listContentDescription = "漫画目录", progressButton = read)
                    },
                    modifier = Modifier.testTag("manga-layout-COMPACT"), navigationIcon = navigation,
                    onClickOpenExternal = openExternal,
                    onClickCover = openCover, onCoverImageSuccess = coverLoaded,
                    nestedScrollableColumnState = nestedScrollState,
                    floatingActionButton = {
                        if (pager.currentPage == 1) ExtendedFloatingActionButton(
                            text = { Text("写评价") }, icon = { Icon(Icons.Rounded.AddComment, null) },
                            onClick = actions.writeReview, expanded = !nestedScrollState.isHeaderScrolledOut,
                            modifier = Modifier.testTag("manga-write-review"))
                    },
                    tabRow = { overlay, visible ->
                        SubjectDetailsContentTabRow(pager, Modifier.ifThen(!overlay) { alpha(if (visible) 1f else 0f) },
                            compact = true, tabs = listOf(SubjectDetailsTab.DETAILS, SubjectDetailsTab.COMMENTS))
                    }) { contentPadding ->
                    HorizontalPager(pager, Modifier.fillMaxSize().padding(contentPadding)) { page ->
                        if (page == 1) MangaReviewFeed(state, pullToRefreshEnabled = nestedScrollState.isHeaderFullyVisible, showWriteButton = false)
                        else {
                            val listState = rememberLazyListState()
                            LazyColumn(Modifier.fillMaxSize().nestedScrollWorkaround(listState), state = listState,
                                contentPadding = PaddingValues(layout.contentHorizontalPadding),
                                verticalArrangement = Arrangement.spacedBy(layout.sectionSpacing)) {
                                if (state.loading || state.usingBangumiCache || state.error != null) item("status") { loadState() }
                                item("chapters") { chapters() }
                                item("progress") { MangaLibraryControls(state, showCollection = false) }
                                if (!details?.summary.isNullOrBlank()) item("summary") {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("简介", style = MaterialTheme.typography.titleMedium)
                                        SubjectSummarySection(details!!.summary)
                                    }
                                }
                                item("tags") { tags() }
                                item("information") { information() }
                                item("related") { related() }
                            }
                        }
                    }
                }
            }
        }
        if (commentsSheet) MangaReviewsSheet(state) { commentsSheet = false }
        ImageViewer(imageViewer) { imageViewer.clear() }
    }
}
