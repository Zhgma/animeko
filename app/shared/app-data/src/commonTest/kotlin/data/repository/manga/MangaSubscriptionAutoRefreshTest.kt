/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.manga

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import me.him188.ani.utils.ktor.asScopedHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

@OptIn(ExperimentalCoroutinesApi::class)
class MangaSubscriptionAutoRefreshTest {
    private fun preferences() = object : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(data.value).also { data.value = it }
    }

    private fun sub(key: String = "goda", url: String = "https://sources.test/goda.js", version: Int = 1) = """
        {"exportedMediaSourceDataList":{"mediaSources":[
          {"factoryId":"manga-native","version":$version,"arguments":{"name":"Source","key":"$key","url":"$url"}}
        ]}}
    """

    @Test
    fun parsesAnimeEnvelopeWithMangaArgumentsAndRejectsIncompatibleOrUnsafeEntries() {
        val result = MangaSubscriptionParser.parse(sub())
        assertEquals(listOf(MangaSource.GODA), result.sources)
        assertEquals("https://sources.test/goda.js", result.sourceLinks[MangaSource.GODA])
        for (text in listOf(sub(version = 2), sub(key = "manga_dex"), sub(url = "file:///source.js"),
            sub().replace("manga-native", "web-selector"), sub().replace("\"key\":\"goda\",", ""))) {
            assertFails { MangaSubscriptionParser.parse(text) }
        }
        assertTrue(MangaSubscriptionParser.parse("""{"exportedMediaSourceDataList":{"mediaSources":[]}}""").sources.isEmpty())
    }

    @Test
    fun refreshesAtStartupAndHourlyAndRetainsCacheOnFailureWithoutImmediateRetries() = runTest {
        val data = preferences()
        val store = MangaSourceSettingsStore(data)
        var calls = 0
        var fail = false
        val client = HttpClient(MockEngine) {
            engine {
                dispatcher = StandardTestDispatcher(testScheduler)
                addHandler {
                    calls++
                    if (fail) respond("offline", HttpStatusCode.ServiceUnavailable) else respond(sub())
                }
            }
        }
        try {
            val repo = MangaSubscriptionRepository(store, client.asScopedHttpClient()) { testScheduler.currentTime }
            val job = backgroundScope.launch { repo.runAutoRefresh() }
            runCurrent()
            assertEquals(1, calls)
            val cached = store.read().subscriptions.single()
            assertEquals(listOf(MangaSource.GODA), cached.sources)
            assertEquals("https://sources.test/goda.js", store.read().subscriptionUrl(MangaSource.GODA))
            advanceTimeBy(1.hours.inWholeMilliseconds - 1)
            runCurrent()
            assertEquals(1, calls)
            fail = true
            advanceTimeBy(1)
            runCurrent()
            assertEquals(2, calls)
            val failed = MangaSourceSettingsStore(data).read().subscriptions.single()
            assertEquals(cached.sources, failed.sources)
            assertEquals(cached.updatedAt, failed.updatedAt)
            assertNotNull(failed.error)
            // A restart during the same hour does not immediately repeat a failed request.
            MangaSubscriptionRepository(store, client.asScopedHttpClient()) { testScheduler.currentTime }.updateAllOutdated()
            assertEquals(2, calls)
            repo.setEnabled(failed.id, false)
            advanceTimeBy(1.hours.inWholeMilliseconds)
            runCurrent()
            assertEquals(2, calls)
            repo.setEnabled(failed.id, true)
            fail = false
            repo.refresh(failed)
            assertEquals(3, calls)
            assertEquals(null, store.read().subscriptions.single().error)
            repo.delete(failed.id)
            advanceTimeBy(1.hours.inWholeMilliseconds)
            runCurrent()
            assertEquals(3, calls)
            job.cancel()
        } finally { client.close() }
    }

    @Test
    fun refreshRemovesSourcesAndPersistsNewLinksWithoutChangingLocalPreferences() = runTest {
        val store = MangaSourceSettingsStore(preferences())
        val before = store.read().copy(order = listOf(MangaSource.GODA, MangaSource.KOMIIC), disabled = setOf(MangaSource.GODA))
        store.save(before)
        var body = sub(url = "https://new.test/source.js")
        val client = HttpClient(MockEngine { respond(body) })
        try {
            val repo = MangaSubscriptionRepository(store, client.asScopedHttpClient())
            repo.refresh(before.subscriptions.single())
            assertEquals(before.order, store.read().order)
            assertEquals(before.disabled, store.read().disabled)
            assertEquals(listOf(MangaSource.GODA), store.read().all)
            assertEquals("https://new.test/source.js", store.read().subscriptionUrl(MangaSource.GODA))
            body = """{"exportedMediaSourceDataList":{"mediaSources":[]}}"""
            repo.refresh(before.subscriptions.single())
            assertTrue(store.read().all.isEmpty())
        } finally { client.close() }
    }

    @Test
    fun migratesBuiltinsOncePreservingDisabledDeletedAndEditedSources() = runTest {
        val data = preferences()
        data.edit { it[stringPreferencesKey("manga.sources.settings")] = """{
          "builtinsRevision":1,"order":["GODA","KOMIIC"],"disabled":["GODA"],"subscriptions":[
            {"id":"builtin-KOMIIC","name":"Komiic","url":"${MangaSource.KOMIIC.subscriptionUrl}","enabled":false,"sources":["KOMIIC"]},
            {"id":"builtin-GODA","name":"Goda","url":"${MangaSource.GODA.subscriptionUrl}","sources":["GODA"]},
            {"id":"builtin-MANHUAGUI","name":"Edited","url":"https://custom.test/edited.js","sources":["MANHUAGUI"]}
          ]} """ }
        val store = MangaSourceSettingsStore(data)
        val result = store.read()
        assertEquals(2, result.subscriptions.size)
        assertTrue(result.subscriptions.any { it.url == MangaSubscription.REPOSITORY_URL })
        assertEquals(listOf(MangaSource.MANHUAGUI), result.enabled)
        assertEquals(listOf(MangaSource.GODA, MangaSource.KOMIIC), result.order)
        assertEquals("https://custom.test/edited.js", result.subscriptions.last().url)
        store.update { it.copy(subscriptions = it.subscriptions.filterNot { sub -> sub.id == MangaSubscription.REPOSITORY_ID }) }
        assertFalse(store.read().subscriptions.any { it.id == MangaSubscription.REPOSITORY_ID })
        store.update { it.copy(subscriptions = emptyList()) }
        assertTrue(store.read().subscriptions.isEmpty())
    }
}
