/*
 * Copyright (C) 2026 OpenAni and contributors.
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package me.him188.ani.app.data.repository.manga

import io.ktor.client.request.get
import me.him188.ani.utils.ktor.ScopedHttpClient

/** Reads manga collections through the native HTTP transport. */
class BangumiMangaRepository(private val http: MangaBangumiHttp) {
    constructor(client: ScopedHttpClient) : this(MangaBangumiHttp(client))
    private val loader = BangumiMangaCollectionLoader(::request)

    suspend fun loadCollections(accessToken: String, type: MangaCollectionType,
        onPage: (username: String, collections: List<MangaCollection>) -> Unit,
    ) = loader.loadCollections(accessToken, type, onPage)

    private suspend fun request(path: String, token: String, type: Int? = null, offset: Int = 0): String {
        val response = http.get("https://api.bgm.tv/v0/$path", token, if (type == null) emptyMap() else
            mapOf("subject_type" to 1, "type" to type, "limit" to 30, "offset" to offset))
        return when (response.status) {
            401, 403 -> throw MangaAuthorizationException()
            in 200..299 -> response.body
            else -> throw MangaHttpException(response.status)
        }
    }
}
