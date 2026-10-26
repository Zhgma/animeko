/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.novel

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import com.fleeksoft.ksoup.Ksoup
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.headersOf
import me.him188.ani.utils.ktor.asScopedHttpClient
import kotlin.test.*

class NovelSourceRepositoryTest {
    @Test fun sourceResultsAreBoundedWithExactTitleFirst() {
        val books = (1..40).map { NovelBook(NovelSource.QUANBEN, "$it", "三体同人$it") } +
            NovelBook(NovelSource.QUANBEN, "exact", "三体")
        val result = limitNovelSearchResults(books + books, "三体")
        assertEquals(10, result.size)
        assertEquals("exact", result.first().id)
        assertEquals(result.size, result.distinctBy { it.id }.size)
    }
    @Test fun illustrationTransportSendsSourceRefererWithoutAccountCredentials() = runTest {
        val client = HttpClient(MockEngine { request ->
            assertEquals("picture.example.org", request.url.host)
            assertEquals("https://www.huanmengacg.com/", request.headers["Referer"])
            assertEquals("Mozilla/5.0 Animecomic", request.headers["User-Agent"])
            assertNull(request.headers["Authorization"])
            assertNull(request.headers["Cookie"])
            respond(byteArrayOf(1, 2, 3), headers = headersOf("Content-Type", "image/jpeg"))
        })
        try {
            val bytes = NovelSourceRepository(client.asScopedHttpClient()).image(
                NovelBook(NovelSource.HUANMENG, "8655", "无职转生"), "https://picture.example.org/plate.jpg")
            assertContentEquals(byteArrayOf(1, 2, 3), bytes)
        } finally { client.close() }
    }

    @Test fun relativeLazyAndSvgIllustrationsKeepTheirPosition() {
        val base = "https://example.org/books/14/10.html"
        val html = """<p>之前</p><img src="../images/a.jpg"><p>之间</p>
            <img src="data:image/gif;base64,AA" data-src="//cdn.example.org/b.jpg">
            <svg><image xlink:href="/images/c.png"/></svg><p>之后</p>"""
        val blocks = htmlBlocks(Ksoup.parse(html, base).body(), base)
        assertEquals(listOf("之前", "", "之间", "", "", "之后"), blocks.map { it.text })
        assertEquals(listOf("https://example.org/books/images/a.jpg", "https://cdn.example.org/b.jpg",
            "https://example.org/images/c.png"), blocks.mapNotNull { it.image })
    }

    @Test fun freeChaptersOnlyAndImagesSurviveParsing() = runTest {
        val repository = NovelSourceRepository { url ->
            when {
                url.contains("/chapters") -> """{"code":1,"data":{"pages":1,"list":[{"id":1,"name":"第1章 开始","vip":"0","cion":"0"},{"id":2,"name":"付费","vip":"1","cion":"0"},{"id":3,"name":"购买","vip":"0","cion":"5"}]}}"""
                else -> """{"code":1,"data":{"content":"<p>正文</p><img src='https://example.org/a.jpg'><script>bad()</script><p>最后一段</p>"}}"""
            }
        }
        val book = NovelBook(NovelSource.HUANMENG, "5684", "游戏人生")
        val chapters = repository.chapters(book)
        assertEquals(1, chapters.size)
        assertEquals(1, chapters.single().number)
        val blocks = repository.content(book, chapters.single())
        assertEquals(listOf("正文", "", "最后一段"), blocks.map { it.text })
        assertEquals("https://example.org/a.jpg", blocks[1].image)
    }

    @Test fun quanbenUsesAmpAndNoGuessedProgressNumbers() = runTest {
        val repository = NovelSourceRepository { url ->
            assertContains(url, "/amp/n/test/")
            if (url.endsWith("list.html")) "<ul class='list3'><li><a href='/amp/n/test/1.html'>序章</a></li><li><a href='/amp/n/test/2.html'>第2章 起始</a></li></ul>"
            else "<div class='articlebody'><p>第一段</p><p>第二段</p></div>"
        }
        val book = NovelBook(NovelSource.QUANBEN, "test", "测试")
        val chapters = repository.chapters(book)
        assertNull(chapters[0].number)
        assertEquals(2, chapters[1].number)
        assertEquals(2, repository.content(book, chapters[0]).size)
        assertFails { repository.content(book, chapters[0].copy(id = "https://elsewhere.invalid")) }
    }

    @Test fun cancellationAndMissingBodyRemainFailures() = runTest {
        val book = NovelBook(NovelSource.QUANBEN, "test", "test")
        val chapter = NovelChapter("https://www.quanben.io/amp/n/test/1.html", "test")
        assertFailsWith<CancellationException> { NovelSourceRepository { throw CancellationException() }.content(book, chapter) }
        assertFails { NovelSourceRepository { "<p>login</p>" }.content(book, chapter) }
    }
}
