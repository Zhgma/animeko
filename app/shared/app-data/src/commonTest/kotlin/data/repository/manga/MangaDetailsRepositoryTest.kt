/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.manga

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import me.him188.ani.app.data.models.subject.SelfRatingInfo
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.test.runTest
import me.him188.ani.utils.ktor.asScopedHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MangaDetailsRepositoryTest {
    @Test fun resolvesMissingBookPlatformWithoutChangingAnimeTransport() = runTest {
        var requests = 0
        val client = HttpClient(MockEngine { request ->
            requests++
            assertEquals("/v0/subjects/140", request.url.encodedPath)
            assertNull(request.headers["Authorization"])
            respond("""{"id":140,"type":1,"platform":"小说"}""")
        })
        try {
            val repository = MangaDetailsRepository(client.asScopedHttpClient())
            val related = MangaRelation(140, 1, "第十四卷", "Volume 14", "cover", "单行本")
            assertEquals("小说", repository.resolveRelation(related).platform)
            assertEquals(related.copy(platform = "漫画"), repository.resolveRelation(related.copy(platform = "漫画")))
            assertEquals(related.copy(type = 2), repository.resolveRelation(related.copy(type = 2)))
            assertEquals(1, requests)
        } finally { client.close() }
    }

    @Test
    fun metadataKeepsRatingDistributionAndStructuredAliases() = runTest {
        val client = HttpClient(MockEngine {
            respond("""{"type":1,"name":"Original","name_cn":"中文","images":{"large":"cover"},
                "rating":{"rank":25,"score":8.5,"total":12,"count":{"8":3,"9":9}},
                "infobox":[{"key":"作者","value":"Author"},{"key":"别名","value":[{"v":"A"},{"v":"B"}]}],
                "collection":{"doing":10},"tags":[{"name":"喜剧"}]}""")
        })
        try {
            val details = MangaDetailsRepository(client.asScopedHttpClient()).details(1, null)
            assertEquals("8.5", details.rating.score)
            assertEquals(9, details.rating.count.s9)
            assertEquals(listOf("A", "B"), details.aliases)
            assertEquals(10, details.collections["doing"])
        } finally { client.close() }
    }

    @Test
    fun publicCommentsUseSeparateEndpointWithoutAccountToken() = runTest {
        val client = HttpClient(MockEngine { request ->
            assertEquals("next.bgm.tv", request.url.host)
            assertEquals("20", request.url.parameters["offset"])
            assertNull(request.headers["Authorization"])
            respond("""{"total":21,"data":[{"id":7,"user":{"nickname":"Reader","avatar":{"medium":"avatar"}},"comment":"Review","rate":9}]}""")
        })
        try {
            val comments = MangaDetailsRepository(client.asScopedHttpClient()).comments(1, 20)
            assertEquals(21, comments.total)
            assertEquals("Review", comments.items.single().text)
            assertEquals(9, comments.items.single().rating)
        } finally { client.close() }
    }
    @Test
    fun ratingPatchPreservesCollectionProgressAndTags() = runTest {
        val client = HttpClient(MockEngine { request ->
            assertEquals(HttpMethod.Patch, request.method)
            assertEquals("/v0/users/-/collections/355555", request.url.encodedPath)
            assertEquals("Bearer test-token", request.headers["Authorization"])
            val payload = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertEquals(setOf("rate", "comment", "private"), payload.keys)
            assertEquals("8", payload["rate"].toString())
            respond("", HttpStatusCode.NoContent)
        })
        try { MangaDetailsRepository(client.asScopedHttpClient()).rate(355555, "test-token", SelfRatingInfo(8, "Review", listOf("keep"), true)) }
        finally { client.close() }
    }

    @Test
    fun selfRatingLoadsPrivateCommentAndUsesAuthenticatedUsername() = runTest {
        val client = HttpClient(MockEngine { request ->
            assertEquals("Bearer token", request.headers["Authorization"])
            if (request.url.encodedPath == "/v0/me") respond("""{"username":"reader"}""")
            else {
                assertEquals("/v0/users/reader/collections/12", request.url.encodedPath)
                respond("""{"rate":9,"comment":"Private review","tags":["tag"],"private":true}""")
            }
        })
        try {
            assertEquals(SelfRatingInfo(9, "Private review", listOf("tag"), true),
                MangaDetailsRepository(client.asScopedHttpClient()).selfRating(12, "token"))
        } finally { client.close() }
    }

    @Test
    fun uncollectedSubjectDoesNotInventAnEmptySavedRating() = runTest {
        val client = HttpClient(MockEngine { request ->
            if (request.url.encodedPath == "/v0/me") respond("""{"username":"reader"}""")
            else respond("{}", HttpStatusCode.NotFound)
        })
        try { assertNull(MangaDetailsRepository(client.asScopedHttpClient()).selfRating(12, "token")) }
        finally { client.close() }
    }

    @Test
    fun relationsKeepMediaTypeAndOriginalRelationLabels() = runTest {
        val client = HttpClient(MockEngine { request ->
            assertEquals("/v0/subjects/355555/subjects", request.url.encodedPath)
            respond("""[{"id":513345,"type":2,"name":"Anime","name_cn":"动画版","relation":"动画","images":{"common":"cover"}},
                {"id":372122,"type":1,"name":"Volume 1","relation":"单行本"}]""")
        })
        try {
            val result = MangaDetailsRepository(client.asScopedHttpClient()).relations(355555, null)
            assertEquals(listOf(2, 1), result.map { it.type })
            assertEquals(listOf("动画", "单行本"), result.map { it.relation })
            assertEquals("Volume 1", result[1].title)
        } finally { client.close() }
    }

}
