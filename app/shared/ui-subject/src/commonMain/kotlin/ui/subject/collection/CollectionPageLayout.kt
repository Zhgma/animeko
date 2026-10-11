/* Copyright (C) 2024-2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.subject.collection

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.him188.ani.app.ui.adaptive.AniTopAppBar
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.input.touchHorizontalScrollOnly
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.isHeightAtLeastMedium
import me.him188.ani.app.ui.foundation.theme.*
import me.him188.ani.app.ui.lang.*
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.platform.hasScrollingBug
import me.him188.ani.utils.platform.isDesktop
import org.jetbrains.compose.resources.stringResource

// 有顺序, https://github.com/Him188/ani/issues/73
@Stable
val COLLECTION_TABS_SORTED = listOf(
    UnifiedCollectionType.DROPPED,
    UnifiedCollectionType.WISH,
    UnifiedCollectionType.DOING,
    UnifiedCollectionType.ON_HOLD,
    UnifiedCollectionType.DONE,
)

/**
 * @param filters see [CollectionPageFilters]
 */
@Composable
internal fun CollectionPageLayout(
    title: @Composable () -> Unit,
    settingsIcon: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    avatar: @Composable (recommendedSize: DpSize) -> Unit,
    filters: @Composable CollectionPageFilters.() -> Unit,
    isRefreshing: () -> Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
    content: @Composable (nestedScrollConnection: NestedScrollConnection?, contentPadding: PaddingValues) -> Unit,
) {
    val isHeightAtLeastMedium = currentWindowAdaptiveInfo1().windowSizeClass.isHeightAtLeastMedium
    val scrollBehavior = if (LocalPlatform.current.hasScrollingBug() || isHeightAtLeastMedium) {
        null // Can't use PinnedBehavior, because we have a TabRow in this page, which does not sync color
    } else {
        // 在紧凑高度时收起 Top bar
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    }
    val frostedGlassActive = isAppChromeFrostedGlassActive()
    val appBarColors = AniThemeDefaults.topAppBarColors()
    Scaffold(
        modifier,
        topBar = {
            // 整个 topBar (app bar + tab row) 作为一个毛玻璃面板; 不启用时用不透明背景遮住下方滚动的内容.
            Column(
                modifier = Modifier.fillMaxWidth()
                    .appChromeFrostedGlass(
                        enabled = frostedGlassActive,
                        containerColor = appBarColors.containerColor,
                    )
                    .ifThen(!frostedGlassActive) { background(appBarColors.containerColor) },
            ) {
                AniTopAppBar(
                    title = title,
                    modifier = Modifier,
                    actions = {
                        actions()

                        if (LocalPlatform.current.isDesktop()) {
                            // PC 无法下拉刷新
                            IconButton(
                                {
                                    onRefresh()
                                },
                                enabled = !isRefreshing(),
                            ) {
                                Icon(Icons.Rounded.Refresh, null)
                            }
                        }

                        settingsIcon()
                    },
                    avatar = avatar,
                    colors = if (frostedGlassActive) {
                        appBarColors.copy(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent,
                        )
                    } else {
                        appBarColors
                    },
                    windowInsets = AniWindowInsets.forTopAppBarWithoutDesktopTitle(),
                    scrollBehavior = scrollBehavior,
                    enableFrostedGlass = false, // 由上面的 Column 统一应用
                )

                filters(CollectionPageFilters)
            }
        },
        contentWindowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
        containerColor = AniThemeDefaults.pageContentBackgroundColor,
    ) { topBarPaddings ->
        Box(
            // 毛玻璃 app chrome 的模糊来源. 内容通过 contentPadding 延伸到 chrome 下方.
            Modifier.appChromeHazeSource(backgroundColor = AniThemeDefaults.pageContentBackgroundColor)
                .fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .wrapContentWidth()
                    .widthIn(max = 1300.dp),
            ) {
                content(scrollBehavior?.nestedScrollConnection, topBarPaddings)
            }
        }
    }
}

