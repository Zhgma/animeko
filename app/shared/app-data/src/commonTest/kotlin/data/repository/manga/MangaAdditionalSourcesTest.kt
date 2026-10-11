/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.manga

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import me.him188.ani.utils.ktor.asScopedHttpClient
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MangaAdditionalSourcesTest {
    @Test
    fun manhuarenDecodesOnlyImageLiterals() {
        val html = """eval(function(p,a,c,k,e,d){}('0 1=["2://3/4.5"];',6,6,'var|newImgs|https|images.test|page|jpg'.split('|'),0,{}))"""
        assertEquals(listOf("https://images.test/page.jpg"), AdditionalMangaSources.unpackManhuarenImages(html))
        assertFailsWith<IllegalStateException> { AdditionalMangaSources.unpackManhuarenImages("<script>loadPaidChapter()</script>") }
    }

    @Test
    fun retriesServerErrorsButDoesNotRetryQuotasOrAcceptChallengeHtml() = runTest {
        var attempts = 0
        val client = HttpClient(MockEngine {
            attempts++
            if (attempts == 1) respond("unavailable", HttpStatusCode.ServiceUnavailable)
            else respond(byteArrayOf(1, 2, 3), headers = headersOf("Content-Type", "image/webp;charset=UTF-8"))
        })
        try {
            assertEquals(3, MangaSourceRepository(client.asScopedHttpClient()).image(MangaPage("https://image.test/a", "https://source.test/")).size)
            assertEquals(2, attempts)
        } finally { client.close() }
        for ((status, body) in listOf(HttpStatusCode.TooManyRequests to "quota", HttpStatusCode.OK to "<html>challenge</html>")) {
            attempts = 0
            val blocked = HttpClient(MockEngine { attempts++; respond(body, status) })
            try {
                assertFailsWith<IllegalStateException> { MangaSourceRepository(blocked.asScopedHttpClient()).image(MangaPage("https://image.test/a", "")) }
                assertEquals(1, attempts)
            } finally { blocked.close() }
        }
    }

    @Test
    fun ykmhUsesPublishedImageHostAndIgnoresNonChapterLinks() = runTest {
        val adapter = AdditionalMangaSources { url -> when {
            url.endsWith("config.js") -> """resHost: [{"domain":["https://img.test"]}]"""
            url.endsWith("7.html") -> """var chapterImages = ["/1.jpg","/2.jpg"];"""
            else -> """<a href="/manhua/test/7.html">第7话</a><a href="/manhua/test/7.html">第7话</a><a href="/ad.html">广告</a>"""
        } }
        val book = MangaSourceBook(MangaSource.YKMH, "test", "Test")
        val chapter = adapter.chapters(book).single()
        assertEquals("7", chapter.id)
        assertEquals(listOf("https://img.test/1.jpg", "https://img.test/2.jpg"), adapter.pages(book, chapter).map { it.url })
        assertNull(adapter.bookFromUrl("https://mangadex.org/title/abc-def/a-title"))
    }

    @Test
    fun baoziUsesChapterIdsAndOnlyReaderImages() = runTest {
        val adapter = AdditionalMangaSources { url ->
            if (url.contains("baozimhapp")) """<div class="comic-contain"><div class="chapter-img"><img class="comic-contain__item" data-src="https://images.test/2.webp"></div></div><img src="https://ads.test/ad.jpg">"""
            else """<div id="chapter-items"><div class="comics-chapters"><a href="/user/page_direct?comic_id=test&amp;chapter_id=7"><span>第2话</span></a></div></div>"""
        }
        val book = MangaSourceBook(MangaSource.BAOZI, "test", "Test")
        val chapter = adapter.chapters(book).single()
        assertEquals("7", chapter.id)
        assertEquals("https://images.test/2.webp", adapter.pages(book, chapter).single().url)
        assertNull(adapter.bookFromUrl("https://godamh.com.evil.test/manga/test"))
        assertNull(adapter.bookFromUrl("https://mangadex.org/title/abc-def/a-title"))
    }

    @Test
    fun godaDecodesBoundedPayloadAndKeepsImageOrder() {
        val original = """[{"order":1,"url":"/one.webp"},{"order":2,"url":"/two.webp"}]"""
        val standard = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        val custom = "_-9876543210abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val encoded = Base64.UrlSafe.encode(original.encodeToByteArray()).trimEnd('=')
            .map { custom[standard.indexOf(it)] }.joinToString("")
            .chunked(7).mapIndexed { i, s -> if (i % 2 == 1) s.reversed() else s }.joinToString("")
        val a = encoded.length / 3
        val b = (encoded.length - a) / 2
        val payload = "J7r${encoded.substring(a, a + b)}kD${encoded.substring(a + b)}W4s${encoded.substring(0, a)}nQ"
        assertEquals(Json.parseToJsonElement(original), AdditionalMangaSources.decodeGodaImages(payload))
        assertFailsWith<IllegalArgumentException> { AdditionalMangaSources.decodeGodaImages("J7rbroken-nQ") }
    }
}
