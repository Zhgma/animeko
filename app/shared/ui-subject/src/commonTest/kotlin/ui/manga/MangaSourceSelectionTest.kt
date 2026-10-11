/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.manga

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.repository.manga.MangaChapter
import me.him188.ani.app.data.repository.manga.MangaSource
import me.him188.ani.app.data.repository.manga.MangaSourceBook
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MangaSourceSelectionTest {
    private val chapter = MangaChapter("1", "第1话", 1.0, "章节")

    @Test
    fun automaticTitleMatchingDoesNotAcceptRelatedButDifferentWorks() {
        assertTrue(mangaTitlesMatch("GRANDBLUE碧藍之海", "碧蓝之海"))
        assertFalse(mangaTitlesMatch("同人碧蓝之海", "碧蓝之海"))
        assertFalse(mangaTitlesMatch("碧蓝之海公式书", "碧蓝之海"))
    }

    @Test
    fun automaticallyFallsBackWhenTheImageCannotBeDownloaded() = runTest {
        val statuses = mutableMapOf<MangaSource, MangaSourceSearchState>()
        val result = findReadableMangaSource(listOf("碧蓝之海"),
            { source, _ -> listOf(MangaSourceBook(source, "book", "GRANDBLUE碧藍之海")) },
            { listOf(chapter) }, { book, _ -> if (book.source == MangaSource.KOMIIC) error("HTTP 429") },
            { source, state -> statuses[source] = state })
        assertEquals(MangaSource.MANHUAGUI, result?.book?.source)
        assertEquals("HTTP 429", statuses[MangaSource.KOMIIC]?.error)
        assertFalse(statuses.getValue(MangaSource.MANHUAGUI).loading)
    }

    @Test
    fun doesNotPickUnrelatedSearchResultsAndTimeoutIsNotAnEmptyResult() = runTest {
        val statuses = mutableMapOf<MangaSource, MangaSourceSearchState>()
        val result = findReadableMangaSource(listOf("碧蓝之海"),
            { source, _ -> if (source == MangaSource.KOMIIC) delay(40_000)
                listOf(MangaSourceBook(source, "wrong", "碧蓝航线")) },
            { error("Must not open unrelated manga") }, { _, _ -> error("Must not download") },
            { source, state -> statuses[source] = state }, listOf(MangaSource.KOMIIC, MangaSource.MANHUAGUI))
        assertNull(result)
        assertTrue(statuses.getValue(MangaSource.KOMIIC).error!!.contains("超时"))
        assertNull(statuses.getValue(MangaSource.MANHUAGUI).error)
    }

    @Test
    fun manualCancellationStopsAutomaticFallback() = runTest {
        var searches = 0
        assertFailsWith<CancellationException> {
            findReadableMangaSource(listOf("Test"), { _, _ -> searches++; throw CancellationException() },
                { emptyList() }, { _, _ -> }, { _, _ -> })
        }
        assertEquals(1, searches)
    }
}
