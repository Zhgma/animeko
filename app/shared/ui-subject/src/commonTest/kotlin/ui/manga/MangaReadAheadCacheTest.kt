/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.manga

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.repository.manga.MangaChapter
import me.him188.ani.app.data.repository.manga.MangaPage
import me.him188.ani.app.data.repository.manga.MangaSource
import me.him188.ani.app.data.repository.manga.MangaSourceBook
import me.him188.ani.app.data.repository.manga.MangaSourceAccessException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class MangaReadAheadCacheTest {
    private val book = MangaSourceBook(MangaSource.KOMIIC, "book", "Test")
    private val chapters = List(3) { MangaChapter("$it", "Chapter $it", it.toDouble(), "章节") }
    private fun pages(chapter: MangaChapter) = List(3) { MangaPage("${chapter.id}/$it", "referer") }

    @Test
    fun refreshingMangaGuiSignaturesRetainsAlreadyDownloadedPages() = runTest {
        var requests = 0
        val cache = MangaReadAheadCache(backgroundScope, { _, _ -> emptyList() }, { requests++; "file://cached" }, {})
        val page = MangaPage("https://us.hamreus.com/comic/1.webp?e=old&m=old", "https://www.manhuagui.com/")
        assertEquals("file://cached", cache.image(page))
        assertEquals("file://cached", cache.image(page.copy(url = page.url.substringBefore('?') + "?e=new&m=new")))
        assertEquals(1, requests)
        cache.clear()
    }

    @Test
    fun quotaFailureStopsWholeChapterRequestsAndRefreshGetsNewSignedUrls() = runTest {
        var requests = 0
        var version = "expired"
        val cache = MangaReadAheadCache(backgroundScope,
            { _, _ -> listOf(MangaPage("https://images.test/page?signature=$version", "")) },
            { requests++; if (version == "expired") throw MangaSourceAccessException("额度用完") else it.url }, {})
        val progress = mutableMapOf<Int, MangaDownloadProgress>()
        cache.prefetch(book, chapters.take(2)) { i, p -> progress[i] = p }
        assertEquals(1, requests)
        assertEquals("额度用完", progress[1]?.error)
        version = "new"
        cache.invalidateCatalogs(book)
        val refreshed = cache.pages(book, chapters.first()).single()
        assertEquals("https://images.test/page?signature=new", cache.image(refreshed))
        assertEquals(2, requests)
        cache.clear()
    }

    @Test
    fun downloadsBothWholeChaptersAndReusesNextChapterWithoutNetwork() = runTest {
        var catalogs = 0
        var downloads = 0
        val discarded = mutableListOf<String>()
        val cache = MangaReadAheadCache(backgroundScope, { _, chapter -> catalogs++; pages(chapter) },
            { downloads++; delay(1); it.url }, discarded::add)
        val progress = mutableMapOf<Int, MangaDownloadProgress>()
        val visible = async { cache.image(pages(chapters[0])[0]) }
        cache.prefetch(book, chapters.take(2)) { i, p -> progress[i] = p }
        visible.await()
        assertEquals(6, downloads)
        assertEquals(2, catalogs)
        assertEquals(listOf(3, 3), progress.values.map { it.completed })
        cache.pages(book, chapters[1]).forEach { cache.image(it) }
        assertEquals(6, downloads)
        assertEquals(2, catalogs)
        cache.prefetch(book, chapters.drop(1)) { _, _ -> }
        assertEquals(9, downloads)
        assertEquals(3, discarded.size)
    }

    @Test
    fun failedPageDoesNotBlockNextChapterAndRetryKeepsCompletedDownloads() = runTest {
        var fail = true
        var downloads = 0
        val progress = mutableMapOf<Int, MangaDownloadProgress>()
        val cache = MangaReadAheadCache(backgroundScope, { _, chapter -> pages(chapter) }, {
            if (it.url == "0/1" && fail) error("Network failed")
            downloads++; it.url
        }, {})
        cache.prefetch(book, chapters.take(2)) { i, p -> progress[i] = p }
        assertEquals(2, progress[0]?.completed)
        assertNotNull(progress[0]?.error)
        assertEquals(3, progress[1]?.completed)
        fail = false
        cache.prefetch(book, chapters.take(2)) { i, p -> progress[i] = p }
        assertEquals(6, downloads)
        assertEquals(3, progress[0]?.completed)
        assertEquals(null, progress[0]?.error)
    }

    @Test
    fun scrollingAwayDoesNotCancelAnImageDownload() = runTest {
        var completed = 0
        val cache = MangaReadAheadCache(backgroundScope, { _, chapter -> pages(chapter) }, {
            delay(1000); completed++; it.url
        }, {})
        val page = pages(chapters[0])[0]
        val visibleItem = async { cache.image(page) }
        runCurrent()
        visibleItem.cancel()
        advanceTimeBy(1100)
        runCurrent()
        assertEquals(1, completed)
        assertEquals(page.url, cache.image(page))
        assertEquals(1, completed)
        cache.clear()
    }

    @Test
    fun cancellationIsNotRecordedAsDownloadFailure() = runTest {
        val cache = MangaReadAheadCache(backgroundScope, { _, chapter -> pages(chapter) }, { throw CancellationException() }, {})
        assertFailsWith<CancellationException> { cache.prefetch(book, chapters.take(2)) { _, _ -> } }
    }
}
