/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.manga

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import me.him188.ani.utils.ktor.asScopedHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MangaSubscriptionTest {
    private fun preferences() = object : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(data.value).also { data.value = it }
    }

    @Test
    fun removesRetiredSourceAndAddsNewBuiltinsOnceWithoutResettingCustomSubscriptions() = runTest {
        val data = preferences()
        data.edit { it[stringPreferencesKey("manga.sources.settings")] = """{
            "order":["MANGADEX","GODA"],"disabled":["GODA"],"subscriptions":[
              {"id":"builtin-MANGADEX","name":"MangaDex","url":"https://example.test/dex.js","sources":["MANGADEX"]},
              {"id":"custom","name":"My source","url":"https://example.test/goda.js","sources":["GODA"]}
            ]}""" }
        val store = MangaSourceSettingsStore(data)
        val migrated = store.read()
        assertFalse(MangaSource.MANGADEX in migrated.all)
        assertEquals(setOf(MangaSource.MANHUAREN), migrated.enabled.toSet())
        assertTrue(MangaSource.YKMH in migrated.subscriptions.flatMap { it.sources })
        assertTrue(migrated.subscriptions.any { it.id == "custom" })
        assertTrue(MangaSource.GODA in migrated.disabled)
        assertTrue(migrated.subscriptions.any { it.url == MangaSubscription.REPOSITORY_URL })
        store.save(migrated.copy(subscriptions = migrated.subscriptions.filter { it.id != MangaSubscription.REPOSITORY_ID }))
        assertFalse(store.read().subscriptions.any { it.id == MangaSubscription.REPOSITORY_ID })
        store.save(MangaSourceSettings(subscriptions = emptyList()))
        assertTrue(store.read().all.isEmpty())
    }

    @Test
    fun readsIndexAndSingleSourceContentRatherThanTrustingTheFilename() {
        val index = MangaSubscriptionParser.parse("""[
            {"key":"Komiic","fileName":"komiic.js"}, {"key":"ManHuaGui"},
            {"key":"copy_manga"}, {"key":"Komiic"}, {"key":"manga_dex"}]
        """)
        assertEquals(listOf(MangaSource.KOMIIC, MangaSource.MANHUAGUI), index.sources)
        assertEquals(listOf("copy_manga", "manga_dex"), index.unsupported)
        assertEquals(listOf(MangaSource.GODA), MangaSubscriptionParser.parse("""
            class GoDa extends ComicSource {
                key = 'goda';
                search = () => { throw 'must not execute'; }
            }
        """).sources)
        for (body in listOf("<html>Login</html>", "[]", "[{\"key\":\"unknown\"}]", "[{\"name\":\"Komiic\"}]")) {
            assertFails { MangaSubscriptionParser.parse(body) }
        }
    }

    @Test
    fun rejectsInvalidUrlsAndMigratesExistingPreferencesWithoutLosingOrder() = runTest {
        for (url in listOf("file:///tmp/source.js", "javascript:alert(1)", "https://user:pass@host.test/a", "https://a.test/a#x")) {
            assertFailsWith<IllegalArgumentException> { MangaSubscriptionParser.validateUrl(url) }
        }
        val data = preferences()
        data.edit { it[stringPreferencesKey("manga.sources.settings")] = """{"order":["GODA","KOMIIC"],"disabled":["BAOZI"]}""" }
        val migrated = MangaSourceSettingsStore(data).read()
        assertEquals(MangaSubscription.REPOSITORY_URL, migrated.subscriptions.single().url)
        assertEquals(MangaSource.GODA, migrated.enabled.first())
        assertFalse(MangaSource.BAOZI in migrated.enabled)
        assertFalse(MangaSource.YKMH in migrated.enabled)
    }

    @Test
    fun addEditDisableAndDeleteArePersistedAndAffectAvailableSources() = runTest {
        val data = preferences()
        val store = MangaSourceSettingsStore(data)
        store.save(MangaSourceSettings(subscriptions = emptyList()))
        val client = HttpClient(MockEngine { request ->
            respond(if (request.url.encodedPath == "/two") """[{"key":"goda"}]""" else """[{"key":"Komiic"},{"key":"baozi"}]""")
        })
        try {
            val repo = MangaSubscriptionRepository(store, client.asScopedHttpClient())
            repo.save(null, "Test", "https://sources.test/one")
            val first = store.read().subscriptions.single()
            assertEquals(setOf(MangaSource.KOMIIC, MangaSource.BAOZI), store.read().enabled.toSet())
            assertNotNull(first.updatedAt)
            repo.save(first.id, "Edited", "https://sources.test/two")
            assertEquals(listOf(MangaSource.GODA), MangaSourceSettingsStore(data).read().enabled)
            assertEquals("https://sources.test/two", store.read().subscriptionUrl(MangaSource.GODA))
            repo.setEnabled(first.id, false)
            assertTrue(store.read().enabled.isEmpty())
            repo.setEnabled(first.id, true)
            assertEquals(listOf(MangaSource.GODA), store.read().enabled)
            repo.delete(first.id)
            assertTrue(MangaSourceSettingsStore(data).read().subscriptions.isEmpty())
            assertTrue(store.read().enabled.isEmpty())
        } finally { client.close() }
    }

    @Test
    fun failedEditAndRefreshKeepLastWorkingSourcesAndRejectDuplicateUrls() = runTest {
        val store = MangaSourceSettingsStore(preferences())
        val original = MangaSubscription("one", "Test", "https://source.test/list", sources = listOf(MangaSource.KOMIIC))
        store.save(MangaSourceSettings(subscriptions = listOf(original)))
        var calls = 0
        val client = HttpClient(MockEngine { calls++; respond("unavailable", HttpStatusCode.ServiceUnavailable) })
        try {
            val repo = MangaSubscriptionRepository(store, client.asScopedHttpClient())
            assertFailsWith<IllegalArgumentException> { repo.save(null, "Duplicate", original.url) }
            assertEquals(0, calls)
            assertFailsWith<IllegalStateException> { repo.save(original.id, "New", "https://source.test/new") }
            assertEquals(original, store.read().subscriptions.single())
            repo.refresh(original)
            assertEquals(original.sources, store.read().subscriptions.single().sources)
            assertTrue(store.read().subscriptions.single().error.orEmpty().contains("503"))
        } finally { client.close() }
    }

    @Test
    fun overlappingSubscriptionsDeduplicateAndStaleSourcePreferencesCannotRestoreDeletedLinks() = runTest {
        val store = MangaSourceSettingsStore(preferences())
        val a = MangaSubscription("a", "A", "https://a.test", sources = listOf(MangaSource.KOMIIC))
        val b = a.copy(id = "b", url = "https://b.test")
        store.save(MangaSourceSettings(subscriptions = listOf(a, b)))
        val stale = store.read()
        store.update { it.copy(subscriptions = it.subscriptions.filterNot { sub -> sub.id == "a" }) }
        store.savePreferences(stale.copy(order = listOf(MangaSource.GODA)))
        assertEquals(listOf(b), store.read().subscriptions)
        assertEquals(listOf(MangaSource.KOMIIC), store.read().enabled)
        store.update { it.copy(subscriptions = emptyList()) }
        assertTrue(store.read().enabled.isEmpty())
    }

    @Test
    fun inFlightRefreshCannotResurrectDeletedSubscriptionAndCancellationPropagates() = runTest {
        val store = MangaSourceSettingsStore(preferences())
        val original = store.read().subscriptions.first()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val client = HttpClient(MockEngine { started.complete(Unit); release.await(); respond("""[{"key":"goda"}]""") })
        try {
            val repo = MangaSubscriptionRepository(store, client.asScopedHttpClient())
            val refresh = launch { repo.refresh(original) }
            started.await()
            repo.delete(original.id)
            release.complete(Unit)
            refresh.join()
            assertFalse(store.read().subscriptions.any { it.id == original.id })
        } finally { client.close() }
        val cancelled = HttpClient(MockEngine { throw CancellationException("cancel") })
        try {
            store.save(MangaSourceSettings(subscriptions = listOf(original)))
            assertFailsWith<CancellationException> { MangaSubscriptionRepository(store, cancelled.asScopedHttpClient()).refresh(original) }
        } finally { cancelled.close() }
    }

}
