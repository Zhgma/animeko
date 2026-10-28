package me.him188.ani.app.data.repository.manga

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import me.him188.ani.utils.ktor.asScopedHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull

class RelaySettingsTest {
    private fun preferences() = object : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences) =
            transform(data.value).also { data.value = it }
    }

    @Test
    fun validatesOriginsAndLeavesOtherPathsUntouched() {
        assertEquals("https://relay.example:8443", validateRelayOrigin(" https://relay.example:8443/ "))
        for (value in listOf("http://relay.example", "https://u:p@relay.example", "https://relay.example/api",
            "https://relay.example/?token=x", "https://relay.example/#x", "file:///etc/passwd")) {
            assertFails { validateRelayOrigin(value) }
        }
        RelayEndpoints.apply(DEFAULT_RELAY_ORIGIN)
        assertEquals("$DEFAULT_RELAY_ORIGIN/api/books/images/path/sig?size=400",
            migrateMangaApiImageUrl("https://43.131.255.74:8443/api/books/images/path/sig?size=400"))
        for (url in listOf("https://43.131.255.74:8443/other", "https://43.131.255.74:8443.evil.test/api/books/images/a")) {
            assertEquals(url, migrateMangaApiImageUrl(url))
        }
    }

    @Test
    fun refreshesWithoutCredentialsAndManualOverrideSurvivesRefreshAndRestart() = runTest {
        val data = preferences()
        var body = """{"version":1,"origin":"https://remote.example:8443"}"""
        val client = HttpClient(MockEngine) { engine { addHandler {
            assertNull(it.headers["Authorization"])
            assertNull(it.headers["Cookie"])
            respond(body)
        } } }
        try {
            val store = RelaySettingsStore(data, client.asScopedHttpClient())
            store.initialize()
            store.refresh()
            assertEquals("https://remote.example:8443/api/books/v0/subjects/1",
                MangaRequestRouting.resolve("GET", "https://api.bgm.tv/v0/subjects/1").url)
            store.save("https://manual.example", DEFAULT_RELAY_SUBSCRIPTION)
            store.refresh()
            assertEquals("https://manual.example/api/komiic", KOMIIC_API_BASE_URL)
            assertEquals("https://manual.example/api/komiic/images/a/b",
                migrateMangaApiImageUrl("https://remote.example:8443/api/komiic/images/a/b"))
            RelayEndpoints.apply(DEFAULT_RELAY_ORIGIN)
            RelaySettingsStore(data, client.asScopedHttpClient()).initialize()
            assertEquals("https://manual.example", RelayEndpoints.origin.value)
            store.save("", DEFAULT_RELAY_SUBSCRIPTION)
            assertEquals("https://remote.example:8443", RelayEndpoints.origin.value)
            for (invalid in listOf("<html>error</html>", """{"version":2,"origin":"https://other.example"}""",
                """{"version":1,"origin":"http://unsafe.example"}""")) {
                body = invalid
                assertFails { store.refresh() }
                assertEquals("https://remote.example:8443", store.flow.first().effectiveOrigin)
            }
        } finally { client.close(); RelayEndpoints.apply(DEFAULT_RELAY_ORIGIN) }
    }
}
