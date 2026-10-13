/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.manga

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

internal const val BOOKS_API_BASE_URL = "https://43.131.255.74:8443/api/books"

@Serializable
internal data class MangaRequestRoute(val url: String, val sendAuthorization: Boolean, val timeoutMillis: Long)

/** Shared routing and credential policy; transport and cache belong to each platform. */
internal object MangaRequestRouting {
    fun resolve(method: String, url: String, body: String? = null, booksApiBaseUrl: String? = BOOKS_API_BASE_URL): MangaRequestRoute {
        val relationsRequest = booksApiBaseUrl != null && method == "GET" && BOOK_RELATIONS_URL.matches(url)
        val commentsRequest = booksApiBaseUrl != null && method == "GET" && BOOK_COMMENTS_URL.matches(url)
        val publicBookRequest = booksApiBaseUrl != null && (
            commentsRequest || relationsRequest || (method == "GET" && BOOK_DETAILS_URL.matches(url)) ||
                (method == "POST" && url == "https://api.bgm.tv/v0/search/subjects")
            )
        val accountRequest = booksApiBaseUrl != null && (
            (method == "GET" && (url == "https://api.bgm.tv/v0/me" || BOOK_COLLECTION_URL.matches(url))) ||
                (method in listOf("POST", "PATCH") && BOOK_COLLECTION_WRITE_URL.matches(url) &&
                    isCollectionStateWrite(method, body))
            )
        require(booksApiBaseUrl == null || publicBookRequest || accountRequest) { "书籍 API 不支持此请求" }
        val target = when {
            commentsRequest -> booksApiBaseUrl + url.removePrefix("https://next.bgm.tv")
            publicBookRequest || accountRequest -> booksApiBaseUrl + url.removePrefix("https://api.bgm.tv")
            else -> url
        }
        return MangaRequestRoute(target, !publicBookRequest, if (accountRequest || commentsRequest || relationsRequest) 20_000 else 8_000)
    }


    private val BOOK_COMMENTS_URL = Regex("https://next\\.bgm\\.tv/p1/subjects/[1-9][0-9]*/comments")
    private val BOOK_RELATIONS_URL = Regex("https://api\\.bgm\\.tv/v0/subjects/[1-9][0-9]*/subjects")
    private val BOOK_DETAILS_URL = Regex("https://api\\.bgm\\.tv/v0/subjects/[1-9][0-9]*")
    private val BOOK_COLLECTION_URL = Regex("https://api\\.bgm\\.tv/v0/users/[A-Za-z0-9_-]+/collections(?:/[1-9][0-9]*)?")
    private val BOOK_COLLECTION_WRITE_URL = Regex("https://api\\.bgm\\.tv/v0/users/-/collections/[1-9][0-9]*")

    private fun isCollectionStateWrite(method: String, body: String?): Boolean {
        val fields = runCatching { Json.parseToJsonElement(body.orEmpty()).jsonObject.keys }.getOrNull() ?: return false
        val allowed = if (method == "POST") setOf("type") else setOf("type", "ep_status", "vol_status", "rate", "comment", "private")
        return fields.isNotEmpty() && fields.all { it in allowed }
    }
}
