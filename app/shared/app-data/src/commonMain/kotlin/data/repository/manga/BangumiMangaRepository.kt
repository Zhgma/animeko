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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.him188.ani.utils.ktor.ScopedHttpClient

enum class MangaCollectionType(val bangumiId: Int, val webPath: String) {
    WISH(1, "wish"), DOING(3, "do"), DONE(2, "collect"), ON_HOLD(4, "on_hold"), DROPPED(5, "dropped"),
}

data class MangaCollection(
    val subjectId: Int,
    val title: String,
    val originalTitle: String,
    val coverUrl: String,
    val chaptersRead: Int,
    val volumesRead: Int,
    val totalChapters: Int,
    val totalVolumes: Int,
    val score: Int,
    val isPrivate: Boolean,
)

class MangaAuthorizationException : Exception("Bangumi authorization required")
class MangaHttpException(val statusCode: Int) : Exception("Bangumi HTTP $statusCode")

/** Reads book collections directly from Bangumi, independently of the anime collection database. */
class BangumiMangaRepository(private val client: ScopedHttpClient) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Bangumi paginates books before classifying manga. Advance by the unfiltered page size,
     * including pages containing only novels. Partial results can be displayed during loading.
     */
    suspend fun loadCollections(
        accessToken: String,
        type: MangaCollectionType,
        onPage: (username: String, collections: List<MangaCollection>) -> Unit,
    ) = coroutineScope {
        val user = json.decodeFromString<BangumiUser>(request("me", accessToken))
        val collections = linkedMapOf<Int, MangaCollection>()
        val permits = Semaphore(4)
        var offset = 0
        do {
            val page = json.decodeFromString<BangumiCollectionPage>(
                request("users/${user.username}/collections", accessToken, type.bangumiId, offset),
            )
            val mangas = page.data.map { collection ->
                async {
                    if (collection.subjectType != 1) return@async null
                    val subject = permits.withPermit {
                        json.decodeFromString<BangumiBook>(request("subjects/${collection.subjectId}", accessToken))
                    }
                    // Books also include novels, art books and other non-manga publications.
                    if (subject.type != 1 || subject.platform != "漫画") return@async null
                    MangaCollection(
                        subjectId = collection.subjectId,
                        title = subject.nameCn.ifBlank { subject.name },
                        originalTitle = subject.name,
                        coverUrl = subject.images.common.ifBlank { subject.images.large },
                        chaptersRead = collection.epStatus,
                        volumesRead = collection.volStatus,
                        totalChapters = subject.eps,
                        totalVolumes = subject.volumes,
                        score = collection.rate,
                        isPrivate = collection.isPrivate,
                    )
                }
            }.awaitAll().filterNotNull()
            mangas.forEach { collections[it.subjectId] = it }
            onPage(user.username, collections.values.toList())
            offset += page.data.size
        } while (page.data.isNotEmpty() && offset < page.total)
    }

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

@Serializable
private data class BangumiUser(val username: String)

@Serializable
private data class BangumiCollectionPage(
    val total: Int = 0,
    val data: List<BangumiBookCollection> = emptyList(),
)

@Serializable
private data class BangumiBookCollection(
    @SerialName("subject_id") val subjectId: Int,
    @SerialName("subject_type") val subjectType: Int,
    @SerialName("ep_status") val epStatus: Int = 0,
    @SerialName("vol_status") val volStatus: Int = 0,
    val rate: Int = 0,
    @SerialName("private") val isPrivate: Boolean = false,
)

@Serializable
private data class BangumiBook(
    val type: Int,
    val platform: String,
    val name: String,
    @SerialName("name_cn") val nameCn: String = "",
    val images: BangumiBookImages = BangumiBookImages(),
    val eps: Int = 0,
    val volumes: Int = 0,
)

@Serializable
private data class BangumiBookImages(val common: String = "", val large: String = "")
