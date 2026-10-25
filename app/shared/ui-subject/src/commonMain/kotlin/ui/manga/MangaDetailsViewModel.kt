/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.manga

import me.him188.ani.app.data.repository.manga.MangaBangumiNetwork
import me.him188.ani.app.data.repository.manga.MangaLibraryChanges
import me.him188.ani.app.data.repository.manga.MangaLibraryRepository
import me.him188.ani.app.data.repository.manga.MangaLibraryEntry
import me.him188.ani.app.data.repository.manga.MangaCollectionType
import me.him188.ani.app.data.repository.manga.MangaSourceSettings
import me.him188.ani.app.data.repository.manga.MangaSourceSettingsStore
import me.him188.ani.app.domain.mediasource.web.BlockedException
import me.him188.ani.app.domain.mediasource.web.captcha.WebSessionManager
import me.him188.ani.app.domain.mediasource.web.captcha.SolveOutcome
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import io.ktor.http.encodeURLPath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Job
import me.him188.ani.app.domain.media.download.MediaDownloadManager
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.subject.SelfRatingInfo
import me.him188.ani.app.data.repository.manga.MangaRelation
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.ui.rating.EditableRatingActions
import me.him188.ani.app.ui.rating.EditableRatingUiState
import me.him188.ani.app.ui.rating.RateRequest
import me.him188.ani.app.data.persistent.dataStores
import me.him188.ani.app.data.repository.manga.MangaChapter
import me.him188.ani.app.data.repository.manga.MangaCollection
import me.him188.ani.app.data.repository.manga.MangaComment
import me.him188.ani.app.data.repository.manga.MangaDetails
import me.him188.ani.app.data.repository.manga.MangaDetailsRepository
import me.him188.ani.app.data.repository.manga.MangaPage
import me.him188.ani.app.data.repository.manga.MangaReadingMemory
import me.him188.ani.app.data.repository.manga.MangaReadingStore
import me.him188.ani.app.data.repository.manga.MangaSource
import me.him188.ani.app.data.repository.manga.MangaSourceBook
import me.him188.ani.app.data.repository.manga.MangaSourceRepository
import me.him188.ani.app.domain.foundation.HttpClientProvider
import me.him188.ani.app.domain.foundation.ScopedHttpClientUserAgent
import me.him188.ani.app.domain.foundation.get
import me.him188.ani.app.domain.session.SessionManager
import me.him188.ani.app.platform.Context
import me.him188.ani.app.platform.files
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.createDirectories
import me.him188.ani.utils.io.delete
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.io.writeBytes
import kotlin.random.Random
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

data class MangaDetailsUiState(
    val subjectId: Int = 0,
    val usingBangumiCache: Boolean = false,
    val library: MangaLibraryEntry? = null, val libraryLoaded: Boolean = false,
    val libraryWorking: Boolean = false, val libraryError: String? = null,
    val sourceSettings: MangaSourceSettings = MangaSourceSettings(),
    val captchaSupported: Boolean = false,
    internal val offline: List<MangaOfflineChapter> = emptyList(),
    val offlineWorking: Boolean = false, val offlineError: String? = null,
    val rating: EditableRatingUiState = EditableRatingUiState.Placeholder,
    val ratingLoaded: Boolean = false, val ratingError: String? = null,
    val relations: List<MangaRelation> = emptyList(), val relationsLoading: Boolean = true,
    val relationsError: String? = null,
    val details: MangaDetails? = null, val loading: Boolean = true, val error: String? = null,
    val comments: List<MangaComment> = emptyList(), val commentsTotal: Int = 0,
    val commentsLoading: Boolean = false, val commentsError: String? = null,
    val source: MangaSource = MangaSource.KOMIIC, val results: List<MangaSourceBook> = emptyList(),
    val searching: Boolean = false, val sourceError: String? = null,
    val sourceSearches: Map<MangaSource, MangaSourceSearchState> = emptyMap(),
    val autoSelecting: Boolean = false,
    val book: MangaSourceBook? = null, val chapters: List<MangaChapter> = emptyList(), val chaptersLoading: Boolean = false,
    val memory: MangaReadingMemory? = null,
    val chapter: MangaChapter? = null, val pages: List<MangaPage> = emptyList(), val pagesLoading: Boolean = false,
    val pagesError: String? = null, val initialPage: Int = 0,
    internal val downloads: List<MangaDownloadProgress> = emptyList(),
)

