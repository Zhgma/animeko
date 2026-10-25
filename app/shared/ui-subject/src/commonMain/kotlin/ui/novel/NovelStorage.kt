/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.novel

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.him188.ani.app.data.repository.novel.NovelBlock
import me.him188.ani.app.data.repository.novel.NovelBook
import me.him188.ani.app.data.repository.novel.NovelChapter
import me.him188.ani.app.ui.manga.mangaImageFileUri
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.createDirectories
import me.him188.ani.utils.io.delete
import me.him188.ani.utils.io.exists
import me.him188.ani.utils.io.moveTo
import me.him188.ani.utils.io.readText
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.io.writeText
import kotlin.random.Random

@Serializable
internal data class NovelDownload(val book: NovelBook, val chapter: NovelChapter, val file: String? = null, val error: String? = null, val assets: List<String> = emptyList())

/** Manifest replacement occurs after the complete chapter has been written. */
internal class NovelStorage(private val directory: SystemPath) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val index = directory.resolve("index.json")
    private val images = directory.resolve("images")
    private fun complete(entry: NovelDownload) = entry.file?.let { directory.resolve(it).exists() } == true && entry.assets.all { images.resolve(it).exists() }
    private fun deleteFiles(entry: NovelDownload) {
        entry.file?.let { directory.resolve(it).delete() }
        entry.assets.forEach { images.resolve(it).delete() }
    }
    private fun readIndex(): List<NovelDownload> = if (index.exists()) json.decodeFromString(index.readText()) else emptyList()
    suspend fun list(): List<NovelDownload> = mutex.withLock { readIndex().map {
        if (it.file != null && !complete(it)) it.copy(file = null, error = "缓存文件缺失，请重试") else it
    } }
    private fun write(entries: List<NovelDownload>) {
        directory.createDirectories()
        val temporary = directory.resolve("index.tmp")
        temporary.writeText(json.encodeToString(entries)); temporary.moveTo(index)
    }
    private fun same(a: NovelDownload, book: NovelBook, chapter: NovelChapter) = a.book.source == book.source && a.book.id == book.id && a.chapter.id == chapter.id
    suspend fun read(book: NovelBook, chapter: NovelChapter): List<NovelBlock>? = mutex.withLock {
        val entry = readIndex().firstOrNull { same(it, book, chapter) } ?: return@withLock null
        if (!complete(entry)) return@withLock null
        directory.resolve(entry.file!!).readText().let { json.decodeFromString(it) }
    }
    suspend fun enqueue(book: NovelBook, chapters: List<NovelChapter>) = mutex.withLock {
        val old = readIndex()
        write(old + chapters.filter { c -> old.none { same(it, book, c) } }.map { NovelDownload(book, it) })
    }
    suspend fun save(book: NovelBook, chapter: NovelChapter, blocks: List<NovelBlock>) = mutex.withLock {
        require(blocks.isNotEmpty())
        directory.createDirectories()
        val old = readIndex(); val existing = old.firstOrNull { same(it, book, chapter) }
        val name = "${Random.nextLong().toULong()}.json"
        val prefix = mangaImageFileUri(images.absolutePath).trimEnd('/') + "/"
        val assets = blocks.mapNotNull { it.image?.takeIf { uri -> uri.startsWith(prefix) }?.removePrefix(prefix) }
        require(assets.all { Regex("[0-9]+\\.image").matches(it) && images.resolve(it).exists() })
        directory.resolve(name).writeText(json.encodeToString(blocks))
        write(old.filterNot { same(it, book, chapter) } + NovelDownload(book, chapter, name, assets = assets))
        existing?.let { deleteFiles(it.copy(assets = it.assets - assets.toSet())) }
    }
    suspend fun failed(entry: NovelDownload, message: String) = mutex.withLock {
        val old = readIndex()
        val previous = old.firstOrNull { same(it, entry.book, entry.chapter) } ?: entry
        write(old.filterNot { same(it, entry.book, entry.chapter) } + previous.copy(error = message))
    }
    suspend fun remove(entry: NovelDownload) = mutex.withLock {
        val old = readIndex()
        write(old.filterNot { same(it, entry.book, entry.chapter) })
        old.firstOrNull { same(it, entry.book, entry.chapter) }?.let(::deleteFiles)
    }
}

internal data class NovelDownloadState(val entries: List<NovelDownload> = emptyList(), val running: Boolean = false)

/** App-lifetime queue. Interrupted entries remain available for explicit retry after restart. */
internal class NovelDownloads(val store: NovelStorage, private val scope: CoroutineScope,
    private val content: suspend (NovelBook, NovelChapter) -> List<NovelBlock>,
) {
    private val mutable = MutableStateFlow(NovelDownloadState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    init { scope.launch { refresh(false) } }
    private suspend fun refresh(running: Boolean) { mutable.value = NovelDownloadState(store.list(), running) }
    fun download(book: NovelBook, chapters: List<NovelChapter>) {
        scope.launch { store.enqueue(book, chapters); resume() }
    }
    fun resume() {
        if (job?.isActive == true) return
        job = scope.launch {
            try {
                refresh(true)
                val attempted = mutableSetOf<String>()
                while (true) {
                    val entry = store.list().firstOrNull { it.file == null && "${it.book.source}:${it.book.id}:${it.chapter.id}" !in attempted } ?: break
                    attempted += "${entry.book.source}:${entry.book.id}:${entry.chapter.id}"
                    try { store.save(entry.book, entry.chapter, content(entry.book, entry.chapter)) }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { store.failed(entry, e.message ?: "缓存失败") }
                    refresh(true)
                }
            } finally { mutable.value = mutable.value.copy(running = false) }
        }
    }
    fun pause() { job?.cancel() }
    fun remove(entry: NovelDownload) { scope.launch { job?.cancelAndJoin(); store.remove(entry); refresh(false) } }
    companion object {
        private val instances = mutableMapOf<String, NovelDownloads>()
        fun get(key: String, create: () -> NovelDownloads): NovelDownloads = instances.getOrPut(key, create)
    }
}
