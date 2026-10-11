/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.manga

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.him188.ani.app.data.repository.manga.MangaChapter
import me.him188.ani.app.data.repository.manga.MangaPage
import me.him188.ani.app.data.repository.manga.MangaSourceBook
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.createDirectories
import me.him188.ani.utils.io.delete
import me.him188.ani.utils.io.exists
import me.him188.ani.utils.io.moveTo
import me.him188.ani.utils.io.readText
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.io.writeBytes
import me.him188.ani.utils.io.writeText
import kotlin.random.Random

@Serializable
data class MangaOfflineChapter(val book: MangaSourceBook, val chapter: MangaChapter,
    val files: List<String>, val total: Int, val error: String? = null) {
    val complete: Boolean get() = total > 0 && files.size == total
}

/** An atomic manifest publishes only fully written images. Incomplete chapters can be resumed. */
internal class MangaOfflineStore(private val directory: SystemPath) {
    private val lock = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val manifest = directory.resolve("index.json")
    private fun read(): List<MangaOfflineChapter> = if (!manifest.exists()) emptyList()
        else json.decodeFromString(manifest.readText())
    suspend fun list(): List<MangaOfflineChapter> = lock.withLock {
        read().map { entry -> entry.copy(files = entry.files.takeWhile { directory.resolve(it).exists() }) }
    }
    suspend fun enqueue(book: MangaSourceBook, chapters: List<MangaChapter>) = lock.withLock {
        val entries = read()
        write(entries + chapters.filter { chapter -> entries.none { same(it, book, chapter) } }
            .map { MangaOfflineChapter(book, it, emptyList(), 0) })
    }
    private fun write(items: List<MangaOfflineChapter>) {
        directory.createDirectories()
        val pending = directory.resolve("index.tmp")
        pending.writeText(json.encodeToString(items))
        pending.moveTo(manifest)
    }
    private fun same(a: MangaOfflineChapter, book: MangaSourceBook, chapter: MangaChapter) =
        a.book.source == book.source && a.book.id == book.id && a.chapter.id == chapter.id
    suspend fun pages(book: MangaSourceBook, chapter: MangaChapter): List<MangaPage>? = lock.withLock {
        read().firstOrNull { same(it, book, chapter) && it.complete && it.files.all { file -> directory.resolve(file).exists() } }
            ?.files?.map { MangaPage(mangaImageFileUri(directory.resolve(it).absolutePath), "") }
    }
    suspend fun download(book: MangaSourceBook, chapter: MangaChapter, pages: List<MangaPage>,
        image: suspend (MangaPage) -> ByteArray, progress: (MangaOfflineChapter) -> Unit,
    ) {
        var entry = lock.withLock {
            read().firstOrNull { same(it, book, chapter) }?.takeIf {
                it.total == pages.size && it.files.all { name -> directory.resolve(name).exists() }
            }?.copy(error = null) ?: MangaOfflineChapter(book, chapter, emptyList(), pages.size)
        }
        suspend fun publish() {
            lock.withLock { write(read().filterNot { same(it, book, chapter) } + entry) }
            progress(entry)
        }
        publish()
        try {
            for (page in pages.drop(entry.files.size)) {
                val bytes = image(page)
                val name = "${Random.nextLong().toULong()}.image"
                lock.withLock { directory.createDirectories(); directory.resolve(name).writeBytes(bytes) }
                entry = entry.copy(files = entry.files + name)
                publish()
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { entry = entry.copy(error = e.message ?: "缓存失败"); publish() }
    }
    suspend fun remove(entry: MangaOfflineChapter) = lock.withLock {
        write(read().filterNot { same(it, entry.book, entry.chapter) })
        entry.files.forEach { directory.resolve(it).delete() }
    }
}
