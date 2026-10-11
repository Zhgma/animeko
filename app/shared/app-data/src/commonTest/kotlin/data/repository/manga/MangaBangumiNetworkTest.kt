/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.manga

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.models.preference.ProxyConfig
import me.him188.ani.app.data.models.preference.ProxyMode
import me.him188.ani.utils.ktor.asScopedHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MangaBangumiNetworkTest {
    private fun preferences() = object : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(data.value).also { data.value = it }
    }

    @Test fun proxyIsIndependentAndPersists() = runTest {
        val data = preferences()
        val settings = MangaBangumiSettingsStore(data)
        val initial = settings.flow.first()
        assertEquals(ProxyMode.DISABLED, initial.default.mode)
        settings.save(initial.copy(default = initial.default.copy(mode = ProxyMode.CUSTOM,
            customConfig = ProxyConfig(url = "http://127.0.0.1:7890"))))
        assertEquals("http://127.0.0.1:7890", MangaBangumiSettingsStore(data).flow.first().default.customConfig.url)
        assertEquals(1, data.data.value.asMap().size)
    }

    @Test fun persistedCollectionAndDetailsRemainUsableWhenBangumiFails() = runTest {
        val data = preferences()
        var offline = false
        val client = HttpClient(MockEngine { request ->
            if (offline) error("Network disconnected")
            respond(when (request.url.encodedPath) {
                "/v0/me" -> """{"username":"alice"}"""
                "/v0/users/alice/collections" -> """{"total":1,"data":[{"subject_id":118165,"subject_type":1,"subject":{"id":118165,"name":"Grand Blue","name_cn":"碧蓝之海"},"ep_status":3,"vol_status":0,"type":3,"rate":8}]}"""
                "/v0/subjects/118165" -> """{"type":1,"platform":"漫画","name":"Grand Blue","name_cn":"碧蓝之海","images":{},"rating":{},"infobox":[],"tags":[],"collection":{}}"""
                else -> error("Unexpected request")
            })
        })
        try {
            val online = MangaBangumiHttp(client.asScopedHttpClient(), MangaBangumiCache(data))
            BangumiMangaRepository(online).loadCollections("secret-token", MangaCollectionType.DOING) { _, _ -> }
            assertEquals("碧蓝之海", MangaDetailsRepository(online).details(118165, "secret-token").title)
            offline = true
            val cacheAfterRestart = MangaBangumiCache(data)
            val cached = MangaBangumiHttp(client.asScopedHttpClient(), cacheAfterRestart)
            var count = 0
            BangumiMangaRepository(cached).loadCollections("secret-token", MangaCollectionType.DOING) { _, items -> count = items.size }
            assertEquals(1, count)
            assertEquals("碧蓝之海", MangaDetailsRepository(cached).details(118165, "secret-token").title)
            assertTrue(cacheAfterRestart.usingCache.value)
            assertTrue(data.data.value.toString().contains("secret-token").not())
            assertFailsWith<IllegalStateException> { MangaDetailsRepository(cached).details(118165, "other-account") }
        } finally { client.close() }
    }

    @Test fun authFailureAndCancellationAreNotReplacedByCacheAndRecoveryUpdatesIt() = runTest {
        val cache = MangaBangumiCache(preferences())
        cache.read("account", "GET metadata") { MangaBangumiResponse(200, "old") }
        assertEquals(401, cache.read("account", "GET metadata") { MangaBangumiResponse(401, "unauthorized") }.status)
        assertFailsWith<CancellationException> {
            cache.read("account", "GET metadata") { throw CancellationException() }
        }
        assertEquals("old", cache.read("account", "GET metadata") { MangaBangumiResponse(503, "down") }.body)
        cache.retry()
        assertEquals("new", cache.read("account", "GET metadata") { MangaBangumiResponse(200, "new") }.body)
    }

    @Test fun failedWritesNeverUseCacheOrNotifyAndSuccessfulWritesPreserveUpdatedCollections() = runTest {
        val cache = MangaBangumiCache(preferences())
        cache.read("account", "GET /collections") { MangaBangumiResponse(200, """{"data":[{"subject_id":1,"ep_status":0}]}""") }
        var status = HttpStatusCode.ServiceUnavailable
        val client = HttpClient(MockEngine { respond("{}", status) })
        try {
            val repo = MangaLibraryRepository(MangaBangumiHttp(client.asScopedHttpClient(), cache))
            val revision = MangaLibraryChanges.revision.value
            assertFailsWith<IllegalStateException> { repo.setProgress(1, "account", 2, 0) }
            assertEquals(revision, MangaLibraryChanges.revision.value)
            status = HttpStatusCode.OK
            repo.setProgress(1, "account", 2, 0)
            assertEquals(revision + 1, MangaLibraryChanges.revision.value)
            assertTrue(cache.read("account", "GET /collections") { error("offline") }.body.contains("\"ep_status\":2"))
        } finally { client.close() }
    }
}
