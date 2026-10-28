/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.manga

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.him188.ani.utils.ktor.ScopedHttpClient
import kotlin.time.Duration.Companion.hours

@Serializable
data class RelaySettings(
    val manualOrigin: String = "",
    val subscriptionUrl: String = DEFAULT_RELAY_SUBSCRIPTION,
    val subscribedOrigin: String = DEFAULT_RELAY_ORIGIN,
    val previousOrigins: List<String> = emptyList(),
) {
    val effectiveOrigin: String get() = manualOrigin.ifBlank { subscribedOrigin }
}

@Serializable
data class RelaySubscription(val version: Int, val origin: String)

class RelaySettingsStore(private val store: DataStore<Preferences>, private val client: ScopedHttpClient) {
    private val key = stringPreferencesKey("network.relay.v1")
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private fun decode(prefs: Preferences) = prefs[key]?.let {
        runCatching { json.decodeFromString<RelaySettings>(it) }.getOrNull()
    } ?: RelaySettings()
    val flow = store.data.map(::decode).distinctUntilChanged()

    suspend fun initialize() { flow.first().let { RelayEndpoints.apply(it.effectiveOrigin, it.previousOrigins) } }

    suspend fun save(manualOrigin: String, subscriptionUrl: String) = mutex.withLock {
        val manual = manualOrigin.trim().takeIf { it.isNotEmpty() }?.let(::validateRelayOrigin).orEmpty()
        val subscription = validateSubscriptionUrl(subscriptionUrl)
        update { it.copy(manualOrigin = manual, subscriptionUrl = subscription) }
    }

    private suspend fun update(transform: (RelaySettings) -> RelaySettings) {
        val result = store.edit { prefs ->
            val old = decode(prefs)
            val updated = transform(old)
            val history = (old.previousOrigins + old.effectiveOrigin).distinct().takeLast(20)
            prefs[key] = json.encodeToString(updated.copy(previousOrigins = history))
        }
        decode(result).let { RelayEndpoints.apply(it.effectiveOrigin, it.previousOrigins) }
    }

    /** Configuration fetches never carry account credentials. Failed refreshes retain the last address. */
    suspend fun refresh(): String = mutex.withLock {
        val before = flow.first()
        val contents = client.use {
            val response = get(validateSubscriptionUrl(before.subscriptionUrl)) {
                expectSuccess = false
                timeout { requestTimeoutMillis = 15_000 }
            }
            check(response.status.value in 200..299) { "配置订阅下载失败：HTTP ${response.status.value}" }
            response.bodyAsText().also { require(it.length <= 16_384) { "配置订阅过大" } }
        }
        val remote = json.decodeFromString<RelaySubscription>(contents)
        require(remote.version == 1) { "不支持此配置订阅版本" }
        val origin = validateRelayOrigin(remote.origin)
        update { it.copy(subscribedOrigin = origin) }
        origin
    }

    suspend fun runAutoRefresh() {
        while (currentCoroutineContext().isActive) {
            try { if (flow.first().manualOrigin.isBlank()) refresh() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Keep the last usable configuration until the next refresh. */ }
            delay(1.hours)
        }
    }

    companion object {
        fun validateSubscriptionUrl(value: String): String {
            val text = MangaSubscriptionParser.validateUrl(value)
            require(Url(text).protocol.name == "https") { "配置订阅必须使用 HTTPS" }
            return text
        }
    }
}
