// SPDX-License-Identifier: AGPL-3.0-only
package me.him188.ani.app.ui.subject.episode.details
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.coerceAtLeast
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.isWidthAtLeastMedium
import me.him188.ani.app.ui.foundation.layout.paddingIfNotEmpty

@Composable
internal fun SectionTitle(
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    Row(
        modifier.padding(top = 12.dp, bottom = 8.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProvideTextStyle(MaterialTheme.typography.titleMedium) {
            Row(Modifier.weight(1f)) {
                content()
            }
            Row(Modifier.padding(start = 16.dp)) {
                actions()
            }
        }
    }
}

/**
 * [subjectRecommendations] 是最底部的内容, 可以使用 [LazyListScope].
 */
@Composable
fun EpisodeDetailsScaffold(
    subjectTitle: @Composable () -> Unit,
    favoriteButton: @Composable () -> Unit,
    loadError: @Composable () -> Unit,
    airingStatus: @Composable (FlowRowScope.() -> Unit),
    mediaSelectorItem: @Composable (contentPadding: PaddingValues) -> Unit,
    danmakuStatisticsSummary: @Composable () -> Unit,
    danmakuStatistics: @Composable (contentPadding: PaddingValues) -> Unit,
    episodeListSection: @Composable () -> Unit,
    subjectRecommendations: LazyListScope.(contentPadding: PaddingValues) -> Unit,
    onExpandSubject: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(all = 16.dp),
    episodeInfo: (@Composable () -> Unit)? = null,
    danmakuListSection: (@Composable () -> Unit)? = null,
) {
    val contentPaddingState by rememberUpdatedState(contentPadding)
    val layoutDirection by rememberUpdatedState(LocalLayoutDirection.current)
    val horizontalPaddingValues by remember {
        derivedStateOf {
            PaddingValues(
                start = contentPaddingState.calculateStartPadding(layoutDirection),
                end = contentPaddingState.calculateStartPadding(layoutDirection),
            )
        }
    }
    val atLeastMedium = currentWindowAdaptiveInfo1().isWidthAtLeastMedium
    val currentDanmakuListSelection by rememberUpdatedState(danmakuListSection)

    LazyColumn(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background),
    ) {
        item {
            val topPadding by remember {
                derivedStateOf {
                    (contentPaddingState.calculateTopPadding() - 8.dp).coerceAtLeast(0.dp)
                }
            }
            Spacer(Modifier.height(topPadding))
        }

        item("episode_detail_header") {
            // header
            Column(
                Modifier.padding(horizontalPaddingValues),
            ) {
                Row {
                    Box(
                        Modifier.padding(top = 8.dp, end = 8.dp)
                            .weight(1f)
                            .clickable(onClick = onExpandSubject),
                    ) {
                        ProvideTextStyle(MaterialTheme.typography.titleLarge) {
                            SelectionContainer { subjectTitle() }
                        }
                    }
                    Row(Modifier.padding(start = 12.dp)) {
                        favoriteButton()
                    }
                }
            }
        }

        if (episodeInfo != null) {
            item("episode_detail_episode_info") {
                Row(
                    Modifier.padding(horizontalPaddingValues),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    episodeInfo()
                }
            }
        }

        item("episode_detail_load_error") {
            Row(Modifier.padding(horizontalPaddingValues).paddingIfNotEmpty(top = 12.dp)) {
                loadError()
            }
        }

        if (atLeastMedium) {
            item("episode_detail_airing_status") {
                SectionTitle(
                    Modifier.padding(top = 8.dp, bottom = 8.dp),
                ) {
                    FlowRow(
                        Modifier,/*.weight(1f)*/
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                    ) {
                        airingStatus()
                    }
                }
            }
        }

        item("episode_detail_exposed_episode_item") {
            Row(Modifier) {
                mediaSelectorItem(horizontalPaddingValues)
            }
        }

        if (currentDanmakuListSelection != null) {
            item("episode_detail_danmaku_list_section") {
                Box(Modifier.padding(top = if (atLeastMedium) 8.dp else 0.dp)) {
                    currentDanmakuListSelection?.let {
                        it()
                    }
                }
            }
        }

        if (!atLeastMedium) {
            item("episode_detail_danmaku_statistics_summary") {
                Row(Modifier.padding(horizontalPaddingValues).padding(top = 4.dp)) {
                    danmakuStatisticsSummary()
                }
            }
        }

        item("episode_detail_episode_list_section") {
            Box(Modifier.padding(top = if (atLeastMedium) 8.dp else 0.dp)) {
                episodeListSection()
            }
        }

        if (!atLeastMedium) {
            item("danmaku_statistics") {
                Row(Modifier.fillMaxWidth()) {
                    danmakuStatistics(horizontalPaddingValues)
                }
            }
        }

        subjectRecommendations(horizontalPaddingValues)

        item("system_bar_spacer") {
            Spacer(
                Modifier.windowInsetsBottomHeight(
                    AniWindowInsets.safeDrawing,
                ).heightIn(min = Dp.Hairline),
            )
        }
    }
}

