/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.manga

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import me.him188.ani.utils.ktor.asScopedHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MangaSourceRepositoryTest {
    @Test
    fun separatesVolumesAndOrdersDecimalChapters() = runTest {
        val client = HttpClient(MockEngine { request ->
            assertEquals("komiic.com", request.url.host)
            assertNull(request.headers["Authorization"])
            respond("""{"data":{"chaptersByComicId":[
                {"id":"3","serial":"10","type":"chapter","size":20},
                {"id":"1","serial":"2","type":"chapter","size":22},
                {"id":"4","serial":"1","type":"book","size":150},
                {"id":"2","serial":"2.5","type":"chapter","size":8}
            ]}}""")
        })
        try {
            val chapters = MangaSourceRepository(client.asScopedHttpClient()).chapters(MangaSourceBook(MangaSource.KOMIIC, "1", "Test"))
            assertEquals(listOf("1", "2", "3", "4"), chapters.map { it.id })
            assertEquals("单行本", chapters.last().group)
            assertEquals(22, chapters.first().pageCount)
        } finally { client.close() }
    }

    @Test
    fun sourceErrorsAndCancellationPropagate() = runTest {
        val errorClient = HttpClient(MockEngine { respond("""{"errors":[{"message":"reading limit reached"}]}""") })
        val cancelClient = HttpClient(MockEngine { throw CancellationException("test") })
        try {
            val error = assertFailsWith<IllegalStateException> { MangaSourceRepository(errorClient.asScopedHttpClient()).search(MangaSource.KOMIIC, "test") }
            assertTrue(error.message.orEmpty().contains("reading limit"))
            assertFailsWith<CancellationException> { MangaSourceRepository(cancelClient.asScopedHttpClient()).search(MangaSource.KOMIIC, "test") }
        } finally { errorClient.close(); cancelClient.close() }
    }

    @Test
    fun chapterLinksDeduplicateAndDoNotMixBooks() {
        val html = """<a href="/comic/12/101.html">开始阅读</a>
            <a href="/comic/12/102.html">第10话</a><a href="/comic/12/101.html">第2话<span>20p</span></a>
            <a href="/comic/12/101.html">第2话</a><a href="/comic/99/103.html">第1话</a>
            <a href="/comic/12/104.html">第1卷</a>"""
        val chapters = MangaSourceRepository.parseChapters("12", html)
        assertEquals(listOf("101", "102", "104"), chapters.map { it.id })
        assertEquals("单行本", chapters.last().group)
    }

    @Test
    fun pastedLinksAcceptOnlySupportedBookUrls() {
        val client = HttpClient(MockEngine { error("No request expected") })
        try {
            val repo = MangaSourceRepository(client.asScopedHttpClient())
            assertEquals("115", repo.bookFromUrl("https://komiic.com/comic/115")?.id)
            assertEquals(MangaSource.MANHUAGUI, repo.bookFromUrl("https://www.manhuagui.com/comic/12887/")?.source)
            assertNull(repo.bookFromUrl("https://komiic.com.evil.test/comic/115"))
            assertNull(repo.bookFromUrl("http://localhost/comic/115"))
        } finally { client.close() }
    }

    @Test
    fun lzStreamHasBoundedFailureForMalformedData() {
        assertEquals("Hello world!", ManhuaguiDecoder.decompress("BIUwNmD2AEDukCcwBMCEQ==="))
        assertFailsWith<IllegalArgumentException> { ManhuaguiDecoder.decompress("A") }
    }

    @Test
    fun packedChapterIsDecodedAsDataWithoutExecutingScript() {
        val html = """<script>unrelated()</script><script>function(p,a,c,k,e,d){}('viewer({"files":["page.webp"],"path":"/test/","sl":{"e":123,"m":"token"}}).start();',62,1,'BIUwNmD2AEDukCcwBMCEQ==='['splic']('|'),0,{})</script>"""
        val data = ManhuaguiDecoder.chapter(html)
        assertEquals("/test/", data.text("path"))
        assertEquals("token", data.obj("sl").text("m"))
        assertEquals(1, data.array("files").size)
    }
}
