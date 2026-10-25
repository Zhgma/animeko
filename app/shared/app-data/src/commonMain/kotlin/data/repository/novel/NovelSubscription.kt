/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.novel

import me.him188.ani.app.data.repository.media.DefaultMediaSubscriptions
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import me.him188.ani.utils.ktor.ScopedHttpClient
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import me.him188.ani.utils.platform.Uuid
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@Serializable
data class NovelSubscription(
    val id: String,
    val name: String,
    val url: String,
    val enabled: Boolean = true,
    val sources: List<NovelSourceDefinition> = emptyList(),
    val unsupported: List<String> = emptyList(),
    val updatedAt: Long? = null,
    val error: String? = null,
    val lastAttemptAt: Long? = null,
) {
    companion object {
        const val REPOSITORY_ID = "animecomic-novel-sub"
        const val REPOSITORY_URL = DefaultMediaSubscriptions.NOVEL

        fun defaults() = listOf(NovelSubscription(
            REPOSITORY_ID, "Animecomic 小说源", REPOSITORY_URL,
            sources = DefaultNovelSources.all,
        ))
    }
}

data class NovelSubscriptionContents(val sources: List<NovelSourceDefinition>, val unsupported: List<String>)

class NovelSubscriptionRepository(
    private val store: NovelSourceSettingsStore,
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
                logger<NovelSubscriptionRepository>().warn(e) { "Failed to update novel subscriptions" }
                10.minutes
            }
            delay(nextDelay)
        }
    }

    suspend fun updateAllOutdated() {
        for (subscription in store.read().subscriptions) refresh(subscription, onlyIfOutdated = true)
    }

    private suspend fun fetch(url: String): NovelSubscriptionContents = client.use {
        val response = get(url) {
            expectSuccess = false
            timeout { requestTimeoutMillis = 20_000 }
        }
        check(response.status.value in 200..299) { "订阅下载失败：HTTP ${response.status.value}" }
        NovelSubscriptionParser.parse(response.bodyAsText())
    }

    /** A failed edit preserves both the URL and the previously imported sources. */
    suspend fun save(id: String?, name: String, rawUrl: String) {
        require(name.isNotBlank()) { "请输入订阅名称" }
        val url = NovelSubscriptionParser.validateUrl(rawUrl)
        require(store.read().subscriptions.none { it.id != id && it.url == url }) { "此订阅链接已存在" }
        val contents = fetch(url)
        store.update { current ->
            require(current.subscriptions.none { it.id != id && it.url == url }) { "此订阅链接已存在" }
            val old = id?.let { target -> current.subscriptions.find { it.id == target } ?: error("此订阅已被删除") }
            val saved = NovelSubscription(old?.id ?: Uuid.randomString(), name.trim(), url, old?.enabled ?: true,
                contents.sources, contents.unsupported, nowMillis())
            current.copy(subscriptions = if (old == null) current.subscriptions + saved
                else current.subscriptions.map { if (it.id == id) saved else it })
        }
    }

    suspend fun refresh(subscription: NovelSubscription) = refresh(subscription, onlyIfOutdated = false)

    private suspend fun refresh(subscription: NovelSubscription, onlyIfOutdated: Boolean) = refreshMutex.withLock {
        val latest = store.read().subscriptions.find { it.id == subscription.id && it.url == subscription.url }
            ?: return@withLock
        val attemptedAt = nowMillis()
        if (onlyIfOutdated) {
            val previous = latest.lastAttemptAt ?: latest.updatedAt
            if (!latest.enabled || previous != null && attemptedAt >= previous &&
                attemptedAt - previous < 1.hours.inWholeMilliseconds) return@withLock
        }
        val refreshed = try {
            val contents = fetch(NovelSubscriptionParser.validateUrl(subscription.url))
            latest.copy(sources = contents.sources, unsupported = contents.unsupported,
                updatedAt = nowMillis(), error = null)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { latest.copy(error = e.message ?: "订阅更新失败") }
        store.update { current -> current.copy(subscriptions = current.subscriptions.map {
            // In-flight refreshes cannot overwrite an edit, deletion, or enable switch.
            if (it.id == subscription.id && it.url == subscription.url) it.copy(sources = refreshed.sources,
                unsupported = refreshed.unsupported, updatedAt = refreshed.updatedAt, error = refreshed.error,
                lastAttemptAt = attemptedAt) else it
        }) }
    }

    suspend fun setEnabled(id: String, enabled: Boolean) = store.update { current ->
        current.copy(subscriptions = current.subscriptions.map { if (it.id == id) it.copy(enabled = enabled) else it })
    }

    suspend fun delete(id: String) = store.update { current ->
        current.copy(subscriptions = current.subscriptions.filterNot { it.id == id })
    }
}
