/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.subject.details.layout

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.animation.AniAnimatedVisibility
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults

@Composable
internal fun MultiColumnScaffold(
    layoutParams: SubjectDetailsLayoutParams,
    modifier: Modifier = Modifier,
    showTopBar: Boolean = true,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    navigationIcon: @Composable () -> Unit = {},
    onClickOpenExternal: () -> Unit = {},
    topBarTitle: String? = null,
    scrollState: ScrollState = rememberScrollState(),
    backgroundOverlay: @Composable (PaddingValues) -> Unit = {},
    content: @Composable RowScope.() -> Unit,
) {
    val density = LocalDensity.current
    // 页内标题位于中栏顶部 (contentTopPadding 之下); 其滚出可视区后切换到粘性标题栏.
    val stickyTopBarVisible by remember(scrollState, density, layoutParams) {
        derivedStateOf {
            scrollState.value >
                    with(density) { (layoutParams.contentTopPadding + TITLE_LINE_HEIGHT).toPx() }
        }
    }
    val topAppBarWindowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
    val topAppBarActions: @Composable RowScope.() -> Unit = {
        IconButton(onClickOpenExternal) {
            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
        }
    }

    Scaffold(
        modifier,
        topBar = {
            if (showTopBar) {
                Box {
                    // 透明背景的, 总是显示
                    TopAppBar(
                        title = {},
                        navigationIcon = navigationIcon,
                        actions = topAppBarActions,
                        colors = AniThemeDefaults.topAppBarColors().copy(containerColor = Color.Transparent),
                        windowInsets = topAppBarWindowInsets,
                    )
                    // 有背景和标题的, 仅在页内标题滚出后显示
                    AniAnimatedVisibility(stickyTopBarVisible && topBarTitle != null) {
                        TopAppBar(
                            title = {
                                Text(topBarTitle ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            navigationIcon = navigationIcon,
                            actions = topAppBarActions,
                            colors = AniThemeDefaults.topAppBarColors(
                                containerColor = AniThemeDefaults.navigationContainerColor,
                            ),
                            windowInsets = topAppBarWindowInsets,
                        )
                    }
                }
            }
        },
        containerColor = AniThemeDefaults.pageContentBackgroundColor,
        contentWindowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
    ) { scaffoldPadding ->
        backgroundOverlay(scaffoldPadding)
        Column(
            Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .verticalScroll(scrollState),
        ) {
            Row(
                // 留白放在滚动容器内, 随内容一起滚出
                Modifier.padding(
                    start = layoutParams.contentHorizontalPadding,
                    end = layoutParams.contentHorizontalPadding,
                    top = layoutParams.contentTopPadding,
                    bottom = layoutParams.contentBottomPadding,
                ),
                horizontalArrangement = Arrangement.spacedBy(layoutParams.columnSpacing),
                content = content,
            )
        }
    }
}

@Composable
internal fun RailCard(content: @Composable () -> Unit) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        // 对齐设计稿 rail 卡 (视觉: 标题字形距顶 ~21, 距侧 20): 卡内首行 SectionHeader
        // 的 TextButton (min 40dp) 居中 + 行框留白自带 ~17dp 顶空 (截图实测), 故 top=4.
        Column(Modifier.padding(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 18.dp)) {
            content()
        }
    }
}
private val TITLE_LINE_HEIGHT = 32.dp
