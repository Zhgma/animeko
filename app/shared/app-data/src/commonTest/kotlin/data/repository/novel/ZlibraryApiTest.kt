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
    @Test fun browserCookiesAreScopedAndVerificationMustPassBeforeSessionIsSaved() = runTest {
        val store = ZlibrarySessionStore(preferences())
        val session = ZlibrarySession(home, "123", "secret_key", "Browser UA", mapOf(
            "__diamwall" to "site_verification", "c_token" to "bad\r\nheader", "unrelated" to "private"))
        var allowed = false
        val client = HttpClient(MockEngine { req ->
            assertEquals("zlib.test", req.url.host)
            assertContains(req.headers["Cookie"].orEmpty(), "__diamwall=site_verification")
            assertFalse(req.headers["Cookie"].orEmpty().contains("bad"))
            assertFalse(req.headers["Cookie"].orEmpty().contains("private"))
            when (req.url.encodedPath) {
                "/eapi/user/profile" -> respond("""{"success":1,"user":{"id":123}}""")
                "/eapi/book/search" -> if (allowed) respond("""{"success":1,"books":[]}""")
                    else respond("verification", HttpStatusCode.TemporaryRedirect)
                else -> error("Unexpected endpoint")
            }
        })
        try {
            val api = ZlibraryApi(client.asScopedHttpClient(), store)
            assertFailsWith<ZlibraryVerificationRequired> { api.accept(session, verifySearch = true) }
            assertNull(store.read(home))
            allowed = true
            api.accept(session, verifySearch = true)
            assertContains(store.read(home)!!.cookieHeader(), "__diamwall=site_verification")
            assertFalse(session.toString().contains("site_verification"))
        } finally { client.close() }
    }
    private val home = "https://zlib.test"
    private fun preferences() = object : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = transform(data.value).also { data.value = it }
    }
    private fun credentials() = ZlibrarySession(home, "123", "secret_key")

    @Test fun passwordLoginValidatesProfileThenSearchesAndDownloadsWithoutPersistingPassword() = runTest {
        val prefs = preferences()
        val paths = mutableListOf<String>()
        val client = HttpClient(MockEngine { req ->
            paths += req.url.encodedPath
            when (req.url.encodedPath) {
                "/eapi/user/login" -> {
                    assertEquals("POST", req.method.value)
                    assertEquals("siteLanguageV2=en", req.headers["Cookie"])
                    assertEquals("email=reader%2Btest%40example.com&password=p%26%3D%2Bword", (req.body as TextContent).text)
                    respond("""{"success":1,"user":{"id":123,"remix_userkey":"secret_key"}}""")
                }
                "/eapi/user/profile" -> {
                    assertContains(req.headers["Cookie"].orEmpty(), "remix_userkey=secret_key")
                    respond("""{"success":1,"user":{"id":123}}""")
                }
                "/eapi/book/search" -> respond("""{"success":1,"books":[{"id":1,"hash":"abcd","title":"Alice","extension":"txt"}]}""")
                "/eapi/book/1/abcd/file" -> respond("""{"success":1,"file":{"extension":"txt","downloadLink":"https://files.test/alice"}}""")
                "/alice" -> { assertNull(req.headers["Cookie"]); respond("Alice text") }
                else -> error("Unexpected endpoint")
            }
        })
        try {
            val api = ZlibraryApi(client.asScopedHttpClient(), ZlibrarySessionStore(prefs))
            api.login(home, "reader+test@example.com", "p&=+word")
            assertFalse(prefs.data.value.asMap().values.any { "p&=+word" in it.toString() || "reader" in it.toString() })
            val restored = ZlibraryApi(client.asScopedHttpClient(), ZlibrarySessionStore(prefs))
            val book = restored.search(home, "Alice").single()
            assertEquals("Alice text", restored.download(home, book).second.decodeToString())
            assertEquals(listOf("/eapi/user/login", "/eapi/user/profile", "/eapi/book/search", "/eapi/book/1/abcd/file", "/alice"), paths)
        } finally { client.close() }
    }

    @Test fun rejectedLoginAndWebsiteChallengePreserveExistingSessionAndNeverFollowRedirects() = runTest {
        val store = ZlibrarySessionStore(preferences()); store.save(credentials())
        var challenge = false
        var calls = 0
        val client = HttpClient(MockEngine { req ->
            calls++; assertEquals("zlib.test", req.url.host)
            if (challenge) respond("html", HttpStatusCode.TemporaryRedirect, headersOf("Location", "https://other.test"))
            else respond("""{"success":0,"error":"Incorrect email or password"}""", HttpStatusCode.BadRequest)
        })
        try {
            val api = ZlibraryApi(client.asScopedHttpClient(), store)
            assertEquals("邮箱或密码不正确", assertFailsWith<ZlibraryLoginFailed> { api.login(home, "reader@example.com", "wrong") }.message)
            challenge = true
            assertContains(assertFailsWith<ZlibraryLoginFailed> { api.login(home, "reader@example.com", "wrong") }.message.orEmpty(), "网页验证")
            assertEquals(2, calls)
            assertEquals("secret_key", store.read(home)?.userKey)
        } finally { client.close() }
    }

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
