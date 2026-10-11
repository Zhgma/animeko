/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.manga

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import me.him188.ani.utils.ktor.asScopedHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class MangaLibraryRepositoryTest {
    @Test fun collectionAndProgressOnlyWriteRequestedFields() = runTest {
        val requests = mutableListOf<Pair<HttpMethod, String>>()
        val client = HttpClient(MockEngine { request ->
            assertEquals("Bearer token", request.headers["Authorization"])
            assertEquals("/v0/users/-/collections/355555", request.url.encodedPath)
            requests += request.method to (request.body as TextContent).text
            respond("", HttpStatusCode.NoContent)
        })
        try {
            val repository = MangaLibraryRepository(client.asScopedHttpClient())
            repository.setType(355555, "token", MangaCollectionType.DOING)
            repository.setProgress(355555, "token", 12, 2)
            assertEquals(HttpMethod.Post, requests[0].first)
            assertEquals("{\"type\":3}", requests[0].second)
            assertEquals(HttpMethod.Patch, requests[1].first)
            assertEquals(setOf("ep_status", "vol_status"), Json.parseToJsonElement(requests[1].second).jsonObject.keys)
        } finally { client.close() }
    }

    @Test fun authorizationFailureDoesNotInvalidateCollectionLists() = runTest {
        val client = HttpClient(MockEngine { respond("{}", HttpStatusCode.Unauthorized) })
        val revision = MangaLibraryChanges.revision.value
        try {
            assertFailsWith<MangaAuthorizationException> { MangaLibraryRepository(client.asScopedHttpClient()).setType(1, "expired", MangaCollectionType.DONE) }
            assertEquals(revision, MangaLibraryChanges.revision.value)
        } finally { client.close() }
    }

    @Test fun missingCollectionDiffersFromFailure() = runTest {
        val client = HttpClient(MockEngine { request ->
            if (request.url.encodedPath == "/v0/me") respond("{\"username\":\"reader\"}")
            else { assertEquals("/v0/users/reader/collections/1", request.url.encodedPath); respond("{}", HttpStatusCode.NotFound) }
        })
        try { assertNull(MangaLibraryRepository(client.asScopedHttpClient()).read(1, "token")) }
        finally { client.close() }
    }
}
