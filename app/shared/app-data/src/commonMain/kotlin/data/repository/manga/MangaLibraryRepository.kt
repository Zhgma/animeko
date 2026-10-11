/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.manga

import io.ktor.client.request.get
import io.ktor.http.HttpMethod
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

class MangaLibraryRepository(private val http: MangaBangumiHttp) {
    constructor(client: ScopedHttpClient) : this(MangaBangumiHttp(client))
    suspend fun read(id: Int, token: String): MangaLibraryEntry? {
        val me = http.get("https://api.bgm.tv/v0/me", token).requireSuccess()
        val username = Json.parseToJsonElement(me).jsonObject.text("username")
        check(username.isNotBlank())
        val response = http.get("https://api.bgm.tv/v0/users/${username.encodeURLPathPart()}/collections/$id", token)
        if (response.status == 404) return null
        val value = Json.parseToJsonElement(response.requireSuccess()).jsonObject
        return MangaLibraryEntry(MangaCollectionType.entries.first { it.bangumiId == value.number("type") },
            value.number("ep_status"), value.number("vol_status"))
    }

    suspend fun setType(id: Int, token: String, type: MangaCollectionType) {
        http.write(HttpMethod.Post, "https://api.bgm.tv/v0/users/-/collections/$id", token,
            buildJsonObject { put("type", type.bangumiId) }.toString())
        MangaLibraryChanges.notifyChanged()
    }

    suspend fun setProgress(id: Int, token: String, chapters: Int, volumes: Int) {
        require(chapters >= 0 && volumes >= 0)
        http.write(HttpMethod.Patch, "https://api.bgm.tv/v0/users/-/collections/$id", token,
            buildJsonObject { put("ep_status", chapters); put("vol_status", volumes) }.toString())
        MangaLibraryChanges.notifyChanged()
    }
}
