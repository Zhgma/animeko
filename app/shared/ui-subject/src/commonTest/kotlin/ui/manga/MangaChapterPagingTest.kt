/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.manga

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.repository.manga.MangaChapter
import me.him188.ani.app.data.repository.manga.MangaCollectionType
import me.him188.ani.app.data.repository.manga.MangaLibraryEntry
import me.him188.ani.app.data.repository.manga.MangaReadingMemory
import me.him188.ani.app.data.repository.manga.MangaSource
import me.him188.ani.app.data.repository.manga.MangaSourceBook
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MangaChapterPagingTest {
    private val book = MangaSourceBook(MangaSource.KOMIIC, "book", "Manga")
    private val chapters = (1..72).map { MangaChapter("chapter-$it", "第${it}话", it.toDouble(), "章节") }
    private val volumes = (1..72).map { MangaChapter("volume-$it", "第${it}卷", it.toDouble(), "单行本") }

    @Test
    fun defaultsToDescendingAndSwitchKeepsReadingChapterVisibleAndSelectsTheCorrectChapter() = runAniComposeUiTest {
        var selected: MangaChapter? = null
        setContent {
            ProvideCompositionLocalsForPreview {
                Box(Modifier.requiredWidth(420.dp)) {
                    MangaChapterGrid(MangaDetailsUiState(book = book, chapters = chapters, chapter = chapters[60]), { selected = it })
                }
            }
        }
        onNodeWithText("倒序").assertIsDisplayed()
        onNodeWithText("7 – 12 / 72").assertIsDisplayed()
        val first = onNodeWithText("第66话").fetchSemanticsNode().boundsInRoot
        val last = onNodeWithText("第61话").fetchSemanticsNode().boundsInRoot
        assertTrue(first.top < last.top)
        onNodeWithText("第61话").performClick()
        runOnIdle { assertEquals(chapters[60], selected) }
        onNodeWithTag("manga-chapter-sort").performClick()
        onNodeWithText("正序").assertIsDisplayed()
        onNodeWithText("61 – 66 / 72").assertIsDisplayed()
    }

    @Test
    fun delayedBangumiProgressSelectsPageContaining61AndLocalReadingTakesPriority() = runAniComposeUiTest {
        var state by mutableStateOf(MangaDetailsUiState(book = book, chapters = chapters))
        setContent {
            ProvideCompositionLocalsForPreview {
                Box(Modifier.requiredWidth(420.dp)) { MangaChapterGrid(state, {}) }
            }
        }
        onNodeWithText("1 – 6 / 72").assertIsDisplayed()
        runOnIdle { state = state.copy(library = MangaLibraryEntry(MangaCollectionType.DOING, 61, 0)) }
        onNodeWithText("7 – 12 / 72").assertIsDisplayed()
        runOnIdle { state = state.copy(memory = MangaReadingMemory(book, chapters[66].id)) }
        onNodeWithText("1 – 6 / 72").assertIsDisplayed()
        runOnIdle { state = state.copy(chapter = chapters[12]) }
        onNodeWithText("55 – 60 / 72").assertIsDisplayed()
    }

    @Test
    fun restoresMemoryGroupAndKeepsManualPagingUntilReadingPositionOrCapacityChanges() = runAniComposeUiTest {
        var state by mutableStateOf(MangaDetailsUiState(book = book, chapters = chapters + volumes,
            memory = MangaReadingMemory(book, volumes[60].id)))
        var width by mutableStateOf(420.dp)
        setContent {
            ProvideCompositionLocalsForPreview {
                Box(Modifier.requiredWidth(width)) { MangaChapterGrid(state, {}) }
            }
        }
        onNodeWithText("单行本").assertIsSelected()
        onNodeWithText("7 – 12 / 72").assertIsDisplayed()
        onNode(hasContentDescription("下一页") or hasContentDescription("Next page")).performClick()
        onNodeWithText("13 – 18 / 72").assertIsDisplayed()
        runOnIdle { state = state.copy(memory = state.memory!!.copy(page = 4), commentsTotal = 12) }
        onNodeWithText("13 – 18 / 72").assertIsDisplayed()
        runOnIdle { width = 560.dp }
        onNodeWithText("9 – 16 / 72").assertIsDisplayed()
        runOnIdle { state = state.copy(chapter = volumes[66]) }
        onNodeWithText("1 – 8 / 72").assertIsDisplayed()
    }

    @Test
    fun switchingSubjectDoesNotReuseTheManuallySelectedPage() = runAniComposeUiTest {
        var state by mutableStateOf(MangaDetailsUiState(subjectId = 1, book = book, chapters = chapters, chapter = chapters[60]))
        setContent {
            ProvideCompositionLocalsForPreview {
                Box(Modifier.requiredWidth(420.dp)) { MangaChapterGrid(state, {}) }
            }
        }
        onNodeWithText("7 – 12 / 72").assertIsDisplayed()
        onNode(hasContentDescription("下一页") or hasContentDescription("Next page")).performClick()
        onNodeWithText("13 – 18 / 72").assertIsDisplayed()
        runOnIdle { state = state.copy(subjectId = 2, book = book.copy(id = "another")) }
        onNodeWithText("7 – 12 / 72").assertIsDisplayed()
    }

    @Test
    fun resolvesActualCatalogEntriesAndDoesNotTreatChapterNumbersAsListIndices() {
        val state = MangaDetailsUiState(book = book, chapters = listOf(chapters[60], chapters[66]),
            memory = MangaReadingMemory(book.copy(id = "another"), chapters[66].id),
            library = MangaLibraryEntry(MangaCollectionType.DOING, 61, 0))
        assertEquals(chapters[60], mangaChapterGridAnchor(state))
        assertNull(mangaChapterGridAnchor(state.copy(library = null)))
        assertEquals(volumes[6], mangaChapterGridAnchor(state.copy(chapters = volumes,
            library = MangaLibraryEntry(MangaCollectionType.DOING, 0, 7))))
        assertNull(mangaChapterGridAnchor(state.copy(chapters = emptyList())))
    }
}
