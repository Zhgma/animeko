/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.manga

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestPipeline
import io.ktor.http.HttpHeaders
import io.ktor.http.takeFrom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import me.him188.ani.app.data.models.preference.ProxyMode
import me.him188.ani.app.data.models.preference.ProxySettings

/** Only an explicit mainland selection with the global proxy disabled enables relay traffic. */
object RelayRoutingPolicy {
    val enabled get() = RelayEndpoints.enabled

    fun shouldRelay(useGlobal: Boolean?, proxyMode: ProxyMode): Boolean =
        useGlobal == false && proxyMode == ProxyMode.DISABLED

    suspend fun start(regions: Flow<Boolean?>, proxies: Flow<ProxySettings>, scope: CoroutineScope,
                      onChange: suspend () -> Unit = {}) {
        combine(regions, proxies) { region, proxy -> shouldRelay(region, proxy.default.mode) }
            .distinctUntilChanged().onEach { enabled.value = it; onChange() }.stateIn(scope)
    }
}

/** Resolve persisted image URLs again at send time, including images already displayed by Sketch. */
internal fun HttpClient.installRelayImageRouting() {
    requestPipeline.intercept(HttpRequestPipeline.State) {
        val original = context.url.buildString()
        if (RelayEndpoints.isRelayRequest(original)) {
            val route = RelayEndpoints.imageRoute(original, RelayRoutingPolicy.enabled.value)
            if (route.url != original) {
                context.url.takeFrom(route.url)
                context.headers.remove(HttpHeaders.Authorization)
                context.headers.remove(HttpHeaders.Cookie)
                context.headers.remove("Referer")
                route.referer?.let { context.headers.append("Referer", it) }
            }
        }
    }
}
