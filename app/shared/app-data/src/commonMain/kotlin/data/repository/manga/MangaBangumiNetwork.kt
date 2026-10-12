/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.manga

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.timeout
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.request
import io.ktor.client.request.parameter
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import me.him188.ani.app.data.models.preference.ProxySettings
import me.him188.ani.app.domain.foundation.DefaultHttpClientProvider
import me.him188.ani.app.domain.foundation.ScopedHttpClientUserAgent
import me.him188.ani.app.domain.foundation.withValue
import me.him188.ani.app.domain.foundation.UserAgentFeature
import me.him188.ani.app.domain.settings.ProxySettingsFlowProxyProvider
import me.him188.ani.utils.io.DigestAlgorithm
import me.him188.ani.utils.io.digest
import me.him188.ani.utils.ktor.ScopedHttpClient
import me.him188.ani.utils.platform.currentTimeMillis

class MangaBangumiSettingsStore(private val store: DataStore<Preferences>) {
    private val key = stringPreferencesKey("manga.bangumi.proxy")
    private val json = Json { ignoreUnknownKeys = true }
    val flow = store.data.map { preferences ->
        preferences[key]?.let { runCatching { json.decodeFromString<ProxySettings>(it) }.getOrNull() }
            ?: ProxySettings.Disabled
    }.distinctUntilChanged()
    suspend fun save(settings: ProxySettings) { store.edit { it[key] = json.encodeToString(settings) } }
}

/** Owns only manga Bangumi API traffic. Source subscriptions and reader images use their own clients. */
class MangaBangumiNetwork(store: DataStore<Preferences>, scope: CoroutineScope) {
    val settings = MangaBangumiSettingsStore(store)
    private val features = setOf(UserAgentFeature.withValue(ScopedHttpClientUserAgent.ANI))
    private val provider = DefaultHttpClientProvider(ProxySettingsFlowProxyProvider(settings.flow, scope), scope)
    val cache = MangaBangumiCache(store)
    val http = MangaBangumiHttp(provider.get(features), cache, BOOKS_API_BASE_URL)
    suspend fun start() {
        provider.startProxyListening(sequenceOf(DefaultHttpClientProvider.HoldingInstanceMatrix(features)))
    }
}

@Serializable
internal data class MangaBangumiResponse(val status: Int, val body: String, val savedAt: Long = currentTimeMillis()) {
    fun requireSuccess(): String {
        if (status == 401 || status == 403) throw MangaAuthorizationException()
        check(status in 200..299) { "Bangumi HTTP $status" }
        return body
    }
}

/** Persistent successful reads, partitioned by credential digest; tokens are never stored in cache keys. */
class MangaBangumiCache(private val store: DataStore<Preferences>) {
    private val key = stringPreferencesKey("manga.bangumi.responses.v1")
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val unavailableUntil = mutableMapOf<String, Long>()
    val usingCache = MutableStateFlow(false)
    private fun account(token: String?) = ByteString(token.orEmpty().encodeToByteArray()).digest(DigestAlgorithm.SHA256).toHexString()
    private fun decode(prefs: Preferences): Map<String, MangaBangumiResponse> = prefs[key]?.let {
        runCatching { json.decodeFromString<Map<String, MangaBangumiResponse>>(it) }.getOrNull()
    }.orEmpty()

    internal suspend fun read(token: String?, requestKey: String, fetch: suspend () -> MangaBangumiResponse): MangaBangumiResponse {
        val account = account(token)
        val id = "$account|$requestKey"
        val cached = try { decode(store.data.first())[id] }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { null }
        val coolingDown = mutex.withLock { (unavailableUntil[account] ?: 0) > currentTimeMillis() }
        if (coolingDown && cached != null) { usingCache.value = true; return cached }
        val response = try { fetch() } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            mutex.withLock { unavailableUntil[account] = currentTimeMillis() + 30_000 }
            if (cached == null) throw e
            usingCache.value = true
            return cached
        }
        if (response.status == 429 || response.status >= 500) {
            mutex.withLock { unavailableUntil[account] = currentTimeMillis() + 30_000 }
            if (cached != null) { usingCache.value = true; return cached }
        }
        // Auth failures and deleted entries must not masquerade as an offline success.
        if (response.status in 200..299 || response.status == 404) {
            mutex.withLock { unavailableUntil.remove(account) }
            try {
                store.edit { prefs ->
                    val entries = decode(prefs).toMutableMap()
                    entries[id] = response
                    var remainingCharacters = 4_000_000
                    val bounded = entries.entries.sortedByDescending { it.value.savedAt }.take(400)
                        .filter { entry ->
                            val length = entry.value.body.length
                            (length <= 256_000 && length <= remainingCharacters).also { if (it) remainingCharacters -= length }
                        }.associate { it.toPair() }
                    prefs[key] = json.encodeToString(bounded)
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* A storage failure must not discard a successful network response. */ }
        }
        return response
    }