class MangaDetailsViewModel(val manga: MangaCollection, context: Context, private val enableReading: Boolean = true) : AbstractViewModel(), KoinComponent, EditableRatingActions {
    private val detailsRepository = MangaDetailsRepository(get<MangaBangumiNetwork>().http)
    private val webSessions = get<WebSessionManager>()
    private val sources = MangaSourceRepository(get<HttpClientProvider>().get(userAgent = ScopedHttpClientUserAgent.NONE), webSessions.cookieJar, get())
    private val sourceSettingsStore = MangaSourceSettingsStore(context.dataStores.preferencesStore)
    private val libraryRepository = MangaLibraryRepository(get<MangaBangumiNetwork>().http)
    private val libraryMutex = Mutex()
    private val commentPaging = MangaCommentPaging(manga.subjectId, detailsRepository, backgroundScope)
    val commentState get() = commentPaging.state
    private val offlineDownloads = MangaOfflineDownloads.get(context.files.dataDir.resolve("manga-offline").resolve(manga.subjectId.toString()),
        get<MediaDownloadManager>().backgroundScope, sources)
    private val offlineStore get() = offlineDownloads.store
    private val readingStore = MangaReadingStore(context.dataStores.preferencesStore)
    private val imageDirectory = context.files.cacheDir.resolve("manga-reader")
    private val cache = MangaReadAheadCache(backgroundScope, sources::pages, ::downloadImage) { uri ->
        imageDirectory.resolve(uri.substringAfterLast('/')).delete()
    }
    private var prefetchJob: Job? = null
    private val mutableState = MutableStateFlow(MangaDetailsUiState(subjectId = manga.subjectId))
    val state = mutableState.asStateFlow()
    private val searchJobs = mutableMapOf<MangaSource, Job>()
    private var autoSourceJob: Job? = null
    private var sourceSelectionStarted = false
    private var chapterJob: Job? = null
    private var pagesJob: Job? = null
    private var detailsJob: Job? = null
    private var commentsJob: Job? = null

    private var ratingJob: Job? = null
    private val ratingEditorRequested = MutableStateFlow(false)
    private var relationsJob: Job? = null

    init {
        backgroundScope.launch {
            get<MangaBangumiNetwork>().cache.usingCache.collect { cached ->
                mutableState.update { it.copy(usingBangumiCache = cached) }
            }
        }
        backgroundScope.launch {
            offlineDownloads.state.collect { download -> mutableState.update {
                it.copy(offline = download.entries, offlineWorking = download.working, offlineError = download.error)
            } }
        }
        if (manga.subjectId > 0) {
            loadLibrary()
            loadSelfRating()
            loadRelations()
            loadDetails()
            loadComments()
        }
        if (enableReading) backgroundScope.launch {
            val settings = sourceSettingsStore.read()
            mutableState.update { it.copy(sourceSettings = settings, captchaSupported = webSessions.isInteractiveSupported) }
            launch {
                sourceSettingsStore.flow.collect { updated ->
                    if (state.value.sourceSettings.enabled != updated.enabled) {
                        cancelAutoSelection()
                        searchJobs.filterKeys { it !in updated.enabled }.values.forEach { it.cancel() }
                    }
                    mutableState.update { it.copy(sourceSettings = updated,
                        sourceSearches = it.sourceSearches.filterKeys { source -> source in updated.enabled }) }
                }
            }
            val memory = readingStore.read(manga.subjectId)
            if (sourceSelectionStarted) return@launch
            mutableState.update { it.copy(memory = memory) }
            if (memory?.sourcePinned == true) selectBook(memory.book)
            else autoSelectSource()
        }
    }

