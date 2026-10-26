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
import me.him188.ani.app.data.repository.player.ReadingHistoryStore
import me.him188.ani.app.data.repository.player.ReadingHistoryEntry
import kotlin.time.Clock
import me.him188.ani.app.domain.foundation.HttpClientProvider
import me.him188.ani.app.domain.foundation.get
import me.him188.ani.app.domain.media.download.MediaDownloadManager
import me.him188.ani.app.domain.mediasource.web.captcha.CaptchaBrowserFactory
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
    val sourceSettings: NovelSourceSettings = NovelSourceSettings(),
    val sourceSearches: Map<NovelSource, NovelSearchState> = emptyMap(),
    val autoSelecting: Boolean = false, val sourcePinned: Boolean = false,
)

internal class NovelViewModel(private val id: Int, context: Context, private val title: String = "", private var coverUrl: String = "") : AbstractViewModel(), KoinComponent {
    private val sourceSettingsStore = get<NovelSourceSettingsStore>()
    private val zlibrary = ZlibraryApi(get<HttpClientProvider>().get(), ZlibrarySessionStore(context.dataStores.preferencesStore))
    val zlibraryLogin = ZlibraryLoginController(zlibrary, get<CaptchaBrowserFactory>())
    private val zlibraryBooks = ZlibraryBookCache(zlibrary, context.files.dataDir.resolve("zlibrary-books")) {
        sourceSettingsStore.read().definition(NovelSource.ZLIBRARY)?.home ?: error("Z-Library 来源已停用")
    }
    private val source = NovelSourceRepository(get<HttpClientProvider>().get(), sourceSettingsStore, zlibrary, zlibraryBooks)
    private val preferences = context.dataStores.preferencesStore
    private val historyStore = ReadingHistoryStore(preferences)
    private var latestHistory: ReadingHistoryEntry? = null
    private val key = stringPreferencesKey("novel.reader.$id")
    private val settingsKey = stringPreferencesKey("novel.reader.settings")
    private val filesKey = novelLocalFilesKey(id)
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
    private val searchJobs = mutableMapOf<NovelSource, Job>()
    private var autoJob: Job? = null
    private var prefetchJob: Job? = null
    private var saveJob: Job? = null
    private var settingsJob: Job? = null
    private var generation = 0
    private var autoSequence = 0
    private var restored = false
    private var aliases = listOf(title)