    internal suspend fun recordCollectionWrite(token: String?, subjectId: String, body: String) {
        val prefix = account(token) + "|"
        val fields = Json.parseToJsonElement(body).jsonObject
        store.edit { prefs ->
            val entries = decode(prefs).mapValues { (id, response) ->
                if (!id.startsWith(prefix) || "/collections" !in id) return@mapValues response
                val root = runCatching { Json.parseToJsonElement(response.body).jsonObject }.getOrNull()
                    ?: return@mapValues response
                val updated = if ("/collections/$subjectId " in id && (response.status in 200..299 || "type" in fields)) {
                    JsonObject(root + fields)
                } else if (response.status in 200..299 && root["data"] is JsonArray) {
                    // Cached page membership describes the last fetched list; refresh reconciles category moves.
                    val rows = root.array("data").map { row ->
                        val collection = row.jsonObject
                        if (collection.text("subject_id") == subjectId) JsonObject(collection + fields.filterKeys { it != "type" }) else row
                    }
                    JsonObject(root + ("data" to JsonArray(rows)))
                } else return@mapValues response
                response.copy(status = 200, body = updated.toString())
            }
            prefs[key] = json.encodeToString(entries)
        }
        retry()
    }
    suspend fun retry() { mutex.withLock { unavailableUntil.clear() }; usingCache.value = false }
}

internal const val BOOKS_API_BASE_URL = "https://119.28.149.19:8443/api/books"

class MangaBangumiHttp(
    private val client: ScopedHttpClient,
    private val cache: MangaBangumiCache? = null,
    private val booksApiBaseUrl: String? = null,
) {
    internal suspend fun get(url: String, token: String? = null, parameters: Map<String, Any> = emptyMap()): MangaBangumiResponse =
        read(HttpMethod.Get, url, token, parameters)

    internal suspend fun search(body: String, token: String?, offset: Int, limit: Int): String =
        read(HttpMethod.Post, "https://api.bgm.tv/v0/search/subjects", token,
            mapOf("offset" to offset, "limit" to limit), body).requireSuccess()

    private suspend fun read(method: HttpMethod, url: String, token: String?, parameters: Map<String, Any>, body: String? = null): MangaBangumiResponse {
        val fetch: suspend () -> MangaBangumiResponse = { send(method, url, token, parameters, body) }
        return cache?.read(token, "${method.value} $url ${parameters.entries.sortedBy { it.key }} ${body.orEmpty()}", fetch) ?: fetch()
    }

    internal suspend fun write(method: HttpMethod, url: String, token: String, body: String) {
        send(method, url, token, emptyMap(), body).requireSuccess()
        // Server success remains success even if local cache update cannot be persisted.
        try { cache?.recordCollectionWrite(token, url.substringAfterLast('/'), body) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { }
    }

    suspend fun probe() {
        send(HttpMethod.Get, "https://api.bgm.tv/v0/subjects/118165", null, emptyMap(), null).requireSuccess()
        send(HttpMethod.Get, "https://next.bgm.tv/p1/subjects/118165/comments", null, mapOf("limit" to 1), null).requireSuccess()
        cache?.retry()
    }

    private suspend fun send(method: HttpMethod, url: String, token: String?, parameters: Map<String, Any>, body: String?): MangaBangumiResponse = client.use {
        val commentsRequest = booksApiBaseUrl != null && method == HttpMethod.Get && BOOK_COMMENTS_URL.matches(url)
        val publicBookRequest = booksApiBaseUrl != null && (
            commentsRequest || (method == HttpMethod.Get && BOOK_DETAILS_URL.matches(url)) ||
                (method == HttpMethod.Post && url == "https://api.bgm.tv/v0/search/subjects")
            )
        val accountRequest = booksApiBaseUrl != null && (
            (method == HttpMethod.Get && (url == "https://api.bgm.tv/v0/me" || BOOK_COLLECTION_URL.matches(url))) ||
                (method in listOf(HttpMethod.Post, HttpMethod.Patch) && BOOK_COLLECTION_WRITE_URL.matches(url) &&
                    isCollectionStateWrite(method, body))
            )
        val target = when {
            commentsRequest -> booksApiBaseUrl + url.removePrefix("https://next.bgm.tv")
            publicBookRequest || accountRequest -> booksApiBaseUrl + url.removePrefix("https://api.bgm.tv")
            else -> url
        }
        val response = request(target) {
            this.method = method
            expectSuccess = false
            timeout { requestTimeoutMillis = if (accountRequest || commentsRequest) 20_000 else 8_000; connectTimeoutMillis = 5_000 }
            if (!publicBookRequest && !token.isNullOrBlank()) bearerAuth(token)
            parameters.forEach { (key, value) -> parameter(key, value) }
            if (body != null) { contentType(ContentType.Application.Json); setBody(body) }
        }
        MangaBangumiResponse(response.status.value, response.bodyAsText())
    }

    private companion object {
        val BOOK_COMMENTS_URL = Regex("https://next\\.bgm\\.tv/p1/subjects/[1-9][0-9]*/comments")
        val BOOK_DETAILS_URL = Regex("https://api\\.bgm\\.tv/v0/subjects/[1-9][0-9]*")
        val BOOK_COLLECTION_URL = Regex("https://api\\.bgm\\.tv/v0/users/[A-Za-z0-9_-]+/collections(?:/[1-9][0-9]*)?")
        val BOOK_COLLECTION_WRITE_URL = Regex("https://api\\.bgm\\.tv/v0/users/-/collections/[1-9][0-9]*")

        fun isCollectionStateWrite(method: HttpMethod, body: String?): Boolean {
            val fields = runCatching { Json.parseToJsonElement(body.orEmpty()).jsonObject.keys }.getOrNull() ?: return false
            val allowed = if (method == HttpMethod.Post) setOf("type") else setOf("type", "ep_status", "vol_status")
            return fields.isNotEmpty() && fields.all { it in allowed }
        }
    }
}