/** Shared touch-only category paging for anime and manga collections. */
@Composable
internal fun CollectionPager(
    state: PagerState,
    modifier: Modifier = Modifier,
    content: @Composable (Int) -> Unit,
) {
    HorizontalPager(
        state = state,
        modifier = modifier.touchHorizontalScrollOnly(),
        beyondViewportPageCount = 1,
        pageSpacing = 0.dp,
    ) { content(it) }
}

@Composable
internal fun CollectionTypeTabLabel(label: String, count: Int?) {
    if (count == null) {
        Text(
            text = label,
            Modifier.width(IntrinsicSize.Max),
            softWrap = false,
        )
    } else {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                softWrap = false,
            )
            Badge(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Text(
                    text = count.toString(),
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .wrapContentSize(align = Alignment.Center),
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Stable
object CollectionPageFilters {
    @Composable
    fun CollectionTypeFilterButtons(
        pagerState: PagerState,
        modifier: Modifier = Modifier,
        itemLabel: @Composable (UnifiedCollectionType) -> Unit = { type ->
            Text(type.displayText(), softWrap = false)
        },
    ) {
        val uiScope = rememberCoroutineScope()
        SingleChoiceSegmentedButtonRow(modifier) {
            COLLECTION_TABS_SORTED.forEachIndexed { index, type ->
                SegmentedButton(
                    selected = pagerState.currentPage == index,
                    onClick = { uiScope.launch { pagerState.scrollToPage(index) } },
                    shape = SegmentedButtonDefaults.itemShape(index, COLLECTION_TABS_SORTED.size),
                    Modifier.wrapContentWidth(),
                ) {
                    itemLabel(type)
                }
            }
        }
    }

    @Composable
    fun CollectionTypeScrollableTabRow(
        selectedIndex: Int,
        onSelect: (Int) -> Unit,
        modifier: Modifier = Modifier,
        itemLabel: @Composable (UnifiedCollectionType) -> Unit = { type ->
            Text(type.displayText(), softWrap = false)
        },
        scrollState: ScrollState = rememberScrollState(),
        tabModifier: (UnifiedCollectionType) -> Modifier = { Modifier },
    ) {
        val widths = remember { mutableStateListOf(*COLLECTION_TABS_SORTED.map { 24.dp }.toTypedArray()) }
        SecondaryScrollableTabRow(
            selectedTabIndex = selectedIndex,
            indicator = @Composable {
                TabRowDefaults.PrimaryIndicator(
                    Modifier.tabIndicatorOffset(selectedIndex, matchContentSize = false),
                    width = widths[selectedIndex],
                )
            },
            containerColor = Color.Unspecified,
            contentColor = MaterialTheme.colorScheme.onSurface,
            divider = {},
            modifier = modifier.fillMaxWidth(),
            scrollState = scrollState,
        ) {
            COLLECTION_TABS_SORTED.forEachIndexed { index, collectionType ->
                Tab(
                    selected = selectedIndex == index,
                    onClick = { onSelect(index) },
                    modifier = tabModifier(collectionType),
                    text = {
                        val density = LocalDensity.current
                        Box(Modifier.onPlaced { widths[index] = with(density) { it.size.width.toDp() } }) {
                            itemLabel(collectionType)
                        }
                    },
                )
            }
        }
    }

}

@Composable
@Stable
internal fun UnifiedCollectionType.displayText(): String {
    return when (this) {
        UnifiedCollectionType.WISH -> stringResource(Lang.subject_collection_wish)
        UnifiedCollectionType.DOING -> stringResource(Lang.subject_collection_doing)
        UnifiedCollectionType.DONE -> stringResource(Lang.subject_collection_done)
        UnifiedCollectionType.ON_HOLD -> stringResource(Lang.subject_collection_on_hold)
        UnifiedCollectionType.DROPPED -> stringResource(Lang.subject_collection_dropped)
        UnifiedCollectionType.NOT_COLLECTED -> stringResource(Lang.subject_collection_uncollected)
    }
}
