/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.novel

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import me.him188.ani.utils.ktor.asScopedHttpClient
import kotlin.test.*

class NovelSubscriptionTest {
    private fun preferences() = object : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = transform(data.value).also { data.value = it }
    }
    private fun source(key: String = "new-site", selector: String = ".body") = NovelSourceDefinition(
        key, "测试书源", "https://books.test", searchUrl = "/search?q={{key}}", searchList = ".book",
        searchName = "a@text", searchLink = "a@href", chapters = ".chapter", content = selector)
    private fun sub(vararg sources: NovelSourceDefinition) = """{"exportedMediaSourceDataList":{"mediaSources":[${
        sources.joinToString { """{"factoryId":"novel-rules","version":1,"arguments":${Json.encodeToString(it)}}""" }
    }]}}"""

    @Test fun sameRemoteUrlCanAddNewSourcesAndChangeRulesWhileFailurePreservesCache() = runTest {
        val store = NovelSourceSettingsStore(preferences())
        var body = sub(source())
        var fail = false
        val client = HttpClient(MockEngine { if (fail) respond("offline", HttpStatusCode.ServiceUnavailable) else respond(body) })
        try {
            val repo = NovelSubscriptionRepository(store, client.asScopedHttpClient())
            val subscription = store.read().subscriptions.single()
            repo.refresh(subscription)
            assertEquals(listOf(NovelSource("new-site")), store.read().enabled)
            body = sub(source(selector = "#new-body"), source("another-site"))
            repo.refresh(subscription)
            val fresh = store.read()
            assertEquals(2, fresh.enabled.size)
            assertEquals("#new-body", fresh.definition(NovelSource("new-site"))!!.content)
            assertEquals(subscription.url, fresh.subscriptions.single().url)
            fail = true; repo.refresh(subscription)
            assertEquals(fresh.definitions, store.read().definitions)
            assertNotNull(store.read().subscriptions.single().error)
            repo.setEnabled(subscription.id, false)
            assertTrue(store.read().enabled.isEmpty())
            repo.delete(subscription.id)
            assertTrue(store.read().subscriptions.isEmpty())
            assertTrue(NovelSourceSettingsStore(preferences()).read().definitions.size >= 6)
        } finally { client.close() }
    }

    @Test fun manualAddEditAndDeletePreserveDisabledSourcesAndAvoidDuplicates() = runTest {
        val store = NovelSourceSettingsStore(preferences())
        var body = sub(source())
        val client = HttpClient(MockEngine { respond(body) })
        try {
            val repo = NovelSubscriptionRepository(store, client.asScopedHttpClient())
            repo.save(null, "我的订阅", "https://sub.test/novel.json")
            val added = store.read().subscriptions.last()
            assertEquals(2, store.read().subscriptions.size)
            assertFails { repo.save(null, "重复", added.url) }
            repo.setEnabled(added.id, false)
            body = "bad json"
            assertFails { repo.save(added.id, "无效编辑", "https://sub.test/bad.json") }
            assertEquals(added.url, store.read().subscriptions.last().url)
            body = sub(source("second-site"))
            repo.save(added.id, "已编辑", "https://sub.test/new.json")
            assertFalse(store.read().subscriptions.last().enabled)
            repo.delete(added.id)
            repo.refresh(added)
            assertEquals(1, store.read().subscriptions.size)
        } finally { client.close() }
    }

    @Test fun oldEnumMemoryRemainsReadableAndNewSourceIdsRoundTrip() {
        val book = Json.decodeFromString<NovelBook>("""{"source":"QUANBEN","id":"santi","title":"三体"}""")
        assertEquals(NovelSource.QUANBEN, book.source)
        val dynamic = book.copy(source = NovelSource("some-new-site"))
        assertEquals(dynamic, Json.decodeFromString<NovelBook>(Json.encodeToString(dynamic)))
        assertEquals(6, DefaultNovelSources.all.size)
        DefaultNovelSources.all.forEach { it.validate() }
    }

    @Test fun acceptsPassiveLegadoAndReportsUnsupportedRulesWithoutExecutingThem() {
        val result = NovelSubscriptionParser.parse("""[
          {"bookSourceName":"静态","bookSourceUrl":"https://custom.test","searchUrl":"/search?q={{key}}",
           "ruleSearch":{"bookList":".books li","name":"a@text","bookUrl":"a@href"},
           "ruleToc":{"chapterList":".chapters li","chapterName":"a@text","chapterUrl":"a@href"},
           "ruleContent":{"content":"#content"}},
          {"bookSourceName":"脚本","bookSourceUrl":"https://dynamic.test","searchUrl":"@js: throw 1"}
        ]""")
        assertEquals(1, result.sources.size)
        assertEquals(1, result.unsupported.size)
        assertFails { NovelSubscriptionParser.parse(sub(source().copy(searchList = "@js:bad"))) }
        assertFails { NovelSubscriptionParser.parse(sub(source(), source())) }
    }

    @Test fun remoteRulesDrivePostSearchCatalogPaginationAndCompleteChapterPagination() = runTest {
        val d = source().copy(searchMethod = "POST", searchBody = "q={{key}}", tocLink = "#toc@href",
            nextToc = "a.next@href", nextContent = "a.next@href")
        val calls = mutableListOf<NovelRequest>()
        val reader = NovelRuleReader { request ->
            calls += request
            when {
                request.url.contains("/search?") -> "<div class='book'><a href='/book'>全职高手</a></div>"
                request.url.endsWith("/book") -> "<a id='toc' href='/toc1'>目录</a>"
                request.url.endsWith("/toc1") -> "<div class='chapter'><a href='/c1'>第1章</a></div><a class='next' href='/toc2'>下一页</a>"
                request.url.endsWith("/toc2") -> "<div class='chapter'><a href='/c2'>第2章</a></div><div class='chapter'><a href='/paid'>VIP 第3章</a></div>"
                request.url.endsWith("/c1") -> "<div class='body'><p>第一页正文</p><script>bad()</script></div><a class='next' href='/c1-2'>下一页</a>"
                request.url.endsWith("/c1-2") -> "<div class='body'><p>第二页正文</p></div>"
                else -> error(request.url)
            }
        }
        val book = reader.search(d, "全职高手").single()
        assertEquals("POST", calls.first().method)
        assertTrue(calls.first().body.startsWith("q=%"))
        val chapters = reader.chapters(d, book)
        assertEquals(2, chapters.size)
        val text = reader.content(d, chapters.first()).joinToString { it.text }
        assertTrue("第一页正文" in text && "第二页正文" in text)
        assertFalse("bad()" in text)
        val changed = NovelRuleReader { "<main id='new-body'>远程规则更新后的正文</main>" }
        assertEquals("远程规则更新后的正文", changed.content(d.copy(content = "#new-body"), chapters.first()).single().text)
    }
}
