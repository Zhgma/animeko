/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.novel

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import me.him188.ani.app.data.persistent.dataStores
import me.him188.ani.app.data.repository.novel.*
import me.him188.ani.app.domain.foundation.HttpClientProvider
import me.him188.ani.app.domain.foundation.get
import me.him188.ani.app.domain.media.download.MediaDownloadManager
import me.him188.ani.app.platform.Context
import me.him188.ani.app.platform.files
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.manga.mangaImageFileUri
import me.him188.ani.utils.io.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import kotlin.random.Random

internal data class NovelState(
    val book: NovelBook? = null, val chapters: List<NovelChapter> = emptyList(), val chapter: NovelChapter? = null,
    val blocks: List<NovelBlock> = emptyList(), val loading: Boolean = false, val error: String? = null,
    val results: List<NovelBook> = emptyList(), val searching: Boolean = false, val searchError: String? = null,
    val initialParagraph: Int = 0, val initialOffset: Int = 0,
    val settings: NovelReaderSettings = NovelReaderSettings(), val readAhead: String = "",
    val localBooks: List<NovelBook> = emptyList(),
)

internal class NovelViewModel(private val id: Int, context: Context) : AbstractViewModel(), KoinComponent {
    private val source = NovelSourceRepository(get<HttpClientProvider>().get())
    private val preferences = context.dataStores.preferencesStore
    private val key = stringPreferencesKey("novel.reader.$id")
    private val settingsKey = stringPreferencesKey("novel.reader.settings")
    private val filesKey = stringPreferencesKey("novel.files.$id")
    private val directory = context.files.dataDir.resolve("novels").resolve(id.toString())
    private val imageDirectory = context.files.cacheDir.resolve("novel-reader-${Random.nextLong().toULong()}")
    private val cachedImages = mutableListOf<SystemPath>()
    private val json = Json { ignoreUnknownKeys = true }
    private val mutable = MutableStateFlow(NovelState())
    val state = mutable.asStateFlow()
    val downloads = NovelDownloads.get(directory.absolutePath) {
        val repository = source
        val assets = directory.resolve("offline").resolve("images")
        NovelDownloads(NovelStorage(directory.resolve("offline")), get<MediaDownloadManager>().backgroundScope) { book, chapter ->
            val paths = mutableListOf<SystemPath>()
            try { cacheNovelImages(repository, book, chapter, assets, paths) }
            catch (e: Exception) { paths.forEach { it.delete() }; throw e }
        }
    }
    private var memory: NovelMemory? = null
    private var document: NovelDocument? = null
    private val readAhead = linkedMapOf<String, List<NovelBlock>>()
    private val cacheMutex = Mutex()
    private var chapterJob: Job? = null
    private var selectionJob: Job? = null
    private var searchJob: Job? = null
    private var prefetchJob: Job? = null
    private var saveJob: Job? = null
    private var settingsJob: Job? = null
    private var generation = 0

    init {
        selectionJob = backgroundScope.launch {
            try {
                val saved = preferences.data.first()
                memory = saved[key]?.let { json.decodeFromString<NovelMemory>(it) }
                val settings = saved[settingsKey]?.let { json.decodeFromString<NovelReaderSettings>(it) } ?: NovelReaderSettings()
                val localBooks = saved[filesKey]?.let { json.decodeFromString<List<NovelBook>>(it) }.orEmpty()
                mutable.update { it.copy(settings = settings, localBooks = localBooks) }
                memory?.let { m ->
                    if (m.book.source == NovelSource.LOCAL) document = json.decodeFromString(directory.resolve("${m.book.id}.json").readText())
                    mutable.update { it.copy(book = m.book, chapters = m.chapters) }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(error = "阅读记录加载失败：${e.message}") } }
        }
    }