    init {
        selectionJob = backgroundScope.launch {
            try {
                val saved = preferences.data.first()
                memory = saved[key]?.let { json.decodeFromString<NovelMemory>(it) }
                coverUrl = coverUrl.ifBlank { memory?.coverUrl.orEmpty() }
                memory = memory?.copy(coverUrl = coverUrl)
                val settings = saved[settingsKey]?.let { json.decodeFromString<NovelReaderSettings>(it) } ?: NovelReaderSettings()
                val localBooks = saved[filesKey]?.let { json.decodeFromString<List<NovelBook>>(it) }.orEmpty()
                val sourceSettings = sourceSettingsStore.read()
                mutable.update { it.copy(settings = settings, localBooks = localBooks, sourceSettings = sourceSettings, sourcePinned = memory?.sourcePinned == true) }
                memory?.let { m ->
                    if (m.book.source == NovelSource.LOCAL) document = json.decodeFromString(directory.resolve("${m.book.id}.json").readText())
                    mutable.update { it.copy(book = m.book, chapters = m.chapters) }
                }
                restored = true
                if (id > 0 && memory == null) autoSelect()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(error = "阅读记录加载失败：${e.message}") } }
        }
    }

    init {
        backgroundScope.launch {
            sourceSettingsStore.flow.collect { settings ->
                val previous = state.value.sourceSettings
                if (previous.enabled != settings.enabled || previous.definitions != settings.definitions) {
                    autoJob?.cancel(); autoSequence++
                    searchJobs.values.forEach { it.cancel() }; searchJobs.clear()
                    mutable.update { it.copy(sourceSearches = emptyMap(), autoSelecting = false) }
                }
                mutable.update { it.copy(sourceSettings = settings) }
            }
        }
    }

    fun loginZlibrary() { backgroundScope.launch {
        try {
            val home = sourceSettingsStore.read().definition(NovelSource.ZLIBRARY)?.home ?: error("Z-Library 来源已停用")
            zlibraryLogin.login(ZlibraryApi.origin(home))
            if (title.isNotBlank()) search(NovelSource.ZLIBRARY, title)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { mutable.update { it.copy(error = e.message) } }
    } }

    fun saveSourceSettings(settings: NovelSourceSettings) { backgroundScope.launch { sourceSettingsStore.savePreferences(settings) } }

    fun search(provider: NovelSource, keyword: String) {
        search(provider, keyword, true)
    }

    fun loadSourceSearches() {
        if (state.value.autoSelecting || title.isBlank()) return
        state.value.sourceSettings.enabled.forEach { provider ->
            val result = state.value.sourceSearches[provider]
            if (result == null || result.loading && searchJobs[provider]?.isActive != true) search(provider, title, false)
        }
    }

    private fun search(provider: NovelSource, keyword: String, promptLogin: Boolean) {
        if (provider !in state.value.sourceSettings.enabled || keyword.isBlank()) return
        searchJobs.remove(provider)?.cancel()
        fun update(value: NovelSearchState) { mutable.update { it.copy(sourceSearches = it.sourceSearches + (provider to value)) } }
        update(NovelSearchState(keyword, loading = true))
        searchJobs[provider] = backgroundScope.launch {
            try {
                if (promptLogin) update(NovelSearchState(keyword, zlibraryLogin.retryWithLogin { source.search(provider, keyword) }))
                else update(searchNovelSource(provider, aliases, source::search))
            }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { update(NovelSearchState(keyword, error = e.message ?: "搜索失败")) }
        }
    }

    fun updateAliases(values: List<String>) {
        val updated = (listOf(title) + values).filter { it.isNotBlank() }.distinct()
        if (updated == aliases) return
        aliases = updated
        if (restored && memory == null && !state.value.sourcePinned) autoSelect()
    }

    fun autoSelect() {
        if (title.isBlank()) return
        autoJob?.cancel(); autoSequence++; selectionJob?.cancel(); chapterJob?.cancel(); prefetchJob?.cancel(); generation++
        searchJobs.values.forEach { it.cancel() }; searchJobs.clear()
        val selectionGeneration = autoSequence
        mutable.update { it.copy(autoSelecting = true, sourcePinned = false, loading = false, error = null) }
        autoJob = backgroundScope.launch {
            try {
                memory = memory?.copy(sourcePinned = false); persist()
                val result = findReadableNovelSource(aliases, state.value.sourceSettings.enabled,
                    source::search, source::chapters, { book, chapter -> source.content(book, chapter) },
                    { provider, result -> mutable.update { it.copy(sourceSearches = it.sourceSearches + (provider to result)) } })
                currentCoroutineContext().ensureActive()
                if (autoSequence != selectionGeneration) return@launch
                if (result != null && result.book.source in state.value.sourceSettings.enabled) {
                    bind(result.book, result.chapters, false)
                } else mutable.update { it.copy(error = "自动匹配未找到可读来源，请手动查找") }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(error = e.message) } }
            finally { if (autoSequence == selectionGeneration) mutable.update { it.copy(autoSelecting = false, loading = false) } }
        }
    }

    private suspend fun bind(book: NovelBook, chapters: List<NovelChapter>, pinned: Boolean) {
        saveJob?.cancel(); document = null; cacheMutex.withLock { readAhead.clear() }
        memory = (savedPosition(book)?.copy(chapters = chapters) ?: NovelMemory(book, chapters)).copy(sourcePinned = pinned)
        persist()
        mutable.update { it.copy(book = book, chapters = chapters, chapter = null, blocks = emptyList(), loading = false, sourcePinned = pinned) }
    }

    fun select(book: NovelBook) {
        if (book.source !in state.value.sourceSettings.enabled) return
        autoJob?.cancel(); autoSequence++; selectionJob?.cancel(); chapterJob?.cancel(); prefetchJob?.cancel(); generation++
        mutable.update { it.copy(loading = true, error = null, autoSelecting = false, sourcePinned = true) }
        selectionJob = backgroundScope.launch {
            try {
                // Persist the explicit binding before networking, so a failed request never silently chooses another source.
                memory = (savedPosition(book) ?: NovelMemory(book, emptyList())).copy(sourcePinned = true)
                persist()
                mutable.update { it.copy(book = book, chapters = memory!!.chapters, chapter = null, blocks = emptyList()) }
                bind(book, zlibraryLogin.retryWithLogin { source.chapters(book) }, true)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(loading = false, error = e.message ?: "目录加载失败") } }
        }
    }

    suspend fun import(name: String, bytes: ByteArray) {
        autoJob?.cancel(); autoSequence++; selectionJob?.cancel(); chapterJob?.cancel(); prefetchJob?.cancel(); generation++
        val importingGeneration = generation
        val created = mutableListOf<SystemPath>()
        var committed = false
        mutable.update { it.copy(loading = true, error = null, autoSelecting = false, sourcePinned = true) }
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
        autoJob?.cancel(); autoSequence++
        selectionJob?.cancel(); chapterJob?.cancel(); prefetchJob?.cancel(); generation++
        selectionJob = backgroundScope.launch {
            try {
                require(book.source == NovelSource.LOCAL && book in state.value.localBooks)
                val parsed = json.decodeFromString<NovelDocument>(directory.resolve("${book.id}.json").readText())
                saveJob?.cancel(); document = parsed
                cacheMutex.withLock { readAhead.clear() }
                memory = savedPosition(book)?.copy(chapters = parsed.chapters) ?: NovelMemory(book, parsed.chapters); persist()
                mutable.update { it.copy(book = book, chapters = parsed.chapters, chapter = null, blocks = emptyList(), loading = false, error = null, autoSelecting = false, sourcePinned = true) }
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
                val blocks = zlibraryLogin.retryWithLogin { content(book, chapter) }
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
        if (state.value.blocks.isEmpty()) return
        memory = memory?.copy(chapterId = chapter.id, paragraph = paragraph.coerceAtLeast(0), offset = offset.coerceAtLeast(0), coverUrl = coverUrl)
        val value = memory ?: return
        latestHistory = ReadingHistoryEntry(id, if (id == 0) value.book.title else title.ifBlank { value.book.title },
            coverUrl, chapter.title, Clock.System.now().toEpochMilliseconds(), novel = value)
        val history = latestHistory!!
        saveJob?.cancel(); saveJob = backgroundScope.launch { delay(250); persist(); historyStore.record(history) }
    }
    fun updateCover(value: String) {
        if (value.isBlank() || value == coverUrl) return
        coverUrl = value
        memory = memory?.copy(coverUrl = value)
        latestHistory = latestHistory?.copy(coverUrl = value, novel = latestHistory?.novel?.copy(coverUrl = value))
        backgroundScope.launch {
            persist()
            historyStore.fillMissingCover(id, value)
        }
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
            latestHistory?.let { historyStore.record(it) }
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
        val bytes = repository.image(book, image)
        directory.createDirectories()
        val file = directory.resolve("${Random.nextLong().toULong()}.image")
        file.writeBytes(bytes); paths += file
        block.copy(image = mangaImageFileUri(file.absolutePath))
    }
}
