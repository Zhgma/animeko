/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.manga

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import me.him188.ani.utils.ktor.asScopedHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BooksApiRoutingTest {
    @Test
    fun publicBookSearchAndDetailsUseBooksServiceWithoutAccountCredentials() = runTest {
        val urls = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            urls += request.url.toString()
            assertNull(request.headers["Authorization"])
            respond("{}")
        })
        try {
            val http = MangaBangumiHttp(client.asScopedHttpClient(), booksApiBaseUrl = BOOKS_API_BASE_URL)
            http.get("https://api.bgm.tv/v0/subjects/118165", "private-token")
            http.search("""{"keyword":"book","filter":{"type":[1]}}""", "private-token", 20, 10)
            assertEquals("$BOOKS_API_BASE_URL/v0/subjects/118165", urls[0])
            assertEquals("$BOOKS_API_BASE_URL/v0/search/subjects?offset=20&limit=10", urls[1])
        } finally { client.close() }
    }

    @Test
    fun relationsCommentsAndRatingWritesKeepTheirOriginalRoutesAndCredentials() = runTest {
        val urls = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            urls += request.url.toString()
            assertEquals("Bearer private-token", request.headers["Authorization"])
            respond("{}")
        })
        try {
            val http = MangaBangumiHttp(client.asScopedHttpClient(), booksApiBaseUrl = BOOKS_API_BASE_URL)
            val originals = listOf(
                "https://api.bgm.tv/v0/subjects/118165/subjects",
                "https://next.bgm.tv/p1/subjects/118165/comments",
            )
            originals.forEach { http.get(it, "private-token") }
            val collection = "https://api.bgm.tv/v0/users/-/collections/118165"
            http.write(HttpMethod.Patch, collection, "private-token", """{"rate":8}""")
            assertEquals(originals + collection, urls)
        } finally { client.close() }
    }

    @Test
    fun collectionReadsAndStateProgressWritesUseBooksServiceWithCurrentUserToken() = runTest {
        val urls = mutableListOf<String>()
        val methods = mutableListOf<HttpMethod>()
        val client = HttpClient(MockEngine { request ->
            urls += request.url.toString()
            methods += request.method
            assertEquals("Bearer current-user-token", request.headers["Authorization"])
            respond("{}")
        })
        try {
            val http = MangaBangumiHttp(client.asScopedHttpClient(), booksApiBaseUrl = BOOKS_API_BASE_URL)
            http.get("https://api.bgm.tv/v0/me", "current-user-token")
            http.get("https://api.bgm.tv/v0/users/alice/collections", "current-user-token", mapOf("subject_type" to 1, "type" to 3))
            http.get("https://api.bgm.tv/v0/users/alice/collections/118165", "current-user-token")
            val url = "https://api.bgm.tv/v0/users/-/collections/118165"
            http.write(HttpMethod.Post, url, "current-user-token", """{"type":3}""")
            http.write(HttpMethod.Patch, url, "current-user-token", """{"ep_status":12,"vol_status":2}""")
            assertEquals(listOf(
                "$BOOKS_API_BASE_URL/v0/me",
                "$BOOKS_API_BASE_URL/v0/users/alice/collections?subject_type=1&type=3",
                "$BOOKS_API_BASE_URL/v0/users/alice/collections/118165",
                "$BOOKS_API_BASE_URL/v0/users/-/collections/118165",
                "$BOOKS_API_BASE_URL/v0/users/-/collections/118165",
            ), urls)
            assertEquals(listOf(HttpMethod.Get, HttpMethod.Get, HttpMethod.Get, HttpMethod.Post, HttpMethod.Patch), methods)
        } finally { client.close() }
    }

    @Test
    fun transportWithoutBooksConfigurationPreservesBangumiRequests() = runTest {
        val original = "https://api.bgm.tv/v0/subjects/118165"
        val client = HttpClient(MockEngine { request ->
            assertEquals(original, request.url.toString())
            assertEquals("Bearer private-token", request.headers["Authorization"])
            respond("{}")
        })
        try { MangaBangumiHttp(client.asScopedHttpClient()).get(original, "private-token") }
        finally { client.close() }
    }
}