    fun search(provider: NovelSource, keyword: String) {
        searchJob?.cancel()
        mutable.update { it.copy(searching = true, searchError = null, results = emptyList()) }
        searchJob = backgroundScope.launch {
            try { val result = source.search(provider, keyword); mutable.update { it.copy(results = result, searching = false) } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(searching = false, searchError = e.message ?: "搜索失败") } }
        }
    }

    fun select(book: NovelBook) {
        selectionJob?.cancel(); chapterJob?.cancel(); prefetchJob?.cancel(); generation++
        mutable.update { it.copy(loading = true, error = null) }
        selectionJob = backgroundScope.launch {
            try {
                val chapters = source.chapters(book)
                saveJob?.cancel()
                document = null; cacheMutex.withLock { readAhead.clear() }
                memory = savedPosition(book)?.copy(chapters = chapters) ?: NovelMemory(book, chapters)
                persist()
                mutable.update { it.copy(book = book, chapters = chapters, chapter = null, blocks = emptyList(), loading = false) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(loading = false, error = e.message ?: "目录加载失败") } }
        }
    }

    suspend fun import(name: String, bytes: ByteArray) {
        selectionJob?.cancel(); chapterJob?.cancel(); prefetchJob?.cancel(); generation++
        val importingGeneration = generation
        val created = mutableListOf<SystemPath>()
        var committed = false
        mutable.update { it.copy(loading = true, error = null) }
        try {
            directory.createDirectories()
            val parsed = readNovelFile(name, bytes) { asset ->
                val file = directory.resolve("${Random.nextLong().toULong()}.image")
                created += file
                file.writeBytes(asset); mangaImageFileUri(file.absolutePath)
            }
            currentCoroutineContext().ensureActive()
            if (generation != importingGeneration) return
            val localId = "local-${Random.nextLong().toULong()}"
            val file = directory.resolve("$localId.json")
            created += file
            file.writeText(json.encodeToString(parsed))
            val book = NovelBook(NovelSource.LOCAL, localId, parsed.title)
            saveJob?.cancel(); document = parsed; cacheMutex.withLock { readAhead.clear() }
            memory = savedPosition(book)?.copy(chapters = parsed.chapters) ?: NovelMemory(book, parsed.chapters); persist()
            val localBooks = state.value.localBooks + book
            preferences.edit { it[filesKey] = json.encodeToString(localBooks) }
            committed = true
            mutable.update { it.copy(book = book, chapters = parsed.chapters, chapter = null, blocks = emptyList(), loading = false, localBooks = localBooks) }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { if (generation == importingGeneration) mutable.update { it.copy(loading = false, error = e.message ?: "文件导入失败") } }
        finally { if (!committed) created.forEach { it.delete() } }
    }

    fun selectLocal(book: NovelBook) {
        selectionJob?.cancel(); chapterJob?.cancel(); prefetchJob?.cancel(); generation++
        selectionJob = backgroundScope.launch {
            try {
                require(book.source == NovelSource.LOCAL && book in state.value.localBooks)
                val parsed = json.decodeFromString<NovelDocument>(directory.resolve("${book.id}.json").readText())
                saveJob?.cancel(); document = parsed
                cacheMutex.withLock { readAhead.clear() }
                memory = savedPosition(book)?.copy(chapters = parsed.chapters) ?: NovelMemory(book, parsed.chapters); persist()
                mutable.update { it.copy(book = book, chapters = parsed.chapters, chapter = null, blocks = emptyList(), loading = false, error = null) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { reportImportError(e.message ?: "文件读取失败") }
        }
    }

    fun open(chapter: NovelChapter) {
        val book = state.value.book ?: return
        chapterJob?.cancel(); prefetchJob?.cancel()
        val currentGeneration = generation
        mutable.update { it.copy(chapter = chapter, blocks = emptyList(), loading = true, error = null, readAhead = "") }
        chapterJob = backgroundScope.launch {
            try {
                val blocks = content(book, chapter)
                if (currentGeneration != generation) return@launch
                val saved = memory?.takeIf { it.chapterId == chapter.id }
                mutable.update { it.copy(blocks = blocks, loading = false, initialParagraph = saved?.paragraph ?: 0, initialOffset = saved?.offset ?: 0) }
                rememberPosition(saved?.paragraph ?: 0, saved?.offset ?: 0)
                val next = state.value.chapters.getOrNull(state.value.chapters.indexOf(chapter) + 1)
                if (next != null && book.source != NovelSource.LOCAL) prefetchJob = backgroundScope.launch {
                    mutable.update { it.copy(readAhead = "正在缓存下一章…") }
                    try {
                        val nextBlocks = content(book, next)
                        cacheMutex.withLock {
                            readAhead.keys.retainAll(setOf(chapter.id, next.id))
                            readAhead[next.id] = nextBlocks
                        }
                        mutable.update { it.copy(readAhead = "本章和下一章已缓存") }
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { mutable.update { it.copy(readAhead = "下一章缓存失败，可重试") } }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(loading = false, error = e.message ?: "正文加载失败") } }
        }
    }
    private suspend fun content(book: NovelBook, chapter: NovelChapter): List<NovelBlock> {
        if (book.source == NovelSource.LOCAL) return document?.contents?.getOrNull(state.value.chapters.indexOf(chapter)) ?: error("本地正文不存在，请重新导入")
        return downloads.store.read(book, chapter) ?: cacheMutex.withLock {
            readAhead[chapter.id] ?: cacheNovelImages(source, book, chapter, imageDirectory, cachedImages).also { readAhead[chapter.id] = it }
        }
    }
    fun resume() { (state.value.chapters.firstOrNull { it.id == memory?.chapterId } ?: state.value.chapters.firstOrNull())?.let(::open) }
    fun closeReader() { chapterJob?.cancel(); prefetchJob?.cancel(); mutable.update { it.copy(chapter = null, loading = false) } }
    fun rememberPosition(paragraph: Int, offset: Int) {
        val chapter = state.value.chapter ?: return
        memory = memory?.copy(chapterId = chapter.id, paragraph = paragraph, offset = offset)
        saveJob?.cancel(); saveJob = backgroundScope.launch { delay(250); persist() }
    }
    private fun positionKey(book: NovelBook) = stringPreferencesKey("novel.position.$id.${book.source}.${book.id}")
    private suspend fun savedPosition(book: NovelBook): NovelMemory? = preferences.data.first()[positionKey(book)]?.let { json.decodeFromString(it) }
    private suspend fun persist() {
        val value = memory ?: return
        preferences.edit { it[key] = json.encodeToString(value); it[positionKey(value.book)] = json.encodeToString(value) }
    }
    fun settings(value: NovelReaderSettings) {
        val safe = value.copy(fontSize = value.fontSize.coerceIn(12, 40), lineHeight = value.lineHeight.coerceIn(1.2f, 2.4f), theme = value.theme.coerceIn(0, 2))
        mutable.update { it.copy(settings = safe) }
        settingsJob?.cancel()
        settingsJob = backgroundScope.launch { preferences.edit { it[settingsKey] = json.encodeToString(safe) } }
    }
    fun reportImportError(message: String) { mutable.update { it.copy(error = message, loading = false) } }
    fun close() {
        backgroundScope.cancel()
        backgroundScope.launch(NonCancellable) {
            persist()
            preferences.edit { it[settingsKey] = json.encodeToString(state.value.settings) }
            cacheMutex.withLock { cachedImages.forEach { it.delete() }; readAhead.clear() }
        }
    }
}

private suspend fun cacheNovelImages(repository: NovelSourceRepository, book: NovelBook, chapter: NovelChapter,
    directory: SystemPath, paths: MutableList<SystemPath>,
): List<NovelBlock> = repository.content(book, chapter).map { block ->
    val image = block.image
    if (image == null) block else {
        val bytes = repository.image(image)
        directory.createDirectories()
        val file = directory.resolve("${Random.nextLong().toULong()}.image")
        file.writeBytes(bytes); paths += file
        block.copy(image = mangaImageFileUri(file.absolutePath))
    }
}
