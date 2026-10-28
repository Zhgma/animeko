/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.novel

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.repository.novel.*
import me.him188.ani.app.data.repository.reading.ReadingAutoCache
import me.him188.ani.app.ui.manga.mangaImageFileUri
import me.him188.ani.utils.io.*
import kotlin.test.*

class NovelReadAheadCacheTest {
    private val book = NovelBook(NovelSource.QUANBEN, "test", "test")
    private val chapters = List(4) { NovelChapter("$it", "$it") }

    @Test fun restartReusesTextAndIllustrationAndJumpRetainsActualPrevious() = runTest {
        val root = SystemPaths.createTempDirectory("novel-auto")
        val disk = ReadingAutoCache(root)
        disk.enter()
        var requests = 0
        fun reader() = NovelReadAheadCache(disk) { _, chapter, directory, paths ->
            requests++
            directory.createDirectories()
            val image = directory.resolve("${chapter.id}.image")
            paths += image; image.writeBytes(byteArrayOf(1))
            listOf(NovelBlock("text ${chapter.id}"), NovelBlock(image = mangaImageFileUri(image.absolutePath)))
        }
        val first = reader()
        first.opened(book, chapters[0], chapters[1])
        val blocks = first.content(book, chapters[0]); first.content(book, chapters[1])
        disk.enter()
        val restarted = reader()
        restarted.opened(book, chapters[0], chapters[1])
        assertEquals(blocks, restarted.content(book, chapters[0])); assertEquals(2, requests)
        restarted.opened(book, chapters[2], chapters[3])
        restarted.content(book, chapters[2]); restarted.content(book, chapters[3])
        assertEquals(blocks, restarted.content(book, chapters[0])); assertEquals(4, requests)
        assertFalse(disk.images.resolve("1.image").exists())
        root.deleteRecursively()
    }

    @Test fun missingIllustrationRetriesAndChangingSourceDoesNotReuseAnotherBook() = runTest {
        val root = SystemPaths.createTempDirectory("novel-auto-retry")
        val disk = ReadingAutoCache(root); disk.enter()
        var requests = 0
        val cache = NovelReadAheadCache(disk) { selected, _, directory, paths ->
            directory.createDirectories()
            val image = directory.resolve("${requests++}.image")
            paths += image; image.writeBytes(byteArrayOf(1))
            listOf(NovelBlock(selected.source.name), NovelBlock(image = mangaImageFileUri(image.absolutePath)))
        }
        cache.opened(book, chapters[0], chapters[1]); cache.content(book, chapters[0])
        disk.images.resolve("0.image").delete()
        assertEquals(book.source.name, cache.content(book, chapters[0]).first().text)
        assertEquals(2, requests)
        val other = book.copy(source = NovelSource.HUANMENG)
        cache.opened(other, chapters[0], chapters[1])
        assertEquals(other.source.name, cache.content(other, chapters[0]).first().text)
        assertEquals(3, requests)
        root.deleteRecursively()
    }

    @Test fun cancelledChapterRemovesPartialIllustrations() = runTest {
        val root = SystemPaths.createTempDirectory("novel-auto-cancel")
        val disk = ReadingAutoCache(root); disk.enter()
        val image = disk.images.resolve("partial.image")
        val cache = NovelReadAheadCache(disk) { _, _, directory, paths ->
            directory.createDirectories(); paths += image; image.writeBytes(byteArrayOf(1))
            throw CancellationException()
        }
        cache.opened(book, chapters[0], chapters[1])
        assertFailsWith<CancellationException> { cache.content(book, chapters[0]) }
        assertFalse(image.exists())
        root.deleteRecursively()
    }
}
