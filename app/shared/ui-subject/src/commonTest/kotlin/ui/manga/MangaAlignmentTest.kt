/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.manga

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.repository.manga.MangaChapter
import me.him188.ani.app.data.repository.manga.MangaPage
import me.him188.ani.app.data.repository.manga.MangaCollectionType
import me.him188.ani.app.data.repository.manga.MangaLibraryEntry
import me.him188.ani.app.data.repository.manga.MangaSource
import me.him188.ani.app.data.repository.manga.MangaSourceSettings
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNull

@OptIn(TestOnly::class)
class MangaAlignmentTest {
    @Test fun collectionMenuUsesReadingLabelsAndEditsTheSubject() = runAniComposeUiTest {
        var selected: MangaCollectionType? = null
        setContent {
            ProvideCompositionLocalsForPreview {
                CompositionLocalProvider(LocalMangaActions provides MangaPageActions(setCollection = { selected = it })) {
                    MangaLibraryControls(MangaDetailsUiState(libraryLoaded = true,
                        library = MangaLibraryEntry(MangaCollectionType.DOING, 12, 2)))
                }
            }
        }
        onNodeWithTag("manga-collection-edit").performClick()
        onNodeWithText("读过").performClick()
        assertEquals(MangaCollectionType.DONE, selected)
    }

    @Test fun reviewPageOffersTheSharedRatingEditorAction() = runAniComposeUiTest {
        var edits = 0
        setContent {
            ProvideCompositionLocalsForPreview {
                CompositionLocalProvider(LocalMangaActions provides MangaPageActions(writeReview = { edits++ })) {
                    Box(Modifier.requiredSize(380.dp, 650.dp)) { MangaReviewFeed(MangaDetailsUiState()) }
                }
            }
        }
        onNodeWithTag("manga-write-review").performClick()
        assertEquals(1, edits)
        onNodeWithText("暂无评论").assertExists()
    }

    @Test fun sourceManagementReordersAutomaticPriority() = runAniComposeUiTest {
        var saved: MangaSourceSettings? = null
        setContent {
            ProvideCompositionLocalsForPreview {
                CompositionLocalProvider(LocalMangaActions provides MangaPageActions(sourceSettings = { saved = it })) {
                    MangaSourcesDialog(MangaDetailsUiState(), "Test", {}, { _, _ -> }, {}, {})
                }
            }
        }
        onNodeWithTag("manga-source-manage").performClick()
        onNodeWithTag("manga-source-up-MANHUAGUI").performScrollTo().performClick()
        assertEquals(MangaSource.MANHUAGUI, saved?.all?.first())
        onNodeWithTag("manga-source-enabled-KOMIIC").performClick()
        assertTrue(MangaSource.KOMIIC in saved!!.disabled)
    }
    @Test fun sourceSwitchDoesNotGuessBetweenDuplicateChapterNumbers() {
        val current = MangaChapter("old", "One", 1.0, "章节")
        val target = MangaChapter("new", "One", 1.0, "章节")
        assertEquals(target, matchingMangaChapter(current, listOf(target)))
        assertNull(matchingMangaChapter(current, listOf(target, target.copy(id = "duplicate"))))
        assertNull(matchingMangaChapter(current, listOf(target.copy(group = "单行本"))))
        assertNull(matchingMangaChapter(current.copy(number = null), listOf(target)))
    }

    @Test fun failedFinalPageDoesNotMarkChapterRead() = runAniComposeUiTest {
        var completions = 0
        setContent {
            ProvideCompositionLocalsForPreview {
                MangaReaderPages(listOf(MangaPage("invalid", "")), 0, {}, { error("Network failure") }, null, null,
                    Modifier.requiredSize(380.dp, 650.dp), onReadCompleted = { completions++ })
            }
        }
        mainClock.advanceTimeBy(6000)
        waitForIdle()
        assertEquals(0, completions)
    }

}
