/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.manga

import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.repository.manga.MangaChapter
import me.him188.ani.app.data.repository.manga.MangaPage
import me.him188.ani.app.data.repository.manga.MangaSource
import me.him188.ani.app.data.repository.manga.MangaSourceBook
import me.him188.ani.utils.io.createTempDirectory
import me.him188.ani.utils.io.SystemPaths
import me.him188.ani.utils.io.deleteRecursively
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.io.delete
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MangaOfflineStoreTest {
    @Test fun resumeAfterFailureAndReopenWithoutNetworkThenDelete() = runTest {
        val directory = SystemPaths.createTempDirectory("manga-offline-test")
        try {
            val book = MangaSourceBook(MangaSource.KOMIIC, "1", "Test")
            val chapter = MangaChapter("1", "Chapter", 1.0, "章节")
            val pages = List(3) { MangaPage("page-$it", "") }
            val store = MangaOfflineStore(directory)
            store.download(book, chapter, pages, { if (it == pages[1]) error("temporary failure") else byteArrayOf(1, 2) }, {})
            assertEquals(1, store.list().single().files.size)
            assertNull(store.pages(book, chapter))
            val reopened = MangaOfflineStore(directory)
            val downloaded = mutableListOf<MangaPage>()
            reopened.download(book, chapter, pages, { downloaded += it; byteArrayOf(3) }, {})
            assertEquals(pages.drop(1), downloaded)
            assertTrue(reopened.list().single().complete)
            assertEquals(3, MangaOfflineStore(directory).pages(book, chapter)?.size)
            reopened.remove(reopened.list().single())
            assertTrue(reopened.list().isEmpty())
            assertNull(reopened.pages(book, chapter))
        } finally { directory.deleteRecursively() }
    }

    @Test fun missingImageDoesNotAdvertiseReadableOfflineChapter() = runTest {
        val directory = SystemPaths.createTempDirectory("manga-missing-test")
        try {
            val store = MangaOfflineStore(directory)
            val book = MangaSourceBook(MangaSource.KOMIIC, "1", "Test")
            val chapter = MangaChapter("1", "Chapter", 1.0, "章节")
            store.download(book, chapter, listOf(MangaPage("a", "")), { byteArrayOf(1) }, {})
            directory.resolve(store.list().single().files.single()).delete()
            assertNull(store.pages(book, chapter))
        } finally { directory.deleteRecursively() }
    }
}
