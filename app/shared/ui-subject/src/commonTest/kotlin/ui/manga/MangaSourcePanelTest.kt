/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.manga

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.repository.manga.MangaChapter
import me.him188.ani.app.data.repository.manga.MangaSource
import me.him188.ani.app.data.repository.manga.MangaSourceBook
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(TestOnly::class)
class MangaSourcePanelTest {
    @Test
    fun sharedSourceRowsSelectTheBookAndExposeAutomaticSelection() = runAniComposeUiTest {
        val book = MangaSourceBook(MangaSource.GODA, "a", "碧蓝之海")
        var selected: MangaSourceBook? = null
        var autoRequests = 0
        setContent {
            ProvideCompositionLocalsForPreview {
                Box(Modifier.requiredSize(380.dp, 900.dp)) {
                    MangaChapterList(MangaDetailsUiState(sourceSearches = mapOf(MangaSource.GODA to MangaSourceSearchState("碧蓝之海", listOf(book)))),
                        "检索词", { _, _ -> }, { selected = it }, {}, {}, { autoRequests++ })
                }
            }
        }
        onNodeWithTag("manga-auto-source").performScrollTo().performClick()
        assertEquals(1, autoRequests)
        onNodeWithText("碧蓝之海", substring = false).performScrollTo().performClick()
        assertEquals(book, selected)
    }

    @Test
    fun volumeOnlyCatalogIsVisibleWithoutSelectingAMissingChapterGroup() = runAniComposeUiTest {
        val volume = MangaChapter("vol-1", "第01卷", 1.0, "单行本")
        var selected: MangaChapter? = null
        setContent {
            ProvideCompositionLocalsForPreview {
                Box(Modifier.requiredSize(380.dp, 700.dp)) {
                    MangaChapterList(MangaDetailsUiState(book = MangaSourceBook(MangaSource.MANHUAGUI, "1", "Test"), chapters = listOf(volume)),
                        "Test", { _, _ -> }, {}, { selected = it }, {})
                }
            }
        }
        onNodeWithTag("manga-chapter-vol-1").performScrollTo().performClick()
        assertEquals(volume, selected)
    }
}
