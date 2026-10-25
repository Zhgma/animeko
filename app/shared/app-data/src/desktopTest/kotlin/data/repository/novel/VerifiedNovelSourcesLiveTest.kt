/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.novel

import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertTrue

class VerifiedNovelSourcesLiveTest {
    @Test fun previouslyRequestedNineBooksHaveReadableFreeChapters() = runBlocking {
        assumeTrue(System.getenv("NOVEL_LIVE_TEST") == "1")
        val repository = NovelSourceRepository { url ->
            val connection = URI(url).toURL().openConnection(Proxy.NO_PROXY)
            connection.connectTimeout = 30_000; connection.readTimeout = 30_000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 Animecomic")
            connection.getInputStream().bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
        val books = listOf(
            NovelBook(NovelSource.QUANBEN, "quanzhigaoshou", "全职高手"),
            NovelBook(NovelSource.QUANBEN, "fanrenxiuxianzhuan", "凡人修仙传"),
            NovelBook(NovelSource.QUANBEN, "santi", "三体"),
            NovelBook(NovelSource.QUANBEN, "2001taikongmanyou", "2001太空漫游"),
            NovelBook(NovelSource.QUANBEN, "yinhediguo1-jidi", "银河帝国"),
            NovelBook(NovelSource.QUANBEN, "dawangraoming", "大王饶命"),
            NovelBook(NovelSource.HUANMENG, "5684", "游戏人生"),
            NovelBook(NovelSource.HUANMENG, "8647", "刀剑神域"),
            NovelBook(NovelSource.HUANMENG, "6078", "我是不才恶女"),
        )
        for (book in books) {
            println("NOVEL_LIVE checking ${book.title}")
            val results = repository.search(book.source, book.title)
            assertTrue(results.any { it.id == book.id }, "${book.title} not in search results")
            val chapters = repository.chapters(book)
            val storyEnd = if (book.id == "yinhediguo1-jidi") 52 else chapters.lastIndex
            for (index in listOf(0, storyEnd / 2, storyEnd).distinct()) {
                val blocks = repository.content(book, chapters[index])
                val han = blocks.sumOf { it.text.count { c -> c in '\u4e00'..'\u9fff' } }
                assertTrue(han > 100, "${book.title} ${chapters[index].title}: $han Chinese characters")
            }
            println("NOVEL_LIVE ${book.title}: ${chapters.size} chapters; first/middle/last story readable")
        }
    }
}
