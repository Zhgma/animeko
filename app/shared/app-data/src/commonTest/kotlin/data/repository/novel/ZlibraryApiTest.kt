/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.novel

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.content.TextContent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import me.him188.ani.utils.ktor.asScopedHttpClient
import kotlin.test.*

class ZlibraryApiTest {
    private val home = "https://zlib.test"
    private fun preferences() = object : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = transform(data.value).also { data.value = it }
    }
    private fun credentials() = ZlibrarySession(home, "123", "secret_key")

    @Test fun missingSessionRequiresLoginWithoutSendingSearch() = runTest {
        val client = HttpClient(MockEngine { error("Must not send anonymously") })
        try {
            val api = ZlibraryApi(client.asScopedHttpClient(), ZlibrarySessionStore(preferences()))
            assertEquals(home, assertFailsWith<ZlibraryLoginRequired> { api.search(home, "Test") }.origin)
        } finally { client.close() }
    }

    @Test fun validatedCookiesSurviveRecreationAndAreBoundToOrigin() = runTest {
        val prefs = preferences()
        val client = HttpClient(MockEngine { req ->
            assertEquals("/eapi/user/profile", req.url.encodedPath)
            assertContains(req.headers["Cookie"].orEmpty(), "remix_userkey=secret_key")
            respond("""{"success":1,"user":{"id":123}}""")
        })
        try {
            ZlibraryApi(client.asScopedHttpClient(), ZlibrarySessionStore(prefs)).accept(credentials())
            assertEquals("secret_key", ZlibrarySessionStore(prefs).read(home)?.userKey)
            assertNull(ZlibrarySessionStore(prefs).read("https://other.test"))
            assertFalse(credentials().toString().contains("secret_key"))
        } finally { client.close() }
    }

    @Test fun expiredSessionClearsOnlyTheRejectedCredential() = runTest {
        val store = ZlibrarySessionStore(preferences()); store.save(credentials())
        val client = HttpClient(MockEngine { respond("""{"success":0,"error":"Please login"}""", HttpStatusCode.BadRequest) })
        try {
            val api = ZlibraryApi(client.asScopedHttpClient(), store)
            assertFailsWith<ZlibraryLoginRequired> { api.search(home, "book") }
            assertNull(store.read(home))
            store.save(ZlibrarySession(home, "123", "new_key"))
            store.invalidate(credentials())
            assertEquals("new_key", store.read(home)?.userKey)
        } finally { client.close() }
    }

    @Test fun networkFailuresAndQuotaDoNotEraseLogin() = runTest {
        val store = ZlibrarySessionStore(preferences()); store.save(credentials())
        var quota = false
        val client = HttpClient(MockEngine {
            if (quota) respond("""{"success":0,"error":"download limit exceeded"}""")
            else respond("unavailable", HttpStatusCode.ServiceUnavailable)
        })
        try {
            val api = ZlibraryApi(client.asScopedHttpClient(), store)
            assertFailsWith<IllegalStateException> { api.search(home, "book") }.also { assertFalse(it is ZlibraryLoginRequired) }
            assertNotNull(store.read(home))
            quota = true
            assertFailsWith<IllegalStateException> { api.search(home, "book") }.also { assertFalse(it is ZlibraryLoginRequired) }
            assertNotNull(store.read(home))
        } finally { client.close() }
    }

    @Test fun searchFiltersFormatsAndKeepsEditionInformation() = runTest {
        val store = ZlibrarySessionStore(preferences()); store.save(credentials())
        val client = HttpClient(MockEngine { req ->
            assertEquals("POST", req.method.value)
            assertContains((req.body as TextContent).text, "extensions%5B%5D=epub")
            respond("""{"success":1,"books":[
              {"id":1,"hash":"abcd","title":"Test","author":"Writer","extension":"EPUB","language":"Chinese","year":2020},
              {"id":2,"hash":"bbcc","title":"Test","extension":"pdf"},
              {"id":3,"hash":"bbdd","title":"Test","extension":"azw3"}
            ]}""")
        })
        try {
            val books = ZlibraryApi(client.asScopedHttpClient(), store).search(home, "Test")
            assertEquals(listOf("1/abcd/epub", "3/bbdd/azw3"), books.map { it.id })
            assertContains(books.first().edition, "EPUB")
            assertContains(books.first().edition, "2020")
        } finally { client.close() }
    }

    @Test fun downloadAndRedirectNeverReceiveAccountCookies() = runTest {
        val store = ZlibrarySessionStore(preferences()); store.save(credentials())
        val calls = mutableListOf<String>()
        val client = HttpClient(MockEngine { req ->
            calls += req.url.host
            when (req.url.host) {
                "zlib.test" -> {
                    assertNotNull(req.headers["Cookie"])
                    respond("""{"success":1,"file":{"extension":"txt","downloadLink":"https://files.test/book"}}""")
                }
                "files.test" -> {
                    assertNull(req.headers["Cookie"]); assertNull(req.headers["Authorization"])
                    respond("", HttpStatusCode.Found, headersOf("Location", "https://cdn.test/content"))
                }
                else -> {
                    assertNull(req.headers["Cookie"]); assertNull(req.headers["Authorization"])
                    respond("Some book content")
                }
            }
        })
        try {
            val result = ZlibraryApi(client.asScopedHttpClient(), store).download(home, NovelBook(NovelSource.ZLIBRARY, "1/abcd/txt", "Test"))
            assertEquals("book.txt", result.first)
            assertEquals("Some book content", result.second.decodeToString())
            assertEquals(listOf("zlib.test", "files.test", "cdn.test"), calls)
        } finally { client.close() }
    }

    @Test fun authenticatedApiRedirectCannotForwardCookies() = runTest {
        val store = ZlibrarySessionStore(preferences()); store.save(credentials())
        var count = 0
        val client = HttpClient(MockEngine { req ->
            count++; assertEquals("zlib.test", req.url.host)
            respond("", HttpStatusCode.Found, headersOf("Location", "https://other.test/steal"))
        })
        try {
            assertFails { ZlibraryApi(client.asScopedHttpClient(), store).search(home, "Test") }
            assertEquals(1, count)
            assertNotNull(store.read(home))
        } finally { client.close() }
    }
}
