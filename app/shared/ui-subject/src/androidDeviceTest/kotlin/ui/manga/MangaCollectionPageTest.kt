/*
 * Copyright (C) 2026 OpenAni and contributors.
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package me.him188.ani.app.ui.manga

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
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
                    state = MangaCollectionUiState(items = listOf(manga)),
                    selfInfo = TestSelfInfoUiState,
                    title = { TrackingCollectionTabs(true) {} },
                    onSelect = { selected = it },
                    onRefresh = {},
                    onClickLogin = {},
                    onClickSettings = {},
                    onManage = {},
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
}
