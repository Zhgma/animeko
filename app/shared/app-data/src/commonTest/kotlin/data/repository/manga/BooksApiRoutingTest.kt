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
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import me.him188.ani.app.data.models.subject.SelfRatingInfo

class BooksApiRoutingTest {
    @Test
    fun unsupportedBookRequestsCannotSilentlyBypassTheBooksService() {
        for ((method, url, body) in listOf(
            Triple("GET", "https://api.bgm.tv/v0/episodes", null),
            Triple("GET", "https://api.bgm.tv/v0/subjects/1/subjects?url=https://example.com", null),
            Triple("PATCH", "https://api.bgm.tv/v0/users/-/collections/1", """{"tags":["x"]}"""),
        )) assertFailsWith<IllegalArgumentException> { MangaRequestRouting.resolve(method, url, body) }
    }

    @Test
    fun everyDetailsRepositoryOperationUsesBooksServiceForSeriesAndIndividualVolumes() = runTest {
        val seen = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            assertTrue(request.url.toString().startsWith("$BOOKS_API_BASE_URL/"))
            val path = request.url.encodedPath.removePrefix("/api/books")
            seen += path
            val account = path == "/v0/me" || "/collections/" in path
            assertEquals(if (account) "Bearer current-token" else null, request.headers["Authorization"])
            respond(when {
                request.method != HttpMethod.Get -> "{}"
                path == "/v0/me" -> """{"username":"alice"}"""
                "/collections/" in path -> """{"type":3,"ep_status":2,"vol_status":1,"rate":8}"""
                path.endsWith("/comments") -> """{"total":0,"data":[]}"""
                path.endsWith("/subjects") -> "[]"
                else -> """{"id":${path.substringAfterLast('/')},"type":1,"name":"book"}"""
            })
        })
        try {
            val http = MangaBangumiHttp(client.asScopedHttpClient(), booksApiBaseUrl = BOOKS_API_BASE_URL)
            val details = MangaDetailsRepository(http)
            val library = MangaLibraryRepository(http)
            for (id in listOf(118165, 118167, 294562, 701000, 311121, 366942, 559875)) {
                details.details(id, "current-token")
                details.comments(id, 0)
                details.relations(id, "current-token")
                details.selfRating(id, "current-token")
                details.rate(id, "current-token", SelfRatingInfo(8, "review", emptyList(), false))
                library.read(id, "current-token")
                library.setType(id, "current-token", MangaCollectionType.entries.first { it.bangumiId == 3 })
                library.setProgress(id, "current-token", 2, 1)
                assertTrue("/v0/subjects/$id/subjects" in seen)
            }
            assertEquals(70, seen.size)
        } finally { client.close() }
    }

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
    fun seriesAndVolumeRelationsUsePublicServiceAndRatingWritesKeepOnlyCurrentUserToken() = runTest {
        val urls = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            urls += request.url.toString()
            if (request.method == HttpMethod.Get) assertNull(request.headers["Authorization"])
            else assertEquals("Bearer private-token", request.headers["Authorization"])
            respond("{}")
        })
        try {
            val http = MangaBangumiHttp(client.asScopedHttpClient(), booksApiBaseUrl = BOOKS_API_BASE_URL)
            for (id in listOf(118165, 117149)) {
                http.get("https://api.bgm.tv/v0/subjects/$id", "private-token")
                http.get("https://api.bgm.tv/v0/subjects/$id/subjects", "private-token")
                http.write(HttpMethod.Patch, "https://api.bgm.tv/v0/users/-/collections/$id", "private-token",
                    """{"rate":8,"comment":"test","private":false}""")
            }
            assertEquals(listOf(118165, 117149).flatMap { id -> listOf(
                "$BOOKS_API_BASE_URL/v0/subjects/$id",
                "$BOOKS_API_BASE_URL/v0/subjects/$id/subjects",
                "$BOOKS_API_BASE_URL/v0/users/-/collections/$id",
            ) }, urls)
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
    fun commentsAndNetworkProbeUseBooksServiceWithoutCredentials() = runTest {
        val urls = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            urls += request.url.toString()
            assertNull(request.headers["Authorization"])
            respond("{}")
        })
        try {
            val http = MangaBangumiHttp(client.asScopedHttpClient(), booksApiBaseUrl = BOOKS_API_BASE_URL)
            http.get("https://next.bgm.tv/p1/subjects/311122/comments", "private-token", mapOf("limit" to 20, "offset" to 20))
            http.probe()
            assertEquals(listOf(
                "$BOOKS_API_BASE_URL/p1/subjects/311122/comments?limit=20&offset=20",
                "$BOOKS_API_BASE_URL/v0/subjects/118165",
                "$BOOKS_API_BASE_URL/p1/subjects/118165/comments?limit=1",
            ), urls)
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
