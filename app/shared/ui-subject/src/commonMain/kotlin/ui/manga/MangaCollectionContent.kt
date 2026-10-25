/*
 * Copyright (C) 2026 OpenAni and contributors.
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package me.him188.ani.app.ui.manga

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import me.him188.ani.app.ui.foundation.layout.plus
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.him188.ani.app.data.repository.manga.MangaCollection
import me.him188.ani.app.data.repository.manga.MangaCollectionType
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.isWidthCompact
import me.him188.ani.app.ui.foundation.layout.paneHorizontalPadding
import me.him188.ani.app.ui.foundation.widgets.PullToRefreshBox
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.manga_doing
import me.him188.ani.app.ui.lang.manga_done
import me.him188.ani.app.ui.lang.manga_dropped
import me.him188.ani.app.ui.lang.manga_error
import me.him188.ani.app.ui.lang.manga_hold
import me.him188.ani.app.ui.lang.manga_login
import me.him188.ani.app.ui.lang.manga_login_message
import me.him188.ani.app.ui.lang.manga_login_title
import me.him188.ani.app.ui.lang.manga_open
import me.him188.ani.app.ui.lang.manga_private
import me.him188.ani.app.ui.lang.manga_progress
import me.him188.ani.app.ui.lang.manga_retry
import me.him188.ani.app.ui.lang.manga_score
import me.him188.ani.app.ui.lang.manga_tab
import me.him188.ani.app.ui.lang.manga_wish
import me.him188.ani.app.ui.lang.subject_collection_page_title
import me.him188.ani.app.ui.subject.collection.COLLECTION_TABS_SORTED
import me.him188.ani.app.ui.subject.collection.CollectionPageLayout
import me.him188.ani.app.ui.subject.collection.CollectionPager
import me.him188.ani.app.ui.subject.collection.CollectionTypeTabLabel
import org.jetbrains.compose.resources.stringResource

/** Switches between anime and manga collection data within the collection page. */
@Composable
fun TrackingCollectionTabs(selected: Int, onSelect: (Int) -> Unit) {
    TrackingCollectionTabsContent(selected, onSelect, listOf("追番", "追漫", "追文"))
}

@Composable
fun TrackingCollectionTabs(mangaSelected: Boolean, onSelectManga: (Boolean) -> Unit) {
    TrackingCollectionTabsContent(mangaSelected, onSelectManga,
        stringResource(Lang.subject_collection_page_title), stringResource(Lang.manga_tab))
}

internal val MANGA_COLLECTION_TABS = COLLECTION_TABS_SORTED.map { MangaCollectionType.valueOf(it.name) }

