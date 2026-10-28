/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.manga

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import me.him188.ani.app.data.models.preference.ProxyMode
import me.him188.ani.app.data.models.preference.ProxySettings
import me.him188.ani.utils.ktor.asScopedHttpClient
import kotlin.io.encoding.Base64
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RelayRoutingPolicyTest {
    @AfterTest fun reset() { RelayRoutingPolicy.enabled.value = false; RelayEndpoints.apply(DEFAULT_RELAY_ORIGIN) }

    @Test fun onlyExplicitMainlandAndDisabledProxyEnableRelay() {
        for (region in listOf(null, true, false)) for (mode in ProxyMode.entries) {
            assertEquals(region == false && mode == ProxyMode.DISABLED, RelayRoutingPolicy.shouldRelay(region, mode), "$region $mode")
        }
    }

    @Test fun savedSettingsAreReadyBeforeStartupAndChangesTakeEffect() = runTest {
        val region = MutableStateFlow<Boolean?>(false)
        val proxy = MutableStateFlow(ProxySettings.Disabled)
        var changes = 0
        RelayRoutingPolicy.start(region, proxy, backgroundScope) { changes++ }
        assertTrue(RelayRoutingPolicy.enabled.value)
        proxy.value = proxy.value.copy(default = proxy.value.default.copy(mode = ProxyMode.SYSTEM))
        runCurrent()
        assertEquals(false, RelayRoutingPolicy.enabled.value)
        proxy.value = ProxySettings.Disabled
        runCurrent()
        assertTrue(RelayRoutingPolicy.enabled.value)
        region.value = null
        runCurrent()
        assertEquals(false, RelayRoutingPolicy.enabled.value)
        assertEquals(4, changes)
    }

    @Test fun bookReadsAndWritesSwitchWithoutRecreatingHttpOrFallingBackToRelay() = runTest {
        var relay = true
        val targets = mutableListOf<String>()
        val client = HttpClient(MockEngine { targets += it.url.toString(); respond("{}") })
        try {
            val http = MangaBangumiHttp(client.asScopedHttpClient(), booksApiBaseUrl = BOOKS_API_BASE_URL,
                booksApiBaseUrlProvider = { BOOKS_API_BASE_URL.takeIf { relay } })
            val endpoints = listOf("https://api.bgm.tv/v0/me", "https://api.bgm.tv/v0/users/test/collections",
                "https://api.bgm.tv/v0/subjects/311122", "https://api.bgm.tv/v0/subjects/311122/subjects",
                "https://next.bgm.tv/p1/subjects/311122/comments")
            for (enabled in listOf(true, false)) {
                relay = enabled
                for (url in endpoints) http.get(url, "test-token")
                http.search("{}", "test-token", 0, 10)
                http.write(HttpMethod.Patch, "https://api.bgm.tv/v0/users/-/collections/311122", "test-token", """{"ep_status":3}""")
            }
            assertTrue(targets.take(7).all { it.startsWith(BOOKS_API_BASE_URL) })
            assertEquals(endpoints, targets.drop(7).take(5))
            assertTrue(targets.drop(7).none { RelayEndpoints.isRelayRequest(it) })
        } finally { client.close() }
    }

    private fun image(service: String, payload: String) = "$DEFAULT_RELAY_ORIGIN/api/$service/images/${Base64.UrlSafe.encode(payload.encodeToByteArray()).trimEnd('=')}/signature"

    @Test fun cachedSignedImagesAndIconResolveToFixedOriginsWhenRelayIsOff() {
        val book = image("books", "/pic/cover/l/aa/test.jpg?r=123")
        assertEquals("https://lain.bgm.tv/pic/cover/l/aa/test.jpg?r=123", RelayEndpoints.imageRoute(book, false).url)
        val kid = "11111111-1111-1111-1111-111111111111"
        val asset = image("komiic", """{"host":"komiic.com","path":"/api/image/$kid","referer":"https://komiic.com/comic/115/chapter/1490/images/all"}""")
        val direct = RelayEndpoints.imageRoute(asset, false)
        assertEquals("https://komiic.com/api/image/$kid", direct.url)
        assertEquals("https://komiic.com/comic/115/chapter/1490/images/all", direct.referer)
        assertEquals(asset, RelayEndpoints.imageRoute(asset, true).url)
        assertEquals("https://komiic.com/favicon.ico", RelayEndpoints.imageRoute("$KOMIIC_API_BASE_URL/icon", false).url)
        val cover = image("komiic", """{"host":"public.komiic.com","path":"/comics/11111111111111111111111111111111/cover.jpg","referer":"https://komiic.com/"}""")
        assertEquals("https://public.komiic.com/comics/11111111111111111111111111111111/cover.jpg", RelayEndpoints.imageRoute(cover, false).url)
        RelayEndpoints.apply("https://relay.example:443")
        assertEquals("https://lain.bgm.tv/pic/cover/l/aa/test.jpg?r=123",
            RelayEndpoints.imageRoute(book.replace(DEFAULT_RELAY_ORIGIN, "https://relay.example"), false).url)
        assertFails { RelayEndpoints.imageRoute(image("komiic", """{"host":"evil.example","path":"/a","referer":"https://komiic.com/"}"""), false) }
        assertFails { RelayEndpoints.imageRoute("$KOMIIC_API_BASE_URL/images/broken/sig", false) }
    }

    @Test fun alreadyHeldImageUrlsAreReroutedAtTransportAndCredentialsAreRemoved() = runTest {
        RelayRoutingPolicy.enabled.value = false
        val client = HttpClient(MockEngine {
            assertEquals("https://lain.bgm.tv/pic/cover/l/test.jpg", it.url.toString())
            assertNull(it.headers["Authorization"])
            assertNull(it.headers["Cookie"])
            respond("image")
        }).apply { installRelayImageRouting() }
        try {
            client.get(image("books", "/pic/cover/l/test.jpg")) {
                header("Authorization", "Bearer private"); header("Cookie", "session=private")
            }
        } finally { client.close() }
    }

    @Test fun komiicSearchCatalogPagesAndImagesUseOriginWithoutRelay() = runTest {
        RelayRoutingPolicy.enabled.value = false
        val kid = "11111111-1111-1111-1111-111111111111"
        val operations = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            assertEquals("komiic.com", request.url.host)
            assertNull(request.headers["Authorization"])
            if (request.url.encodedPath.startsWith("/api/image/")) {
                assertEquals("https://komiic.com/comic/115/chapter/1490/images/all", request.headers["Referer"])
                respond(byteArrayOf(1, 2), headers = headersOf("Content-Type", "image/jpeg"))
            } else {
                assertEquals("/api/query", request.url.encodedPath)
                val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                assertNull(body["operation"])
                val op = body.text("query").substringAfter("query ").substringBefore("(")
                operations += op
                respond(when (op) {
                    "Search" -> """{"data":{"searchComicsAndAuthors":{"comics":[{"id":"115","title":"Test","imageUrl":"https://public.komiic.com/cover.jpg"}]}}}"""
                    "Chapters" -> """{"data":{"chaptersByComicId":[{"id":"1490","serial":"1","type":"chapter","size":1}]}}"""
                    else -> {
                        assertEquals(setOf("chapterId"), body.obj("variables").keys)
                        """{"data":{"imagesByChapterId":[{"kid":"$kid","width":704,"height":1008}]}}"""
                    }
                })
            }
        })
        try {
            val repo = MangaSourceRepository(client.asScopedHttpClient())
            val book = repo.search(MangaSource.KOMIIC, "Test").single()
            val page = repo.pages(book, repo.chapters(book).single()).single()
            assertEquals("https://komiic.com/api/image/$kid", page.url)
            assertEquals(2, repo.image(page).size)
            assertEquals(listOf("Search", "Chapters", "Images"), operations)
            assertEquals("https://komiic.com/favicon.ico", MangaSource.KOMIIC.iconUrl)
        } finally { client.close() }
    }
}
