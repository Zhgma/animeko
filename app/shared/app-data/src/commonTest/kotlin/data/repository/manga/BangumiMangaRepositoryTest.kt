/*
 * Copyright (C) 2026 OpenAni and contributors.
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package me.him188.ani.app.data.repository.manga

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import me.him188.ani.utils.ktor.asScopedHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BangumiMangaRepositoryTest {
    @Test
    fun `continues past novel-only pages and retains manga reading progress`() = runTest {
        val offsets = mutableListOf<String?>()
        val client = HttpClient(MockEngine { request ->
            assertEquals("api.bgm.tv", request.url.host)
            assertEquals("Bearer test-token", request.headers["Authorization"])
            val body = when (request.url.encodedPath) {
                "/v0/me" -> """{"username":"reader"}"""
                "/v0/users/reader/collections" -> {
                    assertEquals("1", request.url.parameters["subject_type"])
                    assertEquals("3", request.url.parameters["type"])
                    offsets.add(request.url.parameters["offset"])
                    if (offsets.size == 1) {
                        """{"total":2,"data":[{"subject_id":1,"subject_type":1}]}"""
                    } else {
                        """{"total":2,"data":[{"subject_id":2,"subject_type":1,"ep_status":45,"vol_status":7,"rate":9,"private":true}]}"""
                    }
                }
                "/v0/subjects/1" -> """{"type":1,"platform":"小说","name":"Novel"}"""
                "/v0/subjects/2" -> """{"type":1,"platform":"漫画","name":"ぐらんぶる","name_cn":"碧蓝之海","eps":100,"volumes":20,"images":{"common":"https://example.com/cover.jpg"},"extra_field":true}"""
                else -> error("Unexpected request ${request.url}")
            }
            respond(body)
        })
        try {
            val pages = mutableListOf<List<MangaCollection>>()
            BangumiMangaRepository(client.asScopedHttpClient()).loadCollections("test-token", MangaCollectionType.DOING) { user, items ->
                assertEquals("reader", user)
                pages += items
            }
            assertEquals(listOf<String?>("0", "1"), offsets)
            assertTrue(pages.first().isEmpty())
            val manga = pages.last().single()
            assertEquals("碧蓝之海", manga.title)
            assertEquals(45, manga.chaptersRead)
            assertEquals(7, manga.volumesRead)
            assertEquals(100, manga.totalChapters)
            assertTrue(manga.isPrivate)
        } finally {
            client.close()
        }
    }

    @Test
    fun `empty pages terminate even when total is stale`() = runTest {
        var requests = 0
        val client = HttpClient(MockEngine { request ->
            requests++
            respond(if (request.url.encodedPath == "/v0/me") """{"username":"reader"}"""
                else """{"total":100,"data":[]}""")
        })
        try {
            BangumiMangaRepository(client.asScopedHttpClient()).loadCollections("token", MangaCollectionType.WISH) { _, items ->
                assertTrue(items.isEmpty())
            }
            assertEquals(2, requests)
        } finally {
            client.close()
        }
    }

    @Test
    fun `unauthorized is not reported as an empty library`() = runTest {
        val client = HttpClient(MockEngine { respond("{}", HttpStatusCode.Unauthorized) })
        try {
            assertFailsWith<MangaAuthorizationException> {
                BangumiMangaRepository(client.asScopedHttpClient()).loadCollections("expired", MangaCollectionType.DOING) { _, _ ->
                    error("An unauthorized response must not produce collections")
                }
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun `cancellation propagates to prevent old account results`() = runTest {
        val client = HttpClient(MockEngine { throw CancellationException("Account changed") })
        try {
            assertFailsWith<CancellationException> {
                BangumiMangaRepository(client.asScopedHttpClient()).loadCollections("token", MangaCollectionType.DOING) { _, _ ->
                    error("Cancelled request must not produce collections")
                }
            }
        } finally {
            client.close()
        }
    }
}
