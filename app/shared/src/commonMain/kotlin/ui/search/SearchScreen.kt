/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.search

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.him188.ani.app.navigation.SubjectDetailPlaceholder
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.navigation.NavRoutes
import me.him188.ani.app.domain.search.SubjectType
import me.him188.ani.app.data.repository.manga.MangaCollection
import me.him188.ani.app.ui.novel.NovelDetailsRoute
import me.him188.ani.app.ui.manga.MangaDetailsRoute
import me.him188.ani.app.ui.exploration.search.SearchPage
import me.him188.ani.app.ui.exploration.search.SearchPageEffect
import me.him188.ani.app.ui.exploration.search.SearchPageIntent
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.foundation.widgets.BackNavigationIconButton
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.foundation.widgets.showLoadError
import me.him188.ani.app.ui.main.SearchViewModel
import me.him188.ani.app.ui.subject.details.SubjectDetailsScreen

@Composable
fun SearchScreen(
    vm: SearchViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToSubjectDetails: (subjectId: Int, placeholder: SubjectDetailPlaceholder?) -> Unit,
    onNavigateToEpisodeDetails: (subjectId: Int, episodeId: Int) -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = AniWindowInsets.forPageContent()
) {
    val navigator = LocalNavigator.current
    val listDetailNavigator = rememberListDetailPaneScaffoldNavigator()
    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()
    val toast = LocalToaster.current
    val searchPageState by vm.searchPageState.collectAsStateWithLifecycle()

    LaunchedEffect(vm) {
        vm.searchPageEffects.collectLatest { effect ->
            when (effect) {
                is SearchPageEffect.NavigateToSubjectDetails -> {
                    if (effect.type == SubjectType.NOVEL) {
                        navigator.navigate(NavRoutes.NovelDetail(effect.subjectId, effect.title, effect.originalTitle, effect.imageUrl))
                        return@collectLatest
                    }
                    if (effect.type == SubjectType.BOOK) {
                        navigator.navigate(NavRoutes.MangaDetail(effect.subjectId, effect.title, effect.originalTitle, effect.imageUrl))
                        return@collectLatest
                    }
                    onNavigateToSubjectDetails(
                        effect.subjectId,
                        SubjectDetailPlaceholder(
                            id = effect.subjectId,
                            name = effect.originalTitle,
                            nameCN = effect.title,
                            coverUrl = effect.imageUrl,
                        ),
                    )
                }

                is SearchPageEffect.NavigateToEpisodeDetails -> {
                    onNavigateToEpisodeDetails(effect.subjectId, effect.episodeId)
                }
            }
        }
    }

    SearchPage(
        state = searchPageState,
        onIntent = vm::onSearchPageIntent,
        suggestionsPager = vm::suggestionsPager,
        detailContent = {
            if (searchPageState.query.type == SubjectType.NOVEL) {
                val book by vm.bookPreview.collectAsStateWithLifecycle()
                book?.let { item ->
                    key(item.subjectId) {
                        NovelDetailsRoute(
                            MangaCollection(item.subjectId, item.title, item.originalTitle, item.imageUrl,
                                0, 0, 0, 0, 0, false),
                            onBack = {
                                vm.onSearchPageIntent(SearchPageIntent.ClearSelection)
                                coroutineScope.launch { listDetailNavigator.navigateBack() }
                            },
                            onReadRequested = { chapterId ->
                                navigator.navigate(NavRoutes.NovelDetail(item.subjectId, item.title, item.originalTitle,
                                    item.imageUrl, startReading = true, initialChapterId = chapterId))
                            },
                        )
                    }
                }
            } else if (searchPageState.query.type == SubjectType.BOOK) {
                val book by vm.bookPreview.collectAsStateWithLifecycle()
                book?.let { item ->
                    key(item.subjectId) {
                        MangaDetailsRoute(MangaCollection(item.subjectId, item.title, item.originalTitle, item.imageUrl,
                            0, 0, 0, 0, 0, false), onBack = {
                            vm.onSearchPageIntent(SearchPageIntent.ClearSelection)
                            coroutineScope.launch { listDetailNavigator.navigateBack() }
                        }, onReadChapter = { chapter ->
                            navigator.navigate(NavRoutes.MangaDetail(item.subjectId, item.title, item.originalTitle, item.imageUrl,
                                initialChapterId = chapter.id))
                        })
                    }
                }
            } else {
                val subjectDetailsState by vm.subjectDetailsStateLoader.state
                    .collectAsStateWithLifecycle()
                val selfInfo by vm.selfInfoFlow.collectAsStateWithLifecycle()

                SubjectDetailsScreen(
                    subjectDetailsState,
                    selfInfo,
                    onPlay = { episodeId ->
                        val current = subjectDetailsState
                        if (current != null) {
                            onNavigateToEpisodeDetails(current.subjectId, episodeId)
                        }
                    },
                    onLoadErrorRetry = { vm.reloadCurrentSubjectDetails() },
                    onClickTag = { tag ->
                        coroutineScope.launch {
                            if (listDetailNavigator.currentDestination?.pane == ListDetailPaneScaffoldRole.Detail) {
                                listDetailNavigator.navigateBack(BackNavigationBehavior.PopUntilScaffoldValueChange)
                            }
                            vm.onSearchPageIntent(
                                SearchPageIntent.UpdateQuery(
                                    searchPageState.query.copy(tags = listOf(tag.name)),
                                ),
                            )
                            gridState.animateScrollToItem(0)
                        }
                    },
                    onEpisodeCollectionUpdate = { request ->
                        coroutineScope.launch {
                            vm.setEpisodeCollectionType.invokeSafe(request)?.let {
                                toast.showLoadError(it)
                            }
                        }
                    },
                    windowInsets = paneContentWindowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Right),
                    navigationIcon = {
                        // 只有在单面板模式下才显示返回按钮
                        if (listDetailLayoutParameters.preferSinglePane) {
                            BackNavigationIconButton(
                                onNavigateBack = {
                                    coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                                        listDetailNavigator.navigateBack()
                                    }
                                },
                            )
                        }
                    },
                )
            }
        },
        modifier.fillMaxSize(),
        gridState = gridState,
        contentWindowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        navigator = listDetailNavigator,
        navigationIcon = {
            BackNavigationIconButton(onNavigateBack)
        },
    )
    LaunchedEffect(vm) {
        vm.onSearchPageIntent(SearchPageIntent.StartInitialSearch)
    }
}
