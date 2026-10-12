/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.manga

import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.him188.ani.app.domain.mediasource.subscription.SubscriptionUpdateData
import me.him188.ani.datasources.api.source.FactoryId
import me.him188.ani.utils.ktor.ScopedHttpClient
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import me.him188.ani.utils.platform.Uuid
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@Serializable
data class MangaSubscription(
    val id: String,
    val name: String,
    val url: String,
    val enabled: Boolean = true,
    val sources: List<MangaSource> = emptyList(),
    val unsupported: List<String> = emptyList(),
    val updatedAt: Long? = null,
    val error: String? = null,
    val lastAttemptAt: Long? = null,
    val sourceLinks: Map<MangaSource, String> = emptyMap(),
) {
    companion object {
        const val REPOSITORY_ID = "animecomic-manga-sub"
        const val REPOSITORY_URL = "https://raw.githubusercontent.com/Zhgma/animeko/main/subs/manga.json"

        fun defaults() = listOf(MangaSubscription(
            REPOSITORY_ID, "Animecomic 漫画源", REPOSITORY_URL,
            sources = MangaSource.maintained,
            sourceLinks = MangaSource.maintained.associateWith { it.subscriptionUrl },
        ))
    }
}

data class MangaSubscriptionContents(
    val sources: List<MangaSource>,
    val unsupported: List<String>,
    val sourceLinks: Map<MangaSource, String> = emptyMap(),
)

@Serializable
private data class MangaNativeSourceArguments(val name: String, val key: String, val url: String)

/** Imports source identities; site protocols are implemented by the native adapters. */
object MangaSubscriptionParser {
    private val json = Json { ignoreUnknownKeys = true }

    private fun adapter(key: String) = when (key.lowercase()) {
        "ccc" -> MangaSource.CCC
        "comick" -> MangaSource.COMICK
        "komiic" -> MangaSource.KOMIIC
        "manhuagui" -> MangaSource.MANHUAGUI
        "baozi" -> MangaSource.BAOZI
        "goda" -> MangaSource.GODA
        "ykmh" -> MangaSource.YKMH
        "manhuaren" -> MangaSource.MANHUAREN
        else -> null
    }

    private fun parseSubscription(text: String): MangaSubscriptionContents {
        val entries = json.decodeFromString<SubscriptionUpdateData>(text).exportedMediaSourceDataList.mediaSources
        require(entries.size <= 500) { "订阅索引条目过多" }
        val links = linkedMapOf<MangaSource, String>()
        val unsupported = mutableListOf<String>()
        for (entry in entries) {
            if (entry.factoryId != FactoryId("manga-native") || entry.version != 1) {
                unsupported += "${entry.factoryId} v${entry.version}"
                continue
            }
            val args = json.decodeFromJsonElement(MangaNativeSourceArguments.serializer(), entry.arguments)
            require(args.name.isNotBlank() && args.key.isNotBlank()) { "漫画源缺少 name 或 key" }
            val url = validateUrl(args.url)
            val source = adapter(args.key)
            if (source == null) unsupported += args.key else {
                require(source !in links) { "订阅中存在重复漫画源：${args.key}" }
                links[source] = url
            }
        }
        // An explicitly empty list removes remote sources; an incompatible list preserves cached sources.
        require(entries.isEmpty() || links.isNotEmpty()) { "订阅中没有本版本支持的漫画源" }
        return MangaSubscriptionContents(links.keys.toList(), unsupported.distinct(), links)
    }

    fun validateUrl(value: String): String {
        val text = value.trim()
        val url = runCatching { Url(text) }.getOrNull()
        require(Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(text) &&
            text.none { it == '\r' || it == '\n' } &&
            url != null && url.protocol.name in setOf("https", "http") && url.host.isNotBlank() &&
            url.user.isNullOrEmpty() && url.password.isNullOrEmpty() && url.fragment.isEmpty()) {
            "请输入有效的 HTTP 或 HTTPS 订阅链接（不含账号密码或片段）"
        }
        return url.toString()
    }

