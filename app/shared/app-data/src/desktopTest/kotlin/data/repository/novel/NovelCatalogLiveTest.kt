/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.novel

import java.net.InetSocketAddress
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URI
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertTrue

class NovelCatalogLiveTest {
    private fun verify(key: String) = runBlocking {
        assumeTrue(System.getenv("NOVEL_LIVE_TEST") == "1")
        val definition = DefaultNovelSources.all.first { it.key == key }
        assumeTrue(definition.unavailableReason.isBlank(), definition.unavailableReason)
        val repository = NovelSourceRepository({ req ->
            val c = URI(req.url).toURL().openConnection(if (key == "washai" && System.getenv("NOVEL_TEST_PROXY") == "1") Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 7890)) else Proxy.NO_PROXY) as HttpURLConnection
            c.connectTimeout = 30_000; c.readTimeout = 30_000
            c.requestMethod = req.method
            c.setRequestProperty("User-Agent", "Mozilla/5.0")
            req.headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (req.method == "POST") {
                c.doOutput = true; c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                c.outputStream.use { it.write(req.body.toByteArray()) }
            }
            try { c.inputStream.bufferedReader().use { it.readText() } } finally { c.disconnect() }
        }, { error("not needed") }, { DefaultNovelSources.all })
        val results = repository.search(definition.source, "全职高手")
        val book = results.first { it.title.trim() == "全职高手" }
        val chapters = repository.chapters(book)
        for (index in listOf(0, chapters.lastIndex / 2, chapters.lastIndex).distinct()) {
            val blocks = repository.content(book, chapters[index])
            val han = blocks.sumOf { it.text.count { c -> c in '\u4e00'..'\u9fff' } }
            assertTrue(han > 100, "$key chapter $index: $han Chinese characters")
            println("NOVEL_CATALOG $key chapter $index ${chapters[index].title}: $han Chinese characters")
        }
        println("NOVEL_CATALOG $key: ${results.size} matches, ${chapters.size} chapters, first/middle/last readable")
    }
    @Test fun jiuai() = verify("jiuai")
    @Test fun shuhaige() = verify("shuhaige")
    @Test fun washai() = verify("washai")
    @Test fun wuba() = verify("wuba")
}
