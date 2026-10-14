/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.manga

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import me.him188.ani.utils.ktor.asScopedHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class KomiicImageRequestsTest {
    @Test fun concurrentReadersAndDownloadsShareOnePacedQueue() = runTest {
        val gate = KomiicImageRequests { testScheduler.currentTime }
        val starts = mutableListOf<Long>()
        val finishes = mutableListOf<Long>()
        var active = 0
        val client = HttpClient(MockEngine {
            assertEquals(0, active++)
            starts += testScheduler.currentTime
            delay(200)
            finishes += testScheduler.currentTime
            active--
            respond(byteArrayOf(1), HttpStatusCode.OK, headersOf("Content-Type", "image/jpeg"))
        })
        try {
            val repos = List(3) { MangaSourceRepository(client.asScopedHttpClient(), null, null, gate) }
            repos.mapIndexed { index, repo -> async {
                repo.image(MangaPage("$KOMIIC_API_BASE_URL/images/$index/signature", ""))
            } }.awaitAll()
            assertEquals(3, starts.size)
            starts.drop(1).zip(finishes).forEach { (start, previousFinish) ->
                assertTrue(start - previousFinish >= 1_000)
            }
        } finally { client.close() }
    }

    @Test fun readingQuotaStopsQueuedImagesAndDoesNotBlockOtherSources() = runTest {
        val gate = KomiicImageRequests { testScheduler.currentTime }
        var requests = 0
        val client = HttpClient(MockEngine { request ->
            if (request.url.host == "images.test") {
                respond(byteArrayOf(1), HttpStatusCode.OK, headersOf("Content-Type", "image/jpeg"))
            } else {
                requests++
                delay(100)
                respond("""{"error":"komiic_reading_limit"}""", HttpStatusCode.TooManyRequests,
                    headersOf("Retry-After", "60"))
            }
        })
        try {
            val repo = MangaSourceRepository(client.asScopedHttpClient(), null, null, gate)
            val errors = List(4) { index -> async {
                assertFailsWith<MangaSourceAccessException> {
                    repo.image(MangaPage("$KOMIIC_API_BASE_URL/images/$index/signature", ""))
                }
            } }.awaitAll()
            assertEquals(1, requests)
            errors.forEach {
                assertTrue(it.message.orEmpty().contains("源站阅读额度已用完"))
                assertEquals(60_000L, it.retryAfterMillis)
            }
            assertEquals(1, repo.image(MangaPage("https://images.test/page", "")).size)
            delay(60_000)
            // Cooldown expiry permits an explicit attempt; it does not restart the stopped queue.
            assertEquals(1, requests)
            assertFailsWith<MangaSourceAccessException> {
                repo.image(MangaPage("$KOMIIC_API_BASE_URL/images/retry/signature", ""))
            }
            assertEquals(2, requests)
        } finally { client.close() }
    }

    @Test fun ordinaryThrottlingUsesRetryAfterInsteadOfQuotaMessage() = runTest {
        val client = HttpClient(MockEngine {
            respond("""{"error":"komiic_rate_limited"}""", HttpStatusCode.TooManyRequests,
                headersOf("Retry-After", "45"))
        })
        try {
            val repo = MangaSourceRepository(client.asScopedHttpClient(), null, null,
                KomiicImageRequests { testScheduler.currentTime })
            val error = assertFailsWith<MangaSourceAccessException> {
                repo.image(MangaPage("$KOMIIC_API_BASE_URL/images/page/signature", ""))
            }
            assertTrue(error.message.orEmpty().contains("45 秒后重试"))
            assertEquals(45_000L, error.retryAfterMillis)
        } finally { client.close() }
    }
}
