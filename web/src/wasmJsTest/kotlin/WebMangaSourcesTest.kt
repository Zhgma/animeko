// SPDX-License-Identifier: AGPL-3.0-only
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import me.him188.ani.app.data.repository.manga.*

@OptIn(ExperimentalCoroutinesApi::class)
class WebMangaSourcesTest {
    private val source = MangaSource.KOMIIC
    private val book = MangaSourceBook(source, "123", "碧蓝之海")
    @Test fun onlyEnabledSupportedSubscriptionAdaptersAreUsed() {
        fun row(key: String, version: Int = 1, enabled: Boolean = true) = FeedSource(key, key, enabled = enabled,
            format = buildJsonObject { put("factoryId", "manga-native"); put("version", version); putJsonObject("arguments") { put("key", key) } })
        val subscription = BrowserSubscription("sub", "漫画", kind = "manga", sources = listOf(row("komiic"), row("komiic"), row("mangadex"), row("goda", 2), row("manhuaren", enabled = false)))
        assertEquals(listOf(source), browserMangaSources(BrowserMediaState(subscriptions = listOf(subscription))))
        assertTrue(browserMangaSources(BrowserMediaState(subscriptions = listOf(subscription.copy(enabled = false)))).isEmpty())
    }
    @Test fun cachedQueriesWaitForExplicitRetry() = runTest {
        var calls = 0
        val session = WebMangaSourceSession(this, { _, _ -> calls++; listOf(book) })
        session.setSources(listOf(source)); session.search(source, "碧蓝之海"); advanceUntilIdle()
        session.search(source, "碧蓝之海"); advanceUntilIdle(); assertEquals(1, calls)
        session.search(source, "碧蓝之海", true); advanceUntilIdle(); assertEquals(2, calls)
    }
    @Test fun lateCancelledSearchCannotOverwriteNewQuery() = runTest {
        val old = CompletableDeferred<Unit>()
        val session = WebMangaSourceSession(this, { _, query ->
            if (query == "old") withContext(NonCancellable) { old.await() }
            listOf(book.copy(title = query))
        })
        session.setSources(listOf(source)); session.search(source, "old"); runCurrent()
        session.search(source, "new"); runCurrent(); old.complete(Unit); advanceUntilIdle()
        assertEquals("new", session.results[source]?.books?.single()?.title)
    }
    @Test fun disabledSourceDropsItsCacheAndCannotBeSelected() = runTest {
        var saved = false
        val session = WebMangaSourceSession(this, { _, _ -> listOf(book) }, saveBook = { saved = true })
        session.setSources(listOf(source)); session.search(source, "test"); advanceUntilIdle()
        session.setSources(emptyList()); session.choose(book); session.search(source, "test"); advanceUntilIdle()
        assertTrue(session.results.isEmpty()); assertNull(session.book); assertFalse(saved)
    }
    @Test fun errorsAreSourceLocalAndCanBeRetried() = runTest {
        var fail = true
        val session = WebMangaSourceSession(this, { s, _ -> if (s == source && fail) error("quota"); listOf(book.copy(source = s)) })
        session.setSources(listOf(source, MangaSource.GODA)); session.match("test"); advanceUntilIdle()
        assertEquals("quota", session.results[source]?.error)
        assertEquals(1, session.results[MangaSource.GODA]?.books?.size)
        fail = false; session.search(source, "test", true); advanceUntilIdle(); assertNull(session.results[source]?.error)
    }
    @Test fun selectionIsPersistedAndStorageFailureIsVisible() = runTest {
        var persisted: MangaSourceBook? = null
        val session = WebMangaSourceSession(this, { _, _ -> emptyList() }, saveBook = { persisted = it })
        session.setSources(listOf(source)); session.choose(book); assertEquals(book, persisted); assertEquals(book, session.book)
        val broken = WebMangaSourceSession(this, { _, _ -> emptyList() }, saveBook = { error("full") })
        broken.setSources(listOf(source)); broken.choose(book)
        assertNull(broken.book); assertTrue(broken.message.orEmpty().contains("保存来源失败"))
    }
    @Test fun originalSourceLinksAreBounded() {
        assertEquals("https://komiic.com/comic/123", mangaBookUrl(book))
        assertNull(mangaBookUrl(book.copy(id = "../../other")))
        assertNull(mangaBookUrl(book.copy(source = MangaSource.MANGADEX)))
    }
    @Test fun sharedParserHandlesKomiicAndManhuaren() = runTest {
        val adapter = MangaSourceSearch(get = { """<ul class="book-list"><li><div class="book-list-info"><a href="/manhua-blue/"><span class="book-list-info-title">碧蓝之海</span></a></div></li></ul>""" },
            queryKomiic = { op, args -> assertEquals("Search", op); assertEquals("blue", args["keyword"]?.jsonPrimitive?.content)
                Json.parseToJsonElement("""{"searchComicsAndAuthors":{"comics":[{"id":"123","title":"碧蓝之海"}]}}""").jsonObject })
        assertEquals(book, adapter.search(source, "blue").single())
        assertEquals("blue", adapter.search(MangaSource.MANHUAREN, "blue").single().id)
    }
}
