/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.manga

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import me.him188.ani.app.data.models.subject.RatingCounts
import me.him188.ani.app.data.models.subject.RatingInfo
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.domain.search.SearchSort
import me.him188.ani.app.domain.search.SubjectSearchQuery
import me.him188.ani.utils.ktor.ScopedHttpClient

data class BangumiBookSearchItem(
    val id: Int, val title: String, val originalTitle: String, val cover: String,
    val platform: String, val date: String, val tags: List<String>, val rating: RatingInfo, val nsfw: Boolean,
)

internal data class BangumiBookSearchPage(val items: List<BangumiBookSearchItem>, val nextOffset: Int?)

/** Bangumi book search includes manga, novels and individual volumes, without touching anime caches. */
class BangumiBookSearchRepository(private val http: MangaBangumiHttp) {
    constructor(client: ScopedHttpClient) : this(MangaBangumiHttp(client))
    suspend fun titleSuggestions(query: SubjectSearchQuery, token: String?): List<String> =
        searchPage(query, token, 0, 10).items.map { it.title }.distinct()

    fun search(query: SubjectSearchQuery, token: suspend () -> String?): Flow<PagingData<BangumiBookSearchItem>> =
        Pager(PagingConfig(pageSize = BOOK_SEARCH_PAGE_SIZE, initialLoadSize = BOOK_SEARCH_PAGE_SIZE), initialKey = 0) { pagingSource(query, token) }.flow

    internal fun pagingSource(query: SubjectSearchQuery, token: suspend () -> String?) =
        object : PagingSource<Int, BangumiBookSearchItem>() {
            override fun getRefreshKey(state: PagingState<Int, BangumiBookSearchItem>): Int? = null
            override suspend fun load(params: LoadParams<Int>): LoadResult<Int, BangumiBookSearchItem> = try {
                val page = searchPage(query, token(), params.key ?: 0, params.loadSize)
                LoadResult.Page(page.items, prevKey = null, nextKey = page.nextOffset)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { LoadResult.Error(RepositoryException.wrapOrThrowCancellation(e)) }
        }

    internal suspend fun searchPage(query: SubjectSearchQuery, token: String?, offset: Int, limit: Int): BangumiBookSearchPage {
        val body = bookSearchBody(query.keywords, query.sort, query.nsfw)
        val root = Json.parseToJsonElement(http.search(body, token, offset, limit.coerceIn(1, BOOK_SEARCH_PAGE_SIZE))).jsonObject
        val data = root.array("data")
        val items = data.map { it.jsonObject }.filter { it.number("type") == 1 }.map { book ->
            val rating = book.obj("rating")
            BangumiBookSearchItem(book.number("id"), book.text("name_cn").ifBlank { book.text("name") },
                book.text("name"), book.obj("images").text("large").ifBlank { book.obj("images").text("common") },
                book.text("platform"), book.text("date"), book.array("tags").take(3).map { it.jsonObject.text("name") },
                RatingInfo(rating.number("rank"), rating.number("total"), RatingCounts.Zero, rating.text("score").ifBlank { "0" }),
                (book["nsfw"] as? JsonPrimitive)?.booleanOrNull ?: false)
        }
        // Advance by the raw response size so unexpected non-book results cannot truncate pagination.
        val next = offset + data.size
        return BangumiBookSearchPage(items, next.takeIf { data.isNotEmpty() && it < root.number("total") })
    }
}
