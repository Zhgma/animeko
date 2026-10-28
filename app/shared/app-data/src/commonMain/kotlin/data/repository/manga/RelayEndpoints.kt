/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.manga

import io.ktor.http.Url
import kotlinx.coroutines.flow.MutableStateFlow

const val DEFAULT_RELAY_ORIGIN = "https://43.131.252.236:8443"
const val DEFAULT_RELAY_SUBSCRIPTION = "https://raw.githubusercontent.com/Zhgma/animeko/main/subs/api.json"

/** Shared endpoint state. Native startup restores it before creating network consumers. */
object RelayEndpoints {
    val origin = MutableStateFlow(DEFAULT_RELAY_ORIGIN)
    private var previousOrigins: Set<String> = emptySet()
    fun apply(value: String, previous: List<String> = emptyList()) {
        val normalized = validateRelayOrigin(value)
        previousOrigins = (previous + origin.value).map(::validateRelayOrigin).toSet()
        origin.value = normalized
    }

    fun migrateImage(url: String): String {
        val origins = previousOrigins + setOf("https://119.28.149.19:8443", "https://43.131.255.74:8443", DEFAULT_RELAY_ORIGIN)
        val old = origins.firstOrNull { origin ->
            listOf("books", "komiic").any { url.startsWith("$origin/api/$it/images/") }
        } ?: return url
        return origin.value + url.removePrefix(old)
    }
}

/** An HTTPS origin only; API paths are fixed by the application. */
fun validateRelayOrigin(value: String): String {
    val text = value.trim().trimEnd('/')
    val url = runCatching { Url(text) }.getOrNull()
    require(text.startsWith("https://") && text.none { it.isWhitespace() } &&
        url != null && url.host.isNotBlank() && url.protocol.name == "https" &&
        url.user.isNullOrEmpty() && url.password.isNullOrEmpty() && url.encodedPath in listOf("", "/") &&
        url.parameters.isEmpty() && url.fragment.isEmpty()) {
        "请输入 HTTPS 服务器地址，例如 https://43.131.252.236:8443，不要填写 /api 路径"
    }
    return text
}
