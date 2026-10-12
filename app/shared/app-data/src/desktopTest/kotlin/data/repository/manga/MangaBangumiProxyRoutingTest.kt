/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.manga

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.data.models.preference.ProxyConfig
import me.him188.ani.app.data.models.preference.ProxyMode
import me.him188.ani.app.data.models.preference.ProxySettings
import me.him188.ani.utils.ktor.createDefaultHttpClient
import me.him188.ani.utils.ktor.proxy
import java.net.ServerSocket
import java.util.concurrent.CompletableFuture
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MangaBangumiProxyRoutingTest {
    @Test fun mangaBangumiUsesDedicatedProxyWhileSourceTrafficConnectsDirectly() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val data = object : DataStore<Preferences> {
            override val data = MutableStateFlow(emptyPreferences())
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                transform(data.value).also { data.value = it }
        }
        val proxy = ServerSocket(0)
        val origin = ServerSocket(0)
        val proxyRequest = CompletableFuture<String>()
        fun serve(server: ServerSocket, body: String, capture: CompletableFuture<String>? = null) = thread(isDaemon = true) {
            server.soTimeout = 15_000
            server.accept().use { socket ->
                val input = socket.getInputStream().bufferedReader()
                capture?.complete(input.readLine()) ?: input.readLine()
                while (!input.readLine().isNullOrEmpty()) { }
                socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n$body".encodeToByteArray())
            }
        }
        serve(proxy, "proxied", proxyRequest)
        serve(origin, "direct")
        val booksBase = "http://127.0.0.1:${origin.localPort}/api/books"
        val network = MangaBangumiNetwork(data, scope, booksBase)
        val settings = ProxySettings.Disabled
        network.settings.save(settings.copy(default = settings.default.copy(mode = ProxyMode.CUSTOM,
            customConfig = ProxyConfig("http://127.0.0.1:${proxy.localPort}"))))
        val sourceClient = createDefaultHttpClient(bare = true) { proxy(null) }
        try {
            network.start()
            val target = "http://127.0.0.1:${origin.localPort}/source"
            assertEquals("proxied", network.http.get("https://api.bgm.tv/v0/subjects/118167").requireSuccess())
            assertTrue(proxyRequest.get().contains("$booksBase/v0/subjects/118167"))
            assertEquals("direct", sourceClient.get(target).bodyAsText())
        } finally {
            sourceClient.close(); scope.cancel(); proxy.close(); origin.close()
        }
    }
}
