/*
 * Copyright (C) 2026 OpenAni and contributors.
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package me.him188.ani.app.data.repository.manga

import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import me.him188.ani.utils.ktor.ScopedHttpClient

/** Reads manga collections through the native HTTP transport. */
class BangumiMangaRepository(private val client: ScopedHttpClient) {
    private val loader = BangumiMangaCollectionLoader(::request)

    suspend fun loadCollections(
        accessToken: String,
        type: MangaCollectionType,
        onPage: (username: String, collections: List<MangaCollection>) -> Unit,
    ) = loader.loadCollections(accessToken, type, onPage)

    private suspend fun request(path: String, token: String, type: Int? = null, offset: Int = 0): String = client.use {
        val response = get("https://api.bgm.tv/v0/$path") {
            expectSuccess = false
            bearerAuth(token)
            if (type != null) {
                parameter("subject_type", 1)
                parameter("type", type)
                parameter("limit", 30)
                parameter("offset", offset)
            }
        }
        when (response.status.value) {
            401, 403 -> throw MangaAuthorizationException()
            in 200..299 -> response.bodyAsText()
            else -> throw MangaHttpException(response.status.value)
        }
    }
}
