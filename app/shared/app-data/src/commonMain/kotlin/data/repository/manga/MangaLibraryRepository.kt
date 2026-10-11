/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.manga

import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import me.him188.ani.utils.ktor.ScopedHttpClient

data class MangaLibraryEntry(val type: MangaCollectionType, val chapters: Int, val volumes: Int)

/** Invalidates the independent manga collection lists after a successful write. */
object MangaLibraryChanges {
    val revision = MutableStateFlow(0)
    fun notifyChanged() { revision.update { it + 1 } }
}

class MangaLibraryRepository(private val client: ScopedHttpClient) {
    suspend fun read(id: Int, token: String): MangaLibraryEntry? = client.use {
        val me = get("https://api.bgm.tv/v0/me") { bearerAuth(token); expectSuccess = true }
        val username = Json.parseToJsonElement(me.bodyAsText()).jsonObject.text("username")
        check(username.isNotBlank())
        val response = get("https://api.bgm.tv/v0/users/${username.encodeURLPathPart()}/collections/$id") {
            bearerAuth(token); expectSuccess = false
        }
        if (response.status.value == 404) return@use null
        check(response.status.value in 200..299) { "收藏 HTTP ${response.status.value}" }
        val value = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        MangaLibraryEntry(MangaCollectionType.entries.first { it.bangumiId == value.number("type") },
            value.number("ep_status"), value.number("vol_status"))
    }

    /** Only the requested fields are submitted, preserving review, privacy and tags. */
    suspend fun setType(id: Int, token: String, type: MangaCollectionType) = client.use {
        val response = post("https://api.bgm.tv/v0/users/-/collections/$id") {
            bearerAuth(token); expectSuccess = false; contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("type", type.bangumiId) }.toString())
        }
        check(response.status.value in 200..299) { "修改收藏 HTTP ${response.status.value}" }
        MangaLibraryChanges.notifyChanged()
    }

    suspend fun setProgress(id: Int, token: String, chapters: Int, volumes: Int) = client.use {
        require(chapters >= 0 && volumes >= 0)
        val response = patch("https://api.bgm.tv/v0/users/-/collections/$id") {
            bearerAuth(token); expectSuccess = false; contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("ep_status", chapters); put("vol_status", volumes) }.toString())
        }
        check(response.status.value in 200..299) { "更新进度 HTTP ${response.status.value}" }
        MangaLibraryChanges.notifyChanged()
    }
}