    fun parse(text: String): MangaSubscriptionContents {
        require(text.length <= 2 * 1024 * 1024) { "订阅文件超过 2 MB" }
        val trimmed = text.trim().removePrefix("\uFEFF").trim()
        if (trimmed.startsWith("{")) return parseSubscription(trimmed)
        val keys = if (trimmed.startsWith("[")) {
            val array = runCatching { Json.parseToJsonElement(trimmed) as? JsonArray }.getOrNull()
                ?: error("订阅索引 JSON 格式不正确")
            require(array.size <= 500) { "订阅索引条目过多" }
            array.map { entry ->
                val item = entry as? JsonObject ?: error("订阅条目格式不正确")
                val key = (item["key"] as? JsonPrimitive)?.content
                require(!key.isNullOrBlank()) { "订阅条目缺少 key" }
                key
            }
        } else {
            require(Regex("class\\s+\\w+\\s+extends\\s+ComicSource\\s*\\{").containsMatchIn(trimmed)) {
                "不支持此订阅格式，请使用漫画 sub、Venera 的 index.json 或单个漫画源 .js 链接"
            }
            val key = Regex("(?m)^\\s*key\\s*=\\s*[\"']([^\"'\\r\\n]+)[\"']\\s*;?\\s*$")
                .find(trimmed)?.groupValues?.get(1) ?: error("漫画源脚本缺少可识别的 key")
            listOf(key)
        }
        val sources = keys.mapNotNull(::adapter).distinct()
        require(sources.isNotEmpty()) { "订阅中没有本版本支持的漫画源；目前支持 Komiic、漫画柜、G站漫画、漫画人、优酷漫画、CCC追漫台和 Comick（兼容手动导入包子漫画）" }
        return MangaSubscriptionContents(sources, keys.filter { adapter(it) == null }.distinct())
    }
}

class MangaSubscriptionRepository(
    private val store: MangaSourceSettingsStore,
    private val client: ScopedHttpClient,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val refreshMutex = Mutex()

    /** Same one-hour default period and application-scope lifetime as anime subscriptions. */
    suspend fun runAutoRefresh() {
        while (currentCoroutineContext().isActive) {
            val nextDelay = try {
                updateAllOutdated()
                1.hours
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger<MangaSubscriptionRepository>().warn(e) { "Failed to update manga subscriptions" }
                10.minutes
            }
            delay(nextDelay)
        }
    }

    suspend fun updateAllOutdated() {
        for (subscription in store.read().subscriptions) refresh(subscription, onlyIfOutdated = true)
    }

    private suspend fun fetch(url: String): MangaSubscriptionContents = client.use {
        val response = get(url) {
            expectSuccess = false
            timeout { requestTimeoutMillis = 20_000 }
        }
        check(response.status.value in 200..299) { "订阅下载失败：HTTP ${response.status.value}" }
        MangaSubscriptionParser.parse(response.bodyAsText())
    }

    /** A failed edit preserves both the URL and the previously imported sources. */
    suspend fun save(id: String?, name: String, rawUrl: String) {
        require(name.isNotBlank()) { "请输入订阅名称" }
        val url = MangaSubscriptionParser.validateUrl(rawUrl)
        require(store.read().subscriptions.none { it.id != id && it.url == url }) { "此订阅链接已存在" }
        val contents = fetch(url)
        store.update { current ->
            require(current.subscriptions.none { it.id != id && it.url == url }) { "此订阅链接已存在" }
            val old = id?.let { target -> current.subscriptions.find { it.id == target } ?: error("此订阅已被删除") }
            val saved = MangaSubscription(old?.id ?: Uuid.randomString(), name.trim(), url, old?.enabled ?: true,
                contents.sources, contents.unsupported, nowMillis(), sourceLinks = contents.sourceLinks)
            current.copy(subscriptions = if (old == null) current.subscriptions + saved
                else current.subscriptions.map { if (it.id == id) saved else it })
        }
    }

    suspend fun refresh(subscription: MangaSubscription) = refresh(subscription, onlyIfOutdated = false)

    private suspend fun refresh(subscription: MangaSubscription, onlyIfOutdated: Boolean) = refreshMutex.withLock {
        val latest = store.read().subscriptions.find { it.id == subscription.id && it.url == subscription.url }
            ?: return@withLock
        val attemptedAt = nowMillis()
        if (onlyIfOutdated) {
            val previous = latest.lastAttemptAt ?: latest.updatedAt
            if (!latest.enabled || previous != null && attemptedAt >= previous &&
                attemptedAt - previous < 1.hours.inWholeMilliseconds) return@withLock
        }
        val refreshed = try {
            val contents = fetch(MangaSubscriptionParser.validateUrl(subscription.url))
            latest.copy(sources = contents.sources, unsupported = contents.unsupported,
                updatedAt = nowMillis(), error = null, sourceLinks = contents.sourceLinks)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { latest.copy(error = e.message ?: "订阅更新失败") }
        store.update { current -> current.copy(subscriptions = current.subscriptions.map {
            // In-flight refreshes cannot overwrite an edit, deletion, or enable switch.
            if (it.id == subscription.id && it.url == subscription.url) it.copy(sources = refreshed.sources,
                unsupported = refreshed.unsupported, updatedAt = refreshed.updatedAt, error = refreshed.error,
                lastAttemptAt = attemptedAt, sourceLinks = refreshed.sourceLinks) else it
        }) }
    }

    suspend fun setEnabled(id: String, enabled: Boolean) = store.update { current ->
        current.copy(subscriptions = current.subscriptions.map { if (it.id == id) it.copy(enabled = enabled) else it })
    }

    suspend fun delete(id: String) = store.update { current ->
        current.copy(subscriptions = current.subscriptions.filterNot { it.id == id })
    }
}
