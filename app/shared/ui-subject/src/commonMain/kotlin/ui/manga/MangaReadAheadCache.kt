/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.manga

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.him188.ani.app.data.repository.manga.MangaChapter
import me.him188.ani.app.data.repository.manga.MangaPage
import me.him188.ani.app.data.repository.manga.MangaSourceBook
import me.him188.ani.app.data.repository.manga.MangaSourceAccessException

data class MangaDownloadProgress(
    val title: String, val completed: Int = 0, val total: Int = 0, val error: String? = null,
)

/** Current and next chapter catalogs and images share the foreground reader's cache. */
internal class MangaReadAheadCache(
    scope: CoroutineScope,
    private val fetchPages: suspend (MangaSourceBook, MangaChapter) -> List<MangaPage>,
    private val fetchImage: suspend (MangaPage) -> String,
    private val discardImage: (String) -> Unit,
) {
    private val downloadsJob = SupervisorJob(scope.coroutineContext[Job])
    private val downloadsScope = CoroutineScope(scope.coroutineContext + downloadsJob)
    private val downloadSlots = Semaphore(3)
    private val pending = mutableMapOf<MangaPage, Deferred<String>>()
    private val catalogMutex = Mutex()
    private val imageMutex = Mutex()
    private val catalogs = mutableMapOf<String, List<MangaPage>>()
    private val images = mutableMapOf<MangaPage, String>()

    private fun key(book: MangaSourceBook, chapter: MangaChapter) = "${book.source}:${book.id}:${chapter.id}"
    // MangaGui's e/m query parameters authorize an immutable page; refreshed signatures share downloaded files.
    private fun imageKey(page: MangaPage) = if (page.url.startsWith("https://us.hamreus.com/"))
        page.copy(url = page.url.substringBefore('?')) else page

    suspend fun pages(book: MangaSourceBook, chapter: MangaChapter): List<MangaPage> = catalogMutex.withLock {
        catalogs.getOrPut(key(book, chapter)) { fetchPages(book, chapter) }
    }

    suspend fun invalidateCatalogs(book: MangaSourceBook) = catalogMutex.withLock {
        catalogs.keys.removeAll { it.startsWith("${book.source}:${book.id}:") }
    }

    suspend fun image(page: MangaPage): String {
        val key = imageKey(page)
        val task = imageMutex.withLock {
            images[key]?.let { return it }
            pending.getOrPut(key) {
                downloadsScope.async {
                    try {
                        val uri = downloadSlots.withPermit { fetchImage(page) }
                        imageMutex.withLock { images[key] = uri }
                        uri
                    } finally {
                        withContext(NonCancellable) { imageMutex.withLock { pending.remove(key) } }
                    }
                }
            }
        }
        // Cancelling a LazyColumn item's await never cancels the chapter-owned download.
        return task.await()
    }

    suspend fun clear() {
        downloadsJob.cancelAndJoin()
        imageMutex.withLock { images.values.forEach(discardImage); images.clear() }
        catalogMutex.withLock { catalogs.clear() }
    }

    suspend fun prefetch(book: MangaSourceBook, chapters: List<MangaChapter>, onProgress: (Int, MangaDownloadProgress) -> Unit) {
        val retained = mutableSetOf<MangaPage>()
        var allCatalogsLoaded = true
        chapters.forEachIndexed { index, chapter ->
            var completed = 0
            var total = 0
            var failure: String? = null
            try {
                val pages = pages(book, chapter)
                retained.addAll(pages.map(::imageKey))
                total = pages.size
                onProgress(index, MangaDownloadProgress(chapter.title, 0, total))
                for (page in pages) {
                    try { image(page); completed++ }
                    catch (e: CancellationException) { throw e }
                    catch (e: MangaSourceAccessException) {
                        onProgress(index, MangaDownloadProgress(chapter.title, completed, total, e.message))
                        chapters.drop(index + 1).forEachIndexed { remaining, next ->
                            onProgress(index + remaining + 1, MangaDownloadProgress(next.title, error = e.message))
                        }
                        return
                    }
                    catch (e: Exception) { failure = e.message ?: "图片下载失败" }
                    onProgress(index, MangaDownloadProgress(chapter.title, completed, total, failure))
                }
            } catch (e: CancellationException) { throw e }
            catch (e: MangaSourceAccessException) {
                onProgress(index, MangaDownloadProgress(chapter.title, completed, total, e.message))
                chapters.drop(index + 1).forEachIndexed { remaining, next ->
                    onProgress(index + remaining + 1, MangaDownloadProgress(next.title, error = e.message))
                }
                return
            }
            catch (e: Exception) { allCatalogsLoaded = false; failure = e.message ?: "章节加载失败" }
            onProgress(index, MangaDownloadProgress(chapter.title, completed, total, failure))
        }
        // Keep both chapters, including failed pages for retry; older chapters can be released.
        if (allCatalogsLoaded) {
            imageMutex.withLock {
                images.keys.filter { it !in retained }.forEach { images.remove(it)?.let(discardImage) }
            }
            catalogMutex.withLock { catalogs.keys.retainAll(chapters.map { key(book, it) }.toSet()) }
        }
    }
}
