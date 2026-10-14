/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(TestOnly::class)

package me.him188.ani.app.ui.exploration.schedule

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import me.him188.ani.utils.platform.annotations.TestOnly
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.adaptive.AniTopAppBar
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.foundation.widgets.BackNavigationIconButton
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.exploration_schedule
import me.him188.ani.app.ui.search.LoadErrorCard
import org.jetbrains.compose.resources.stringResource

@Composable
fun ScheduleScreen(
    presentation: SchedulePagePresentation,
    onRetry: () -> Unit,
    onClickItem: (item: AiringScheduleItemPresentation) -> Unit,
    modifier: Modifier = Modifier,
    layoutParams: ScheduleScreenLayoutParams = ScheduleScreenLayoutParams.calculate(),
    colors: ScheduleScreenColors = ScheduleScreenDefaults.colors(),
    navigationIcon: @Composable () -> Unit = {},
    state: ScheduleScreenState = remember { ScheduleScreenState { presentation.days } },
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
) {
    Scaffold(
        modifier,
        topBar = {
            AniTopAppBar(
                title = { Text(stringResource(Lang.exploration_schedule)) },
                Modifier.fillMaxWidth(),
                navigationIcon = navigationIcon,
                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            )
        },
        containerColor = AniThemeDefaults.pageContentBackgroundColor,
        contentWindowInsets = windowInsets.only(WindowInsetsSides.Horizontal),
    ) { paddingValues ->
        if (presentation.error != null) {
            LoadErrorCard(
                presentation.error,
                onRetry = onRetry,
                modifier = Modifier.padding(paddingValues)
                    .padding(layoutParams.pageContentPadding)
                    .padding(all = 16.dp),
            )
        } else {
            ScheduleScreenContent(
                state = state,
                modifier = Modifier.padding(paddingValues),
                layoutParams = layoutParams,
                colors = colors,
            ) { day ->
                ScheduleDayColumn(
                    onClickItem = onClickItem,
                    dayOfWeek = {
                        if (layoutParams.showDayOfWeekHeadline) {
                            DayOfWeekHeadline(day)
                        }
                    },
                    items = presentation.airingSchedules.firstOrNull { it.date == day.date }?.episodes.orEmpty(),
                    layoutParams = layoutParams.columnLayoutParams,
                    state = state.scheduleColumnLazyListStates[day] ?: rememberLazyListState(),
                    itemColors = colors.itemColors,
                )
            }
        }
    }
}

@Composable
@Preview
fun PreviewSchedulePage() {
    ProvideCompositionLocalsForPreview {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest) {
            ScheduleScreen(
                presentation = createTestSchedulePagePresentation(),
                onRetry = {},
                onClickItem = {},
                navigationIcon = {
                    BackNavigationIconButton({})
                },
            )
        }
    }
}
