/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.manga

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.Cookie
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.domain.mediasource.web.BlockedException
import me.him188.ani.app.domain.mediasource.web.captcha.WebSourceCookieJar
import me.him188.ani.app.domain.mediasource.web.captcha.WebSourceIdentityRegistry
import me.him188.ani.utils.ktor.asScopedHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class MangaSourceSessionTest {
    @Test fun browserSessionIsAppliedOnlyToItsOwnHost() = runTest {
        val cookies = WebSourceCookieJar()
        cookies.addCookie(Url("https://www.manhuagui.com/"), Cookie("verified", "yes", path = "/", secure = true))
        val identities = WebSourceIdentityRegistry().apply { setUserAgent("www.manhuagui.com", "Test browser") }
        val client = HttpClient(MockEngine { request ->
            if (request.url.host == "www.manhuagui.com") {
                assertEquals("verified=yes", request.headers["Cookie"])
                assertEquals("Test browser", request.headers["User-Agent"])
                respond("<html></html>", headers = headersOf("Content-Type", "text/html"))
            } else {
                assertNull(request.headers["Cookie"])
                respond("{\"data\":{\"searchComicsAndAuthors\":{\"comics\":[]}}}")
            }
        })
        try {
            val repository = MangaSourceRepository(client.asScopedHttpClient(), cookies, identities)
            repository.search(MangaSource.MANHUAGUI, "test")
            repository.search(MangaSource.KOMIIC, "test")
        } finally { client.close() }
    }

    @Test fun challengeProducesTheSharedInteractiveVerificationRequest() = runTest {
        val client = HttpClient(MockEngine { respond("<html><title>Just a moment...</title><script src='/cdn-cgi/challenge-platform/scripts/jsd/main.js'></script></html>",
            HttpStatusCode.Forbidden, headersOf("Content-Type", "text/html")) })
        try {
            val blocked = assertFailsWith<BlockedException> { MangaSourceRepository(client.asScopedHttpClient()).search(MangaSource.MANHUAGUI, "test") }
            assertEquals("manga:MANHUAGUI", blocked.request.mediaSourceId)
            assertEquals("www.manhuagui.com", Url(blocked.request.pageUrl).host)
        } finally { client.close() }
    }
}
