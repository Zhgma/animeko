/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.manga

import androidx.paging.PagingSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import me.him188.ani.app.domain.search.SearchSort
import me.him188.ani.app.domain.search.SubjectSearchQuery
import me.him188.ani.app.domain.search.SubjectType
import me.him188.ani.app.domain.search.withSearchType
import me.him188.ani.utils.ktor.asScopedHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class BangumiBookSearchRepositoryTest {
    @Test
    fun bookSearchUsesBangumiTypeOneAndPreservesPagination() = runTest {
        val client = HttpClient(MockEngine { request ->
            assertEquals("api.bgm.tv", request.url.host)
            assertEquals("/v0/search/subjects", request.url.encodedPath)
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("Bearer test-token", request.headers["Authorization"])
            assertEquals("20", request.url.parameters["limit"])
            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertEquals("碧蓝之海", body.text("keyword"))
            assertEquals("heat", body.text("sort"))
            assertEquals("[1]", body.obj("filter")["type"].toString())
            assertEquals("false", body.obj("filter")["nsfw"].toString())
            respond("""{"total":3,"data":[
                {"id":118165,"type":1,"name":"ぐらんぶる","name_cn":"碧蓝之海","platform":"漫画","images":{"large":"cover"},"rating":{"score":8.2,"total":100,"rank":20}},
                {"id":2,"type":2,"name":"Unexpected anime"}]}""")
        })
        try {
            val page = BangumiBookSearchRepository(client.asScopedHttpClient()).searchPage(
                SubjectSearchQuery(" 碧蓝之海 ", type = SubjectType.BOOK, sort = SearchSort.COLLECTION, nsfw = false), "test-token", 0, 60)
            assertEquals(2, page.nextOffset)
            assertEquals(118165, page.items.single().id)
            assertEquals("ぐらんぶる", page.items.single().originalTitle)
            assertEquals("8.2", page.items.single().rating.score)
        } finally { client.close() }
    }

    @Test
    fun novelsAreIncludedAndAnEmptyPageStopsLoading() = runTest {
        val client = HttpClient(MockEngine { request ->
            assertNull(request.headers["Authorization"])
            if (request.url.parameters["offset"] == "0") respond("""{"total":20,"data":[{"id":3,"type":1,"name":"Novel","platform":"小说"}]}""")
            else respond("""{"total":20,"data":[]}""")
        })
        try {
            val repository = BangumiBookSearchRepository(client.asScopedHttpClient())
            val query = SubjectSearchQuery("Novel", type = SubjectType.BOOK)
            assertEquals("小说", repository.searchPage(query, null, 0, 20).items.single().platform)
            assertNull(repository.searchPage(query, null, 1, 20).nextOffset)
        } finally { client.close() }
    }

    @Test
    fun failuresRemainRetryablePagingErrors() = runTest {
        val client = HttpClient(MockEngine { respond("unavailable", HttpStatusCode.ServiceUnavailable) })
        try {
            val source = BangumiBookSearchRepository(client.asScopedHttpClient()).pagingSource(SubjectSearchQuery("test")) { null }
            assertIs<PagingSource.LoadResult.Error<Int, BangumiBookSearchItem>>(
                source.load(PagingSource.LoadParams.Refresh(0, 20, false)))
        } finally { client.close() }
    }

    @Test
    fun switchingSearchesCanCancelInFlightRequests() = runTest {
        val client = HttpClient(MockEngine { throw CancellationException("new query") })
        try {
            val source = BangumiBookSearchRepository(client.asScopedHttpClient()).pagingSource(SubjectSearchQuery("test")) { null }
            assertFailsWith<CancellationException> { source.load(PagingSource.LoadParams.Refresh(0, 20, false)) }
        } finally { client.close() }
    }

    @Test
    fun typeSwitchKeepsKeywordAndClearsAnimeFilters() {
        val original = SubjectSearchQuery("花朵", tags = listOf("TV"), year = 2025, sort = SearchSort.DATE)
        assertEquals(SubjectType.ANIME, original.type)
        val books = original.withSearchType(SubjectType.BOOK)
        assertEquals("花朵", books.keywords)
        assertNull(books.tags)
        assertNull(books.year)
        assertEquals(SearchSort.MATCH, books.sort)
        assertEquals(SubjectType.ANIME, books.withSearchType(SubjectType.ANIME).type)
    }
}
