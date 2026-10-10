/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.manga

import io.ktor.http.encodeURLPath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
import me.him188.ani.app.data.repository.user.AccessTokenSession
import me.him188.ani.app.domain.foundation.HttpClientProvider
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
    val rating: EditableRatingUiState = EditableRatingUiState.Placeholder,
    val ratingLoaded: Boolean = false, val ratingError: String? = null,
    val relations: List<MangaRelation> = emptyList(), val relationsLoading: Boolean = true,
    val relationsError: String? = null,
    val details: MangaDetails? = null, val loading: Boolean = true, val error: String? = null,
    val comments: List<MangaComment> = emptyList(), val commentsTotal: Int = 0,
    val commentsLoading: Boolean = false, val commentsError: String? = null,
    val source: MangaSource = MangaSource.KOMIIC, val results: List<MangaSourceBook> = emptyList(),
    val searching: Boolean = false, val sourceError: String? = null,
    val book: MangaSourceBook? = null, val chapters: List<MangaChapter> = emptyList(), val chaptersLoading: Boolean = false,
    val memory: MangaReadingMemory? = null,
    val chapter: MangaChapter? = null, val pages: List<MangaPage> = emptyList(), val pagesLoading: Boolean = false,
    val pagesError: String? = null, val initialPage: Int = 0,
    internal val downloads: List<MangaDownloadProgress> = emptyList(),
)

class MangaDetailsViewModel(val manga: MangaCollection, context: Context) : AbstractViewModel(), KoinComponent, EditableRatingActions {
    private val client = get<HttpClientProvider>().get()
    private val detailsRepository = MangaDetailsRepository(client)
    private val sources = MangaSourceRepository(client)
    private val readingStore = MangaReadingStore(context.dataStores.preferencesStore)
    private val imageDirectory = context.files.cacheDir.resolve("manga-reader")
    private val cache = MangaReadAheadCache(backgroundScope, sources::pages, ::downloadImage) { uri ->
        imageDirectory.resolve(uri.substringAfterLast('/')).delete()
    }
    private var prefetchJob: Job? = null
    private val mutableState = MutableStateFlow(MangaDetailsUiState(subjectId = manga.subjectId))
    val state = mutableState.asStateFlow()
    private var searchJob: Job? = null
    private var chapterJob: Job? = null
    private var pagesJob: Job? = null
    private var detailsJob: Job? = null
    private var commentsJob: Job? = null

    private var ratingJob: Job? = null
    private val ratingEditorRequested = MutableStateFlow(false)
    private var relationsJob: Job? = null

    init {
        loadSelfRating()
        loadRelations()
        loadDetails()
        loadComments()
        backgroundScope.launch {
            val memory = readingStore.read(manga.subjectId)
            mutableState.update { it.copy(memory = memory) }
            if (memory != null) selectBook(memory.book)
            else search(MangaSource.KOMIIC, defaultKeyword())
        }
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
                val session = get<SessionManager>().sessionFlow.first()
                val details = detailsRepository.details(manga.subjectId, (session as? AccessTokenSession)?.tokens?.bangumiAccessToken)
                mutableState.update { it.copy(details = details, loading = false, rating = it.rating.copy(ratingInfo = details.rating)) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutableState.update { it.copy(loading = false, error = e.message ?: "作品信息加载失败") } }
        }
    }

    private suspend fun token(): String? = (get<SessionManager>().sessionFlow.first() as? AccessTokenSession)?.tokens?.bangumiAccessToken

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
            check(state.value.rating.enableEdit) { "请先收藏这部漫画" }
            val self = state.value.rating.selfRatingInfo.copy(score = request.score, comment = request.comment, isPrivate = request.isPrivate)
            detailsRepository.rate(manga.subjectId, token() ?: error("请先登录 Bangumi"), self)
            mutableState.update { it.copy(rating = it.rating.copy(selfRatingInfo = self, showRatingDialog = false)) }
            commentsJob?.cancel(); commentsJob = null
            mutableState.update { it.copy(comments = emptyList(), commentsTotal = 0) }
            loadComments(); loadDetails()
            null
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            mutableState.update { it.copy(ratingError = e.message ?: "评价提交失败，请重试") }
            LoadError.fromException(e)
        } finally { mutableState.update { it.copy(rating = it.rating.copy(isUpdating = false)) } }
    }

    fun loadRelations() {
        relationsJob?.cancel()
        relationsJob = backgroundScope.launch {
            mutableState.update { it.copy(relationsLoading = true, relationsError = null) }
            try {
                val relations = detailsRepository.relations(manga.subjectId, token())
                mutableState.update { it.copy(relations = relations, relationsLoading = false) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutableState.update { it.copy(relationsLoading = false, relationsError = e.message ?: "关联作品加载失败") } }
        }
    }

    fun loadComments() {
        if (commentsJob?.isActive == true) return
        commentsJob = backgroundScope.launch {
            mutableState.update { it.copy(commentsLoading = true, commentsError = null) }
            try {
                val page = detailsRepository.comments(manga.subjectId, state.value.comments.size)
                mutableState.update { it.copy(comments = (it.comments + page.items).distinctBy(MangaComment::id),
                    commentsTotal = if (page.items.isEmpty()) it.comments.size else page.total, commentsLoading = false) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutableState.update { it.copy(commentsLoading = false, commentsError = e.message ?: "评论加载失败") } }
        }
    }

    fun search(source: MangaSource, keyword: String) {
        searchJob?.cancel()
        searchJob = backgroundScope.launch {
            mutableState.update { it.copy(source = source, searching = true, sourceError = null, results = emptyList()) }
            try {
                val link = sources.bookFromUrl(keyword)
                val result = link?.let(::listOf) ?: sources.search(source, keyword)
                mutableState.update { it.copy(searching = false, results = result) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutableState.update { it.copy(searching = false, sourceError = e.message ?: "搜索失败") } }
        }
    }

    fun selectBook(book: MangaSourceBook) {
        prefetchJob?.cancel()
        pagesJob?.cancel()
        chapterJob?.cancel()
        chapterJob = backgroundScope.launch {
            mutableState.update { it.copy(book = book, source = book.source, chapters = emptyList(), chaptersLoading = true, sourceError = null) }
            try {
                val chapters = sources.chapters(book)
                val memory = state.value.memory?.takeIf { it.book.source == book.source && it.book.id == book.id }
                    ?: MangaReadingMemory(book)
                readingStore.save(manga.subjectId, memory)
                mutableState.update { it.copy(chapters = chapters, chaptersLoading = false, memory = memory) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutableState.update { it.copy(chaptersLoading = false, sourceError = e.message ?: "章节加载失败") } }
        }
    }

    fun openChapter(chapter: MangaChapter) {
        val book = state.value.book ?: return
        prefetchJob?.cancel()
        pagesJob?.cancel()
        pagesJob = backgroundScope.launch {
            val memory = state.value.memory
            mutableState.update { it.copy(chapter = chapter, pages = emptyList(), pagesLoading = true, pagesError = null, downloads = emptyList(),
                initialPage = if (memory?.chapterId == chapter.id) memory.page else 0) }
            try {
                val pages = cache.pages(book, chapter)
                mutableState.update { it.copy(pages = pages, pagesLoading = false, initialPage = it.initialPage.coerceIn(pages.indices)) }
                prefetchChapters()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutableState.update { it.copy(pagesLoading = false, pagesError = e.message ?: "图片列表加载失败") } }
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

    suspend fun image(page: MangaPage): String = cache.image(page)

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
