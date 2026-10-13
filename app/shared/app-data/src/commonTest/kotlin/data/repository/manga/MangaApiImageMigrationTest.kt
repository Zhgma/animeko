/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.manga

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.Cookie
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import me.him188.ani.app.domain.mediasource.web.captcha.WebSourceCookieJar
import me.him188.ani.utils.ktor.asScopedHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MangaApiImageMigrationTest {
    private val old = "https://119.28.149.19:8443"
    private val current = "https://43.131.255.74:8443"
    private fun preferences() = object : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(data.value).also { data.value = it }
    }

    @Test fun signedPathsMigrateButOtherOriginsAndPathsStayUntouched() {
        for (kind in listOf("books", "komiic")) {
            val path = "/api/$kind/images/payload/signature"
            assertEquals(current + path, migrateMangaApiImageUrl(old + path))
        }
        for (url in listOf("$old/api/books/health", "$old/api/books/images-evil/a", "$old.evil/api/books/images/a",
            "https://elsewhere.example/api/books/images/a", "$current/api/komiic/images/a/b")) {
            assertEquals(url, migrateMangaApiImageUrl(url))
        }
    }

    @Test fun imageFieldsMigrateWithoutEditingCommentText() {
        val image = "$old/api/books/images/payload/signature"
        val result = Json.parseToJsonElement(migrateMangaApiImages(
            """{"images":{"large":"$image"},"user":{"avatar":{"medium":"$image"}},"comment":"$image","rate":8}""",
        )).jsonObject
        assertEquals("$current/api/books/images/payload/signature", result.obj("images").text("large"))
        assertEquals("$current/api/books/images/payload/signature", result.obj("user").obj("avatar").text("medium"))
        assertEquals(image, result.text("comment"))
        assertEquals(8, result.number("rate"))
    }

    @Test fun cachedBookResponsesMigrateEvenWhenOffline() = runTest {
        val cache = MangaBangumiCache(preferences())
        val request = "GET https://api.bgm.tv/v0/subjects/118167 [] "
        cache.read(null, request) { MangaBangumiResponse(200,
            """{"images":{"large":"$old/api/books/images/payload/signature"}}""") }
        val client = HttpClient(MockEngine { error("Offline") })
        try {
            val response = MangaBangumiHttp(client.asScopedHttpClient(), cache, BOOKS_API_BASE_URL)
                .get("https://api.bgm.tv/v0/subjects/118167")
            assertEquals("$current/api/books/images/payload/signature",
                Json.parseToJsonElement(response.body).jsonObject.obj("images").text("large"))
        } finally { client.close() }
    }

    @Test fun oldKomiicPageLinksAndSavedCoversUseNewHostWithoutCookies() = runTest {
        val path = "/api/komiic/images/payload/signature"
        val cookies = WebSourceCookieJar().apply {
            addCookie(Url(current), Cookie("another-service", "secret", path = "/", secure = true))
        }
        val client = HttpClient(MockEngine { request ->
            assertEquals("43.131.255.74", request.url.host)
            assertNull(request.headers["Cookie"])
            assertNull(request.headers["Authorization"])
            if (request.url.encodedPath.endsWith("/query")) {
                respond("""{"data":{"imagesByChapterId":[{"url":"$old$path","width":10,"height":20}]}}""")
            } else {
                assertEquals(path, request.url.encodedPath)
                respond(byteArrayOf(-1, -40, -1, -39), HttpStatusCode.OK, headersOf("Content-Type", "image/jpeg"))
            }
        })
        try {
            val book = MangaSourceBook(MangaSource.KOMIIC, "115", "Book", old + path)
            val repo = MangaSourceRepository(client.asScopedHttpClient(), cookies)
            assertEquals(current + path, repo.pages(book, MangaChapter("1490", "1", 1.0, "章节")).single().url)
            assertEquals(4, repo.image(MangaPage(old + path, old)).size)
            val data = preferences()
            val memory = MangaReadingMemory(book, chapterId = "1490", page = 12)
            MangaReadingStore(data).save(118165, memory)
            assertEquals(memory.copy(book = book.copy(cover = current + path)), MangaReadingStore(data).read(118165))
        } finally { client.close() }
    }
}
