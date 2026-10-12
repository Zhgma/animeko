/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.manga

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.Cookie
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import me.him188.ani.app.domain.mediasource.web.captcha.WebSourceCookieJar
import me.him188.ani.utils.ktor.asScopedHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class KomiicRelayTest {
    @Test fun searchChaptersPagesAndImagesUseRelayWithoutOriginCookiesOrArbitraryGraphql() = runTest {
        val operations = mutableListOf<String>()
        val imageUrl = "$KOMIIC_API_BASE_URL/images/payload/signature"
        val cookies = WebSourceCookieJar().apply {
            addCookie(Url("https://komiic.com/"), Cookie("origin-session", "private", path = "/", secure = true))
            addCookie(Url(KOMIIC_API_BASE_URL), Cookie("another-service", "private", path = "/", secure = true))
        }
        val client = HttpClient(MockEngine { request ->
            assertEquals("119.28.149.19", request.url.host)
            assertNull(request.headers["Cookie"])
            assertNull(request.headers["Authorization"])
            if (request.url.toString() == imageUrl) {
                respond(byteArrayOf(-1, -40, -1, -39), HttpStatusCode.OK, headersOf("Content-Type", "image/jpeg"))
            } else {
                assertEquals("$KOMIIC_API_BASE_URL/query", request.url.toString())
                val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                assertNull(body["query"])
                val op = body.text("operation"); operations += op
                respond(when (op) {
                    "Search" -> """{"data":{"searchComicsAndAuthors":{"comics":[{"id":"115","title":"Test","imageUrl":"$imageUrl"}]}}}"""
                    "Chapters" -> """{"data":{"chaptersByComicId":[{"id":"1490","serial":"1","type":"chapter","size":1}]}}"""
                    else -> {
                        assertEquals("115", body.obj("variables").text("comicId"))
                        assertEquals("1490", body.obj("variables").text("chapterId"))
                        """{"data":{"imagesByChapterId":[{"kid":"page","url":"$imageUrl","width":704,"height":1008}]}}"""
                    }
                })
            }
        })
        try {
            val repo = MangaSourceRepository(client.asScopedHttpClient(), cookies)
            val book = repo.search(MangaSource.KOMIIC, "Test").single()
            assertEquals(imageUrl, book.cover)
            val page = repo.pages(book, repo.chapters(book).single()).single()
            assertEquals(704, page.width)
            assertEquals(4, repo.image(page).size)
            assertEquals(listOf("Search", "Chapters", "Images"), operations)
            assertEquals("$KOMIIC_API_BASE_URL/icon", MangaSource.KOMIIC.iconUrl)
        } finally { client.close() }
    }

    @Test fun missingSignedImageDoesNotFallBackToUnreachableOrigin() = runTest {
        val client = HttpClient(MockEngine { respond("""{"data":{"imagesByChapterId":[{"kid":"page","width":1,"height":1}]}}""") })
        try {
            assertFailsWith<IllegalStateException> {
                MangaSourceRepository(client.asScopedHttpClient()).pages(MangaSourceBook(MangaSource.KOMIIC, "115", "Test"), MangaChapter("1490", "1", 1.0, "章节"))
            }
        } finally { client.close() }
    }
}
