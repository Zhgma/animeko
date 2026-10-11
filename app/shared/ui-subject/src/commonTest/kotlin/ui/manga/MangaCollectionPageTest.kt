/*
 * Copyright (C) 2026 OpenAni and contributors.
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package me.him188.ani.app.ui.manga

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import me.him188.ani.app.data.repository.manga.MangaCollection
import me.him188.ani.app.data.repository.manga.MangaCollectionType
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.user.TestSelfInfoUiState
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(TestOnly::class)
class MangaCollectionPageTest {
    @Test
    fun topTabsSwitchBothWays() = runAniComposeUiTest {
        var manga by mutableStateOf(false)
        setContent {
            ProvideCompositionLocalsForPreview {
                TrackingCollectionTabs(manga) { manga = it }
            }
        }
        onNodeWithTag("tracking-anime").assertIsSelected()
        onNodeWithTag("tracking-manga").performClick().assertIsSelected()
        onNodeWithTag("tracking-anime").performClick().assertIsSelected()
    }

    @Test
    fun mangaCardAndCategoryDispatchIndependentActions() = runAniComposeUiTest {
        val manga = MangaCollection(2, "碧蓝之海", "ぐらんぶる", "", 45, 7, 100, 20, 9, false)
        var selected: MangaCollectionType? = null
        var opened: Int? = null
        setContent {
            ProvideCompositionLocalsForPreview {
                MangaCollectionContent(
                    state = MangaCollectionUiState(pages = mapOf(MangaCollectionType.DOING to MangaCollectionPageState(items = listOf(manga), loading = false, count = 1))),
                    selfInfo = TestSelfInfoUiState,
                    title = { TrackingCollectionTabs(true) {} },
                    onSelect = { selected = it },
                    onRefresh = {},
                    onClickLogin = {},
                    onOpen = { opened = it.subjectId },
                )
            }
        }
        onNodeWithText("碧蓝之海").assertExists()
        onNodeWithTag("manga-2").performClick()
        onNodeWithTag("manga-status-WISH").performClick()
        runOnIdle {
            assertEquals(2, opened)
            assertEquals(MangaCollectionType.WISH, selected)
        }
    }
    @Test
    fun categoryOrderCountsAndSwipeUseAnimeCollectionComponents() = runAniComposeUiTest {
        val expected = listOf(MangaCollectionType.DROPPED, MangaCollectionType.WISH,
            MangaCollectionType.DOING, MangaCollectionType.ON_HOLD, MangaCollectionType.DONE)
        var selected: MangaCollectionType? = null
        setContent {
            ProvideCompositionLocalsForPreview {
                MangaCollectionContent(
                    state = MangaCollectionUiState(pages = expected.mapIndexed { index, type ->
                        type to MangaCollectionPageState(loading = false, count = index + 11)
                    }.toMap()),
                    selfInfo = TestSelfInfoUiState,
                    title = { TrackingCollectionTabs(true) {} },
                    onSelect = { selected = it }, onRefresh = {}, onClickLogin = {},
                    onOpen = {},
                )
            }
        }
        val tabs = expected.map { onNodeWithTag("manga-status-${it.name}") }
        tabs.zipWithNext().forEach { (left, right) ->
            assertTrue(left.fetchSemanticsNode().boundsInRoot.left < right.fetchSemanticsNode().boundsInRoot.left)
        }
        expected.forEachIndexed { index, type ->
            onNode(hasText((index + 11).toString()) and hasAnyAncestor(hasTestTag("manga-status-${type.name}")), useUnmergedTree = true).assertExists()
        }
        onNodeWithTag("manga-status-DOING").assertIsSelected()
        onNodeWithTag("manga-collection-pager").performTouchInput { swipeLeft() }
        waitForIdle()
        onNodeWithTag("manga-status-ON_HOLD").assertIsSelected()
        runOnIdle { assertEquals(MangaCollectionType.ON_HOLD, selected) }
        onNodeWithTag("manga-collection-pager").performTouchInput { swipeRight() }
        waitForIdle()
        onNodeWithTag("manga-status-DOING").assertIsSelected()
        onNodeWithTag("manga-status-DONE").performClick()
        waitForIdle()
        onNodeWithTag("manga-status-DONE").assertIsSelected()
        runOnIdle { assertEquals(MangaCollectionType.DONE, selected) }
    }

}
