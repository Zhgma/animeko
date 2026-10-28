/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.manga

import io.ktor.http.Url
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.io.encoding.Base64

const val DEFAULT_RELAY_ORIGIN = "https://43.131.252.236:8443"
const val DEFAULT_RELAY_SUBSCRIPTION = "https://raw.githubusercontent.com/Zhgma/animeko/main/subs/api.json"

/** Shared endpoint state. Native startup restores it before creating network consumers. */
object RelayEndpoints {
    val origin = MutableStateFlow(DEFAULT_RELAY_ORIGIN)
    val enabled = MutableStateFlow(false)
    private var previousOrigins: Set<String> = emptySet()
    fun apply(value: String, previous: List<String> = emptyList()) {
        val normalized = validateRelayOrigin(value)
        previousOrigins = (previous + origin.value).map(::validateRelayOrigin).toSet()
        origin.value = normalized
    }

    private fun knownOrigins() = previousOrigins + setOf(origin.value, "https://119.28.149.19:8443",
        "https://43.131.255.74:8443", DEFAULT_RELAY_ORIGIN)

    fun isRelayRequest(url: String): Boolean {
        val request = runCatching { Url(url) }.getOrNull() ?: return false
        return request.encodedPath.startsWith("/api/") && knownOrigins().any {
            val known = Url(it)
            request.protocol == known.protocol && request.host.equals(known.host, ignoreCase = true) && request.port == known.port
        }
    }

    internal fun imageRoute(url: String, enabled: Boolean = this.enabled.value): RelayImageRoute {
        if (!isRelayRequest(url)) return RelayImageRoute(url)
        if (enabled) return RelayImageRoute(migrateImage(url))
        val path = Url(url).encodedPath
        if (path == "/api/komiic/icon") return RelayImageRoute("https://komiic.com/favicon.ico", "https://komiic.com/")
        val match = Regex("/api/(books|komiic)/images/([A-Za-z0-9_-]+)/[A-Za-z0-9_-]+(?:[?].*)?").matchEntire(path)
        requireNotNull(match) { "当前网络设置未启用中转，请刷新页面" }
        val payload = match.groupValues[2]
        val decoded = Base64.UrlSafe.decode(payload.padEnd((payload.length + 3) / 4 * 4, '=')).decodeToString()
        if (match.groupValues[1] == "books") {
            require(Regex("/(?:r/(?:100|200|400|800)/)?pic/(?:cover|user)/(?:s|m|l)/[A-Za-z0-9_/-]+[.](?:jpg|jpeg|png|webp|gif)(?:[?](?:r=[0-9]{1,12}|hd=1)(?:&(?:r=[0-9]{1,12}|hd=1))*)?", RegexOption.IGNORE_CASE).matches(decoded))
            return RelayImageRoute("https://lain.bgm.tv$decoded")
        }
        val asset = Json.parseToJsonElement(decoded).jsonObject
        val host = asset.text("host")
        val target = asset.text("path")
        val referer = asset.text("referer")
        require((host == "komiic.com" && Regex("/api/image/[a-f0-9-]{36}").matches(target)) ||
            (host == "public.komiic.com" && Regex("/comics/[a-f0-9]{32}/[A-Za-z0-9_-]+[.](?:jpg|jpeg|png|webp)").matches(target)))
        require(Regex("https://komiic[.]com/(?:comic/[1-9][0-9]{0,9}/chapter/[1-9][0-9]{0,9}/images/all)?").matches(referer))
        return RelayImageRoute("https://$host$target", referer)
    }

    fun migrateImage(url: String): String {
        val origins = previousOrigins + setOf("https://119.28.149.19:8443", "https://43.131.255.74:8443", DEFAULT_RELAY_ORIGIN)
        val old = origins.firstOrNull { origin ->
            listOf("books", "komiic").any { url.startsWith("$origin/api/$it/images/") }
        } ?: return url
        return origin.value + url.removePrefix(old)
    }
}

internal data class RelayImageRoute(val url: String, val referer: String? = null)

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
