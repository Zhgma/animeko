/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.manga

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.test.runTest
import me.him188.ani.utils.ktor.asScopedHttpClient
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class VerifiedMangaSourcesTest {
    @Test fun cccUsesAnonymousHeadersAndRejectsPaidReading() = runTest {
        val client = HttpClient(MockEngine { req ->
            assertEquals("web_desktop", req.headers["device"])
            assertEquals(null, req.headers["Authorization"])
            respond(when (req.url.encodedPath) {
                "/book" -> """{"code":0,"data":{"data":[{"id":276,"name":"测试作品","image1":"https://image.test/cover.jpg"}]}}"""
                "/book/276/chapter" -> """{"code":0,"data":{"chapters":[{"id":2,"name":"第一话","unit":"话","sequence":1}]}}"""
                "/book/chapter/2" -> """{"code":0,"data":{"chapter":{"sales_plan":0,"proportion":[{"id":9,"width":1200,"height":1600}]}}}"""
                else -> """{"code":0,"data":{"chapter":{"sales_plan":1,"is_free":0,"proportion":[]}}}"""
            })
        })
        try {
            val repo = MangaSourceRepository(client.asScopedHttpClient())
            val book = repo.search(MangaSource.CCC, "作品").single()
            val chapter = repo.chapters(book).single()
            val page = repo.pages(book, chapter).single()
            assertEquals(9, page.cccImageId)
            assertEquals("https://storage.googleapis.com/ccc-www/fs/chapter_content/encrypt/9/2", page.url)
            assertFailsWith<IllegalStateException> { repo.pages(book, chapter.copy(id="3")) }
        } finally { client.close() }
    }

    @Test fun comickLoadsAllCatalogPagesAndReaderPagesWithoutCrossSiteNavigation() = runTest {
        val paths = mutableListOf<String>()
        val adapter = VerifiedMangaSources { url ->
            paths += url
            when {
                "/search?" in url -> """<script id="sv-data">{"data":[{"slug":"look-back","title":"Look Back"}]}</script>"""
                "chapter-list?page=1" in url -> """{"data":[{"hid":"a","chap":"1","lang":"en"}],"pagination":{"last_page":2}}"""
                "chapter-list?page=2" in url -> """{"data":[{"hid":"b","chap":"2","lang":"en"}],"pagination":{"last_page":2}}"""
                url.endsWith("?page=2") -> """<script id="sv-data">{"chapter":{"images":[{"url":"https://images.test/2.webp"}]}}</script>"""
                else -> """<script id="sv-data">{"chapter":{"images":[{"url":"https://images.test/1.webp"}]}}</script><a id="next-chapter" href="?page=2">下一页</a>"""
            }
        }
        val book = adapter.search(MangaSource.COMICK, "Look Back").single()
        val chapters = adapter.chapters(book)
        assertEquals(listOf(1.0, 2.0), chapters.map { it.number })
        assertEquals(listOf("https://images.test/1.webp", "https://images.test/2.webp"), adapter.pages(book, chapters.first()).map { it.url })
        val bad = VerifiedMangaSources { """<script id="sv-data">{"chapter":{"images":[{"url":"https://images.test/1.webp"}]}}</script><a id="next-chapter" href="https://other.test/read">下一页</a>""" }
        assertFailsWith<IllegalArgumentException> { bad.pages(book, chapters.first()) }
    }

    @Test fun cccImageEnvelopeUsesStandardPaddedAesAndBase64() {
        val key = "4HEBBu0/VT297L1X4v0tyU4lAz1Jb/UdnDO1sZ6crMiJUNzzuCVgaGcKEbnO6aGZFR73o3+5jq7pjGhWlwQtkTwrMJXalbLkqKroxoFiw30gp6kx3uBxiWlxVKPNGe7veQB09kIoJJ0UM04GB5B0BA=="
        assertContentEquals(byteArrayOf(1,2,3,4), decodeCccImage(key, Base64.decode("neXhTJALaqx0NXQYLD8GsHqsvrEVinL1P1XixtKQYl8=")))
        assertFailsWith<IllegalArgumentException> { decodeCccImage("not base64!", byteArrayOf(1)) }
    }

    @Test fun maintainedSubscriptionExcludesBaoziButCustomSubscriptionsRemainSupported() {
        assertFalse(MangaSource.BAOZI in MangaSubscription.defaults().single().sources)
        assertEquals(listOf(MangaSource.BAOZI), MangaSubscriptionParser.parse("""[{"key":"baozi"}]""").sources)
        assertEquals(listOf(MangaSource.CCC, MangaSource.COMICK), MangaSubscriptionParser.parse("""[{"key":"ccc"},{"key":"comick"}]""").sources)
    }
}