@Composable
fun MangaCollectionPageContent(
    state: MangaCollectionUiState,
    avatar: @Composable (DpSize) -> Unit,
    title: @Composable () -> Unit,
    onSelect: (MangaCollectionType) -> Unit,
    onRefresh: () -> Unit,
    onClickLogin: () -> Unit,
    onOpen: (MangaCollection) -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
    actions: @Composable () -> Unit = {},
    novel: Boolean = false,
    onLocalBooks: () -> Unit = {},
) {
    val pager = rememberPagerState(initialPage = MANGA_COLLECTION_TABS.indexOf(state.type)) { MANGA_COLLECTION_TABS.size }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager.currentPage) { onSelect(MANGA_COLLECTION_TABS[pager.currentPage]) }
    CollectionPageLayout(
        title = title,
        settingsIcon = {},
        actions = { actions() },
        avatar = avatar,
        filters = {
            CollectionTypeScrollableTabRow(
                selectedIndex = pager.currentPage,
                onSelect = { index -> scope.launch { pager.animateScrollToPage(index) } },
                modifier = Modifier.padding(horizontal = currentWindowAdaptiveInfo1().windowSizeClass.paneHorizontalPadding),
                itemLabel = { type ->
                    val mangaType = MangaCollectionType.valueOf(type.name)
                    CollectionTypeTabLabel(mangaTypeLabel(mangaType), state.page(mangaType).count)
                },
                tabModifier = { Modifier.testTag("manga-status-${it.name}") },
            )
        },
        isRefreshing = { state.page(MANGA_COLLECTION_TABS[pager.currentPage]).loading },
        onRefresh = onRefresh,
        modifier = modifier.testTag("manga-collection-page"),
        windowInsets = windowInsets,
    ) { nestedScrollConnection, contentPadding ->
        CollectionPager(pager, Modifier.fillMaxSize().testTag("manga-collection-pager")) { pageIndex ->
            val type = MANGA_COLLECTION_TABS[pageIndex]
            val page = state.page(type)
            val pullState = rememberPullToRefreshState()
            val spacing = if (currentWindowAdaptiveInfo1().windowSizeClass.isWidthCompact) 16.dp else 24.dp
            PullToRefreshBox(
                page.loading, onRefresh,
                state = pullState,
                enabled = !state.needsLogin,
                touchOnly = true,
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = contentPadding.calculateTopPadding()),
                        isRefreshing = page.loading,
                        state = pullState,
                    )
                },
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(180.dp),
                    state = rememberLazyGridState(),
                    modifier = Modifier.fillMaxSize().testTag("manga-grid-${type.name}")
                        .then(if (nestedScrollConnection != null) Modifier.nestedScroll(nestedScrollConnection) else Modifier),
                    contentPadding = contentPadding + PaddingValues(spacing / 2),
                ) {
                    if (novel) item(span = { GridItemSpan(maxLineSpan) }) {
                        TextButton(onLocalBooks, Modifier.fillMaxWidth().testTag("novel-local-books")) { Text("本地电子书 · TXT / EPUB / MOBI / AZW3") }
                    }
                    if (state.needsLogin) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            MangaMessage(if (novel) "登录后同步小说收藏" else stringResource(Lang.manga_login_title), if (novel) "使用 Bangumi 账号同步想读、在读和读过的小说" else stringResource(Lang.manga_login_message)) {
                                Button(onClickLogin, Modifier.testTag("manga-authorize-${type.name}")) {
                                    Text(stringResource(Lang.manga_login))
                                }
                            }
                        }
                    }
                    if (page.failed) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            MangaMessage(stringResource(Lang.manga_error)) {
                                Button(onRefresh) { Text(stringResource(Lang.manga_retry)) }
                            }
                        }
                    }
                    items(page.items, key = { it.subjectId }) { manga ->
                        Box(Modifier.padding(spacing / 2)) { MangaCard(manga, novel) { onOpen(manga) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun MangaCard(manga: MangaCollection, novel: Boolean, onClick: () -> Unit) {
    MangaCollectionCard(
        title = manga.title,
        progress = if (novel) "${manga.chaptersRead} / ${manga.totalChapters.takeIf { it > 0 } ?: "—"} 章 · ${manga.volumesRead} / ${manga.totalVolumes.takeIf { it > 0 } ?: "—"} 卷" else stringResource(Lang.manga_progress, manga.chaptersRead,
            manga.totalChapters.takeIf { it > 0 }?.toString() ?: "—", manga.volumesRead,
            manga.totalVolumes.takeIf { it > 0 }?.toString() ?: "—"),
        onClick = onClick,
        modifier = Modifier.testTag("manga-${manga.subjectId}"),
        image = {
            AsyncImage(manga.coverUrl, if (novel) "查看小说详情" else stringResource(Lang.manga_open),
                Modifier.fillMaxWidth().aspectRatio(0.7f), contentScale = ContentScale.Crop)
        },
        annotations = {
            if (manga.score > 0) Text(stringResource(Lang.manga_score, manga.score), style = MaterialTheme.typography.labelSmall)
            if (manga.isPrivate) Text(stringResource(Lang.manga_private), style = MaterialTheme.typography.labelSmall)
        },
    )
}

@Composable
private fun MangaMessage(title: String, description: String? = null, action: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (description != null) Text(description, style = MaterialTheme.typography.bodyMedium)
        action()
    }
}

@Composable
private fun mangaTypeLabel(type: MangaCollectionType): String = stringResource(
    when (type) {
        MangaCollectionType.WISH -> Lang.manga_wish
        MangaCollectionType.DOING -> Lang.manga_doing
        MangaCollectionType.DONE -> Lang.manga_done
        MangaCollectionType.ON_HOLD -> Lang.manga_hold
        MangaCollectionType.DROPPED -> Lang.manga_dropped
    },
)
