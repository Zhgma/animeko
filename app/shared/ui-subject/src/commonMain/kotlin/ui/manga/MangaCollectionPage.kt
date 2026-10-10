/*
 * Copyright (C) 2026 OpenAni and contributors.
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package me.him188.ani.app.ui.manga

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.him188.ani.app.data.repository.manga.MangaCollection
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.navigation.NavRoutes
import me.him188.ani.app.data.repository.manga.MangaCollectionType
import me.him188.ani.app.ui.adaptive.AniTopAppBar
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.foundation.session.SelfAvatar
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.foundation.widgets.PullToRefreshBox
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.manga_count
import me.him188.ani.app.ui.lang.manga_doing
import me.him188.ani.app.ui.lang.manga_done
import me.him188.ani.app.ui.lang.manga_dropped
import me.him188.ani.app.ui.lang.manga_empty
import me.him188.ani.app.ui.lang.manga_empty_message
import me.him188.ani.app.ui.lang.manga_error
import me.him188.ani.app.ui.lang.manga_hold
import me.him188.ani.app.ui.lang.manga_loading
import me.him188.ani.app.ui.lang.manga_login
import me.him188.ani.app.ui.lang.manga_login_message
import me.him188.ani.app.ui.lang.manga_login_title
import me.him188.ani.app.ui.lang.manga_manage
import me.him188.ani.app.ui.lang.manga_note
import me.him188.ani.app.ui.lang.manga_open
import me.him188.ani.app.ui.lang.manga_private
import me.him188.ani.app.ui.lang.manga_progress
import me.him188.ani.app.ui.lang.manga_refresh
import me.him188.ani.app.ui.lang.manga_retry
import me.him188.ani.app.ui.lang.manga_score
import me.him188.ani.app.ui.lang.manga_tab
import me.him188.ani.app.ui.lang.manga_wish
import me.him188.ani.app.ui.lang.settings
import me.him188.ani.app.ui.lang.subject_collection_page_title
import me.him188.ani.app.ui.user.SelfInfoUiState
import org.jetbrains.compose.resources.stringResource

/** The only shared navigation between the independent anime and manga collection screens. */
@Composable
fun TrackingCollectionTabs(mangaSelected: Boolean, onSelectManga: (Boolean) -> Unit) {
    PrimaryTabRow(
        selectedTabIndex = if (mangaSelected) 1 else 0,
        modifier = Modifier.width(184.dp),
        containerColor = Color.Transparent,
        divider = {},
    ) {
        listOf(stringResource(Lang.subject_collection_page_title), stringResource(Lang.manga_tab))
            .forEachIndexed { index, title ->
                Tab(
                    selected = mangaSelected == (index == 1),
                    onClick = { onSelectManga(index == 1) },
                    modifier = Modifier.testTag(if (index == 0) "tracking-anime" else "tracking-manga"),
                    text = { Text(title, style = MaterialTheme.typography.titleLarge) },
                )
            }
    }
}

@Composable
fun MangaCollectionPage(
    selfInfo: SelfInfoUiState,
    onClickLogin: () -> Unit,
    onClickSettings: () -> Unit,
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
) {
    val vm = viewModel { MangaCollectionViewModel() }
    val state by vm.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val navigator = LocalNavigator.current
    MangaCollectionContent(
        state = state,
        selfInfo = selfInfo,
        title = title,
        onSelect = vm::select,
        onRefresh = vm::refresh,
        onClickLogin = onClickLogin,
        onClickSettings = onClickSettings,
        onManage = {
            uriHandler.openUri(state.username?.let { "https://bgm.tv/book/list/$it/${state.type.webPath}" }
                ?: "https://bgm.tv/book")
        },
        onOpen = { navigator.navigate(NavRoutes.MangaDetail(it.subjectId, it.title, it.originalTitle, it.coverUrl, it.chaptersRead, it.volumesRead)) },
        modifier = modifier,
        windowInsets = windowInsets,
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
    onClickSettings: () -> Unit,
    onManage: () -> Unit,
    onOpen: (MangaCollection) -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
) {
    Scaffold(
        modifier = modifier.testTag("manga-collection-page"),
        containerColor = AniThemeDefaults.pageContentBackgroundColor,
        contentWindowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
        topBar = {
            Column {
                AniTopAppBar(
                    title = title,
                    actions = {
                        IconButton(onRefresh, enabled = !state.loading && !state.needsLogin) {
                            Icon(Icons.Rounded.Refresh, stringResource(Lang.manga_refresh))
                        }
                        IconButton(onClickSettings) { Icon(Icons.Rounded.Settings, stringResource(Lang.settings)) }
                    },
                    avatar = { size -> SelfAvatar(selfInfo, size, onClickLogin) },
                    windowInsets = AniWindowInsets.forTopAppBarWithoutDesktopTitle(),
                )
                SecondaryScrollableTabRow(selectedTabIndex = MangaCollectionType.entries.indexOf(state.type)) {
                    MangaCollectionType.entries.forEach { type ->
                        Tab(
                            selected = state.type == type,
                            onClick = { onSelect(type) },
                            modifier = Modifier.testTag("manga-status-${type.name}"),
                            text = { Text(mangaTypeLabel(type)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        PullToRefreshBox(state.loading, onRefresh, Modifier.fillMaxSize().padding(padding)) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(180.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(Lang.manga_count, state.items.size), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(Lang.manga_note), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onManage) { Text(stringResource(Lang.manga_manage)) }
                    }
                }
                if (state.needsLogin) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        MangaMessage(stringResource(Lang.manga_login_title), stringResource(Lang.manga_login_message)) {
                            Button(onClickLogin) { Text(stringResource(Lang.manga_login)) }
                        }
                    }
                } else if (state.items.isEmpty() && !state.loading && !state.failed) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        MangaMessage(stringResource(Lang.manga_empty), stringResource(Lang.manga_empty_message)) {
                            Button(onManage) { Text(stringResource(Lang.manga_manage)) }
                        }
                    }
                }
                if (state.failed) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        MangaMessage(stringResource(Lang.manga_error)) {
                            Button(onRefresh) { Text(stringResource(Lang.manga_retry)) }
                        }
                    }
                }
                items(state.items, key = { it.subjectId }) { manga ->
                    MangaCard(manga, onClick = { onOpen(manga) })
                }
                if (state.loading) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            CircularProgressIndicator()
                            Text(stringResource(Lang.manga_loading))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MangaCard(manga: MangaCollection, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.testTag("manga-${manga.subjectId}")) {
        AsyncImage(manga.coverUrl, stringResource(Lang.manga_open),
            Modifier.fillMaxWidth().aspectRatio(0.7f), contentScale = ContentScale.Crop)
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(manga.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(stringResource(Lang.manga_progress, manga.chaptersRead,
                manga.totalChapters.takeIf { it > 0 }?.toString() ?: "—", manga.volumesRead,
                manga.totalVolumes.takeIf { it > 0 }?.toString() ?: "—"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (manga.score > 0) Text(stringResource(Lang.manga_score, manga.score), style = MaterialTheme.typography.labelSmall)
            if (manga.isPrivate) Text(stringResource(Lang.manga_private), style = MaterialTheme.typography.labelSmall)
        }
    }
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