    fun loadLibrary() {
        backgroundScope.launch {
            try {
                val entry = token()?.let { libraryRepository.read(manga.subjectId, it) }
                mutableState.update { it.copy(library = entry, libraryLoaded = true, libraryError = null) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutableState.update { it.copy(libraryError = e.message) } }
        }
    }

    fun dismissLibraryError() { mutableState.update { it.copy(libraryError = null) } }
    fun setCollection(type: MangaCollectionType) = libraryWrite {
        libraryRepository.setType(manga.subjectId, token() ?: error("请先登录 Bangumi"), type)
        mutableState.update { it.copy(library = (it.library ?: MangaLibraryEntry(type, 0, 0)).copy(type = type), libraryLoaded = true,
            rating = it.rating.copy(enableEdit = true)) }
        ratingJob?.cancel(); ratingJob = null; loadSelfRating()
    }
    fun setProgress(chapters: Int, volumes: Int) = libraryWrite {
        check(state.value.library != null) { "请先收藏这本书" }
        libraryRepository.setProgress(manga.subjectId, token() ?: error("请先登录 Bangumi"), chapters, volumes)
        mutableState.update { it.copy(library = it.library?.copy(chapters = chapters, volumes = volumes)) }
    }
    private fun libraryWrite(block: suspend () -> Unit) {
        if (state.value.libraryWorking) return
        mutableState.update { it.copy(libraryWorking = true, libraryError = null) }
        backgroundScope.launch {
            try { libraryMutex.withLock { block() } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutableState.update { it.copy(libraryError = e.message ?: "提交失败，请重试") } }
            finally { mutableState.update { it.copy(libraryWorking = false) } }
        }
    }
    fun markChapterRead(chapter: MangaChapter) {
        val entry = state.value.library ?: return
        val number = chapter.number?.takeIf { it >= 1 && it % 1.0 == 0.0 }?.toInt() ?: return
        if (chapter.group == "单行本" && number > entry.volumes) setProgress(entry.chapters, number)
        else if (chapter.group == "章节" && number > entry.chapters) setProgress(number, entry.volumes)
    }
    fun saveSourceSettings(settings: MangaSourceSettings) {
        cancelAutoSelection()
        mutableState.update { it.copy(sourceSettings = settings) }
        backgroundScope.launch { sourceSettingsStore.savePreferences(settings) }
    }
    fun solveCaptcha(source: MangaSource) {
        val request = state.value.sourceSearches[source]?.captcha ?: return
        backgroundScope.launch {
            val previous = state.value.sourceSearches[source] ?: return@launch
            updateSource(source, previous.copy(resolvingCaptcha = true))
            try {
                if (webSessions.solve(request, interactive = true) is SolveOutcome.Solved) {
                    updateSource(source, previous.copy(captcha = null, resolvingCaptcha = false, error = null))
                    if (previous.retryBook != null) selectBook(previous.retryBook) else search(source, previous.keyword)
                }
                else updateSource(source, previous.copy(error = "验证未完成，可重试或切换来源"))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { updateSource(source, previous.copy(error = e.message)) }
        }
    }
    fun cacheChapters(chapters: List<MangaChapter>, book: MangaSourceBook? = state.value.book) {
        if (book != null) offlineDownloads.download(book, chapters)
    }
    fun cancelOffline() { offlineDownloads.pause() }
    fun deleteOffline(entry: MangaOfflineChapter) { offlineDownloads.delete(entry) }
    fun openOffline(entry: MangaOfflineChapter) {
        sourceSelectionStarted = true
        cancelAutoSelection(); chapterJob?.cancel(); pagesJob?.cancel()
        val memory = state.value.memory?.takeIf { it.book == entry.book } ?: MangaReadingMemory(entry.book)
        mutableState.update { it.copy(book = entry.book, memory = memory, source = entry.book.source,
            chapters = it.offline.filter { item -> item.book == entry.book }.map { item -> item.chapter }) }
        openChapter(entry.chapter)
    }

    fun defaultKeyword(): String = when (manga.subjectId) {
        118165 -> "碧蓝之海"
        355555 -> "花薰凛然"
        else -> manga.title
    }

    fun loadDetails() {
        detailsJob?.cancel()
        detailsJob = backgroundScope.launch {
            mutableState.update { it.copy(loading = true, error = null) }
            try {
                val details = detailsRepository.details(manga.subjectId, null)
                mutableState.update { it.copy(details = details, loading = false, rating = it.rating.copy(ratingInfo = details.rating)) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutableState.update { it.copy(loading = false, error = e.message ?: "作品信息加载失败") } }
        }
    }

    private suspend fun token(): String? = get<SessionManager>().getBangumiAccessToken()

    fun loadSelfRating(openEditor: Boolean = false) {
        if (openEditor) ratingEditorRequested.value = true
        if (ratingJob?.isActive == true) return
        ratingJob = backgroundScope.launch {
            try {
                val accessToken = token() ?: error("请先登录 Bangumi")
                val self = detailsRepository.selfRating(manga.subjectId, accessToken)
                mutableState.update { it.copy(ratingLoaded = true, ratingError = null, rating = it.rating.copy(
                    selfRatingInfo = self ?: SelfRatingInfo.Empty, enableEdit = self != null,
                    showRatingDialog = ratingEditorRequested.value && self != null,
                    showRatingRequiresCollectionDialog = ratingEditorRequested.value && self == null)) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (ratingEditorRequested.value) mutableState.update { it.copy(ratingError = e.message ?: "评价加载失败") } }
            finally { ratingEditorRequested.value = false }
        }
    }

    override fun requestEditRating() {
        if (!state.value.ratingLoaded) { loadSelfRating(openEditor = true); return }
        mutableState.update { it.copy(rating = it.rating.copy(showRatingDialog = it.rating.enableEdit,
            showRatingRequiresCollectionDialog = !it.rating.enableEdit)) }
    }
    override fun cancelEditRating() { mutableState.update { it.copy(rating = it.rating.copy(showRatingDialog = false)) } }
    override fun dismissRatingRequiresCollectionDialog() {
        mutableState.update { it.copy(rating = it.rating.copy(showRatingRequiresCollectionDialog = false)) }
    }
    fun dismissRatingError() { mutableState.update { it.copy(ratingError = null) } }
    override fun submitRating(request: RateRequest) {
        if (state.value.rating.isUpdating) return
        backgroundScope.launch { saveRating(request) }
    }
    override suspend fun updateScore(score: Int): LoadError? {
        val self = state.value.rating.selfRatingInfo
        return saveRating(RateRequest(score, self.comment.orEmpty(), self.isPrivate))
    }
    private suspend fun saveRating(request: RateRequest): LoadError? {
        mutableState.update { it.copy(ratingError = null, rating = it.rating.copy(isUpdating = true)) }
        return try {
            check(state.value.rating.enableEdit) { "请先收藏这本书" }
            val self = state.value.rating.selfRatingInfo.copy(score = request.score, comment = request.comment, isPrivate = request.isPrivate)
            detailsRepository.rate(manga.subjectId, token() ?: error("请先登录 Bangumi"), self)
            MangaLibraryChanges.notifyChanged()
            mutableState.update { it.copy(rating = it.rating.copy(selfRatingInfo = self, showRatingDialog = false)) }
            commentsJob?.cancel(); commentsJob = null
            mutableState.update { it.copy(comments = emptyList(), commentsTotal = 0) }
            loadComments(); commentPaging.refresh(); loadDetails()
            null
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            mutableState.update { it.copy(ratingError = e.message ?: "评价提交失败，请重试") }
            LoadError.fromException(e)
        } finally { mutableState.update { it.copy(rating = it.rating.copy(isUpdating = false)) } }
    }

    suspend fun resolveRelation(relation: MangaRelation): MangaRelation = detailsRepository.resolveRelation(relation)

    fun loadRelations() {
        relationsJob?.cancel()
        relationsJob = backgroundScope.launch {
            mutableState.update { it.copy(relationsLoading = true, relationsError = null) }
            try {
                val relations = detailsRepository.relations(manga.subjectId, null)
                mutableState.update { it.copy(relations = relations, relationsLoading = false) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutableState.update { it.copy(relationsLoading = false, relationsError = e.message ?: "关联作品加载失败") } }
        }
    }

    fun loadComments() { commentPaging.refresh() }

    fun search(source: MangaSource, keyword: String) {
        sourceSelectionStarted = true
        cancelAutoSelection()
        if (source !in state.value.sourceSettings.enabled) {
            mutableState.update { it.copy(sourceError = "该来源已停用或订阅已移除，请在设置的数据源中启用或添加") }; return
        }
        val link = sources.bookFromUrl(keyword)
        val target = link?.source ?: source
        if (target !in state.value.sourceSettings.enabled) {
            mutableState.update { it.copy(sourceError = "链接对应来源已停用，请先启用") }; return
        }
        searchJobs[target]?.cancel()
        mutableState.update { it.copy(source = target, autoSelecting = false, sourceError = null) }
        updateSource(target, MangaSourceSearchState(keyword, loading = true))
        searchJobs[target] = backgroundScope.launch {
            try {
                val result = link?.let(::listOf) ?: sources.search(target, keyword)
                updateSource(target, MangaSourceSearchState(keyword, result))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { updateSource(target, MangaSourceSearchState(keyword, error = e.message ?: "搜索失败", captcha = (e as? BlockedException)?.request)) }
        }
    }

    private fun recordCaptcha(book: MangaSourceBook, failure: Exception) {
        if (failure !is BlockedException) return
        val previous = state.value.sourceSearches[book.source] ?: MangaSourceSearchState(defaultKeyword())
        updateSource(book.source, previous.copy(captcha = failure.request, error = failure.message, retryBook = book))
    }

    private fun cancelAutoSelection() {
        autoSourceJob?.cancel()
        mutableState.update { current ->
            if (!current.autoSelecting) current else current.copy(autoSelecting = false,
                sourceSearches = current.sourceSearches.mapValues { (_, value) -> value.copy(loading = false) })
        }
    }

    private fun updateSource(source: MangaSource, result: MangaSourceSearchState) {
        mutableState.update { current ->
            current.copy(sourceSearches = current.sourceSearches + (source to result),
                searching = if (current.source == source) result.loading else current.searching,
                results = if (current.source == source) result.books else current.results)
        }
    }

    fun autoSelectSource() {
        sourceSelectionStarted = true
        autoSourceJob?.cancel()
        chapterJob?.cancel()
        prefetchJob?.cancel()
        pagesJob?.cancel()
        searchJobs.values.forEach { it.cancel() }
        val previousChapter = state.value.chapter
        mutableState.update { it.copy(autoSelecting = true, chaptersLoading = false, searching = false,
            sourceError = null, sourceSearches = emptyMap(), chapter = null, pages = emptyList()) }
        autoSourceJob = backgroundScope.launch {
            val aliases = listOf(defaultKeyword(), manga.title, manga.originalTitle).filter { it.isNotBlank() }.distinct()
            // Explicitly choosing automatic mode releases a pinned source even if all sources fail.
            state.value.memory?.let { old ->
                val memory = old.copy(sourcePinned = false)
                readingStore.save(manga.subjectId, memory)
                mutableState.update { it.copy(memory = memory) }
            }
            val found = findReadableMangaSource(aliases, sources::search, sources::chapters,
                probe = { book, chapter -> cache.image(cache.pages(book, chapter).first()) }, progress = ::updateSource, sources = state.value.sourceSettings.enabled)
            if (found != null) {
                val memory = state.value.memory?.takeIf { it.book.source == found.book.source && it.book.id == found.book.id }
                    ?.copy(sourcePinned = false) ?: MangaReadingMemory(found.book, sourcePinned = false)
                readingStore.save(manga.subjectId, memory)
                mutableState.update { it.copy(book = found.book, source = found.book.source, chapters = found.chapters,
                    chaptersLoading = false, autoSelecting = false, searching = false, memory = memory, sourceError = null) }
                matchingMangaChapter(previousChapter, found.chapters)?.let(::openChapter)
            } else mutableState.update { it.copy(autoSelecting = false, searching = false,
                sourceError = "未找到可下载的匹配来源，请手动搜索别名或选择作品。") }
        }
    }

    fun selectBook(book: MangaSourceBook) {
        if (book.source !in state.value.sourceSettings.enabled) {
            mutableState.update { it.copy(sourceError = "已记住的来源已停用，请启用或选择其他来源") }; return
        }
        sourceSelectionStarted = true
        cancelAutoSelection()
        prefetchJob?.cancel()
        pagesJob?.cancel()
        chapterJob?.cancel()
        val previousChapter = state.value.chapter
        chapterJob = backgroundScope.launch {
            val memory = state.value.memory?.takeIf { it.book.source == book.source && it.book.id == book.id }
                ?.copy(sourcePinned = true) ?: MangaReadingMemory(book)
            mutableState.update { it.copy(book = book, source = book.source, chapters = emptyList(), chaptersLoading = true,
                sourceError = null, autoSelecting = false, memory = memory, chapter = null, pages = emptyList()) }
            try {
                // Persist the user's choice before network access, including when the source is temporarily down.
                readingStore.save(manga.subjectId, memory)
                val offline = offlineStore.list().filter { it.book.source == book.source && it.book.id == book.id && it.complete }
                if (offline.isNotEmpty()) mutableState.update { it.copy(chapters = offline.map { entry -> entry.chapter }) }
                val chapters = try { sources.chapters(book) } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { if (offline.isEmpty()) throw e else offline.map { it.chapter } }
                mutableState.update { it.copy(chapters = chapters, chaptersLoading = false) }
                if (previousChapter != null) {
                    val matching = matchingMangaChapter(previousChapter, chapters)
                    if (matching != null) openChapter(matching)
                    else mutableState.update { it.copy(chapter = null, pages = emptyList(), sourceError = "来源已切换，请选择对应章节") }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                recordCaptcha(book, e)
                mutableState.update { it.copy(chaptersLoading = false, sourceError = e.message ?: "章节加载失败") }
            }
        }
    }

    fun openChapter(chapter: MangaChapter) {
        cancelAutoSelection()
        val book = state.value.book ?: return
        prefetchJob?.cancel()
        pagesJob?.cancel()
        pagesJob = backgroundScope.launch {
            val memory = state.value.memory
            mutableState.update { it.copy(chapter = chapter, pages = emptyList(), pagesLoading = true, pagesError = null, downloads = emptyList(),
                initialPage = if (memory?.chapterId == chapter.id) memory.page else 0) }
            try {
                val pages = offlineStore.pages(book, chapter) ?: cache.pages(book, chapter)
                mutableState.update { it.copy(pages = pages, pagesLoading = false, initialPage = it.initialPage.coerceIn(pages.indices)) }
                if (pages.none { it.url.startsWith("file:") }) prefetchChapters()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                recordCaptcha(book, e)
                mutableState.update { it.copy(pagesLoading = false, pagesError = e.message ?: "图片列表加载失败") }
            }
        }
    }

    fun closeReader() {
        pagesJob?.cancel()
        mutableState.update { it.copy(chapter = null, pages = emptyList(), pagesLoading = false, pagesError = null) }
    }

    fun close() {
        backgroundScope.cancel()
        backgroundScope.launch(NonCancellable) { cache.clear() }
    }

    fun rememberPage(chapterId: String, page: Int) {
        val snapshot = state.value
        if (snapshot.chapter?.id != chapterId || snapshot.pages.isEmpty()) return
        val memory = (snapshot.memory ?: MangaReadingMemory(snapshot.book ?: return))
            .copy(chapterId = chapterId, page = page.coerceIn(snapshot.pages.indices))
        mutableState.update { it.copy(memory = memory) }
        backgroundScope.launch { readingStore.save(manga.subjectId, memory) }

    }

    fun rememberReaderLayout(rightToLeft: Boolean, firstPageSingle: Boolean) {
        val snapshot = state.value
        val memory = (snapshot.memory ?: MangaReadingMemory(snapshot.book ?: return))
            .copy(rightToLeft = rightToLeft, firstPageSingle = firstPageSingle)
        mutableState.update { it.copy(memory = memory) }
        backgroundScope.launch { readingStore.save(manga.subjectId, memory) }
    }

    fun prefetchChapters() {
        val snapshot = state.value
        val book = snapshot.book ?: return
        val chapter = snapshot.chapter ?: return
        val siblings = snapshot.chapters.filter { it.group == chapter.group }
        val index = siblings.indexOfFirst { it.id == chapter.id }
        val chapters = listOfNotNull(chapter, siblings.getOrNull(index + 1))
        prefetchJob?.cancel()
        mutableState.update { it.copy(downloads = chapters.map { item -> MangaDownloadProgress(item.title) }) }
        prefetchJob = backgroundScope.launch {
            cache.prefetch(book, chapters) { position, progress ->
                mutableState.update { current ->
                    current.copy(downloads = current.downloads.mapIndexed { i, value -> if (i == position) progress else value })
                }
            }
        }
    }

    fun retryDownloads() {
        val snapshot = state.value
        val book = snapshot.book ?: return
        val chapter = snapshot.chapter ?: return
        prefetchJob?.cancel()
        pagesJob?.cancel()
        pagesJob = backgroundScope.launch {
            cache.invalidateCatalogs(book)
            openChapter(chapter)
        }
    }

    suspend fun image(page: MangaPage): String = if (page.url.startsWith("file:")) page.url else cache.image(page)

    private suspend fun downloadImage(page: MangaPage): String {
        val bytes = sources.image(page)
        imageDirectory.createDirectories()
        val file = imageDirectory.resolve("${manga.subjectId}-${Random.nextLong().toULong()}.image")
        file.writeBytes(bytes)
        return mangaImageFileUri(file.absolutePath)
    }

}

internal fun mangaImageFileUri(absolutePath: String): String {
    val normalized = absolutePath.replace('\\', '/')
    return "file://" + (if (normalized.startsWith('/')) normalized else "/$normalized").encodeURLPath()
}
