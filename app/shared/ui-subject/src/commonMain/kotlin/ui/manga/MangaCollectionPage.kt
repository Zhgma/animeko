/*
 * Copyright (C) 2026 OpenAni and contributors.
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package me.him188.ani.app.ui.manga

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.him188.ani.app.data.repository.manga.BangumiBookKind
import me.him188.ani.app.data.repository.manga.MangaCollection
import me.him188.ani.app.data.repository.manga.MangaCollectionType
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.navigation.NavRoutes
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.foundation.session.SelfAvatar
import me.him188.ani.app.ui.user.SelfInfoUiState

@Composable
fun MangaCollectionPage(
    selfInfo: SelfInfoUiState,
    onClickLogin: () -> Unit,
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
    kind: BangumiBookKind = BangumiBookKind.MANGA,
) {
    val vm = viewModel(key = "books-${kind.name}") { MangaCollectionViewModel(kind) }
    val state by vm.state.collectAsStateWithLifecycle()
    val usingCache by vm.usingCache.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    MangaCollectionContent(
        state, selfInfo, title, vm::select, vm::refresh, onClickLogin,
        onOpen = { if (kind == BangumiBookKind.NOVEL) navigator.navigate(NavRoutes.NovelDetail(it.subjectId, it.title, it.originalTitle, it.coverUrl))
            else navigator.navigate(NavRoutes.MangaDetail(it.subjectId, it.title, it.originalTitle, it.coverUrl, it.chaptersRead, it.volumesRead)) },
        modifier = modifier,
        windowInsets = windowInsets,
        kind = kind,
        usingBangumiCache = usingCache,
    )
}

@Composable
internal fun MangaCollectionContent(
    state: MangaCollectionUiState,
    selfInfo: SelfInfoUiState,
    title: @Composable () -> Unit,
    onSelect: (MangaCollectionType) -> Unit,
    onRefresh: () -> Unit,
    onClickLogin: () -> Unit,
    onOpen: (MangaCollection) -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
    kind: BangumiBookKind = BangumiBookKind.MANGA,
    usingBangumiCache: Boolean = false,
) {
    val navigator = LocalNavigator.current
    MangaCollectionPageContent(
        state = state,
        avatar = { size -> SelfAvatar(selfInfo, size, onClickLogin) },
        title = title, onSelect = onSelect, onRefresh = onRefresh,
        onClickLogin = { navigator.navigateBangumiAuthorize() }, onOpen = onOpen,
        modifier = modifier, windowInsets = windowInsets, novel = kind == BangumiBookKind.NOVEL,
        usingBangumiCache = usingBangumiCache,
        onLocalBooks = { navigator.navigate(NavRoutes.NovelDetail(0, "本地阅读")) },
    )
}
