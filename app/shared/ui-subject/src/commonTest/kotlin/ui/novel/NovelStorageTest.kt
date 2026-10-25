/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.novel

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.repository.novel.*
import me.him188.ani.utils.io.SystemPaths
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.createTempDirectory
import me.him188.ani.utils.io.createDirectories
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.io.writeBytes
import me.him188.ani.utils.io.delete
import me.him188.ani.utils.io.exists
import me.him188.ani.app.ui.manga.mangaImageFileUri
import kotlin.test.*

class NovelStorageTest {
    @Test fun missingIllustrationMakesChapterRetryableAndDeleteRemovesOwnedImages() = runTest {
        val directory = SystemPaths.createTempDirectory("novel-offline-images")
        val images = directory.resolve("images").also { it.createDirectories() }
        val first = images.resolve("123.image").also { it.writeBytes(byteArrayOf(1)) }
        val second = images.resolve("456.image").also { it.writeBytes(byteArrayOf(2)) }
        val book = NovelBook(NovelSource.HUANMENG, "test", "test")
        val chapter = NovelChapter("1", "1")
        val store = NovelStorage(directory)
        store.save(book, chapter, listOf(NovelBlock(image = mangaImageFileUri(first.absolutePath)), NovelBlock(image = mangaImageFileUri(second.absolutePath))))
        assertNotNull(store.read(book, chapter))
        first.delete()
        assertNull(store.read(book, chapter))
        assertNull(store.list().single().file)
        store.remove(store.list().single())
        assertFalse(second.exists())
    }
    @Test fun interruptedQueueDoesNotPublishIncompleteChaptersAndResumesAfterRestart() = runTest {
        val directory = SystemPaths.createTempDirectory("novel-offline-test")
        val book = NovelBook(NovelSource.QUANBEN, "test", "测试")
        val chapter = NovelChapter("1", "第一章")
        val first = NovelStorage(directory)
        first.enqueue(book, listOf(chapter))
        assertNull(first.read(book, chapter))
        val restarted = NovelStorage(directory)
        assertNull(restarted.list().single().file)
        val blocks = listOf(NovelBlock("完整正文"))
        restarted.save(book, chapter, blocks)
        assertEquals(blocks, NovelStorage(directory).read(book, chapter))
        restarted.remove(restarted.list().single())
        assertNull(restarted.read(book, chapter))
        assertTrue(restarted.list().isEmpty())
    }

    @Test fun failedDownloadsStayRetryableAndSourceIdentitiesDoNotCollide() = runTest {
        val store = NovelStorage(SystemPaths.createTempDirectory("novel-offline-retry"))
        val book = NovelBook(NovelSource.QUANBEN, "test", "test")
        val chapter = NovelChapter("1", "1")
        var attempts = 0
        val queue = NovelDownloads(store, backgroundScope) { _, _ ->
            if (attempts++ == 0) error("network unavailable")
            listOf(NovelBlock("success"))
        }
        queue.download(book, listOf(chapter)); runCurrent()
        assertNull(queue.state.value.entries.single().file)
        assertEquals("network unavailable", queue.state.value.entries.single().error)
        queue.resume(); runCurrent()
        assertNotNull(queue.state.value.entries.single().file)
        assertNull(store.read(book.copy(source = NovelSource.HUANMENG), chapter))
    }
}
