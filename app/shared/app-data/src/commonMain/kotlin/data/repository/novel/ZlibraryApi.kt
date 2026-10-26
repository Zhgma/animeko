/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.novel

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.flow.first
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import me.him188.ani.utils.ktor.ScopedHttpClient

class ZlibraryLoginRequired(val origin: String) : IllegalStateException("请登录 Z-Library 后重试")

/** Site session only; passwords and login form contents are never stored. */
@Serializable
class ZlibrarySession(val origin: String, val userId: String, val userKey: String, val userAgent: String = "Mozilla/5.0") {
    fun isValid() = userId.matches(Regex("[0-9]+")) && userKey.matches(Regex("[A-Za-z0-9_-]{1,512}"))
    override fun toString() = "ZlibrarySession(origin=$origin, credentials=redacted)"
}

class ZlibrarySessionStore(private val store: DataStore<Preferences>) {
    private val key = stringPreferencesKey("novel.zlibrary.session.v1")
    private val json = Json { ignoreUnknownKeys = true }
    suspend fun read(origin: String): ZlibrarySession? = store.data.first()[key]?.let {
        runCatching { json.decodeFromString<ZlibrarySession>(it) }.getOrNull()
    }?.takeIf { it.origin == origin && it.isValid() }
    suspend fun save(value: ZlibrarySession) {
        require(value.isValid())
        store.edit { it[key] = json.encodeToString(value) }
    }
    suspend fun invalidate(session: ZlibrarySession) {
        store.edit { prefs ->
            val current = prefs[key]?.let { runCatching { json.decodeFromString<ZlibrarySession>(it) }.getOrNull() }
            if (current?.origin == session.origin && current.userKey == session.userKey) prefs.remove(key)
        }
    }
    suspend fun clear() { store.edit { it.remove(key) } }
}

/** EAPI protocol reference: bipinkrish/Zlibrary-API (MIT), Zlibrary.py. */
class ZlibraryApi(private val client: ScopedHttpClient, val sessions: ZlibrarySessionStore) {
    companion object {
        val formats = setOf("epub", "txt", "mobi", "azw3", "azw")
        fun origin(home: String): String {
            val url = Url(home)
            require(url.protocol.name == "https" && url.user.isNullOrEmpty() && url.password.isNullOrEmpty()) { "Z-Library 需要 HTTPS 地址" }
            return "https://${url.host}" + if (url.port == 443) "" else ":${url.port}"
        }
    }

    suspend fun accept(session: ZlibrarySession) {
        val root = api(session, "/eapi/user/profile")
        val user = root["user"] as? JsonObject ?: error("登录信息尚未就绪")
        check(user.text("id") == session.userId) { "登录信息尚未就绪" }
        sessions.save(session)
    }

    private suspend fun session(home: String) = origin(home).let { sessions.read(it) ?: throw ZlibraryLoginRequired(it) }

    private suspend fun api(session: ZlibrarySession, path: String, form: String? = null): JsonObject {
        require(session.isValid())
        val (status, bytes) = transfer(session.origin + path, session, form, 4 * 1024 * 1024)
        if (status == 401) { sessions.invalidate(session); throw ZlibraryLoginRequired(session.origin) }
        val root = runCatching { Json.parseToJsonElement(bytes.decodeToString()).jsonObject }.getOrNull()
        val message = root?.text("error").orEmpty().lowercase()
        val expired = message.contains("please login") || message.contains("not logged") || message.contains("not authorized") || message.contains("token expired")
        if (expired) { sessions.invalidate(session); throw ZlibraryLoginRequired(session.origin) }
        check(status in 200..299) { "Z-Library 请求失败（$status），请重试" }
        check(root != null) { "Z-Library 未返回有效数据，请重试或打开登录页面" }
        check(root.text("success") in setOf("1", "true")) {
            if (message.contains("limit") || message.contains("quota")) "Z-Library 下载额度不足，请稍后重试"
            else "Z-Library 暂时无法完成请求，请重试"
        }
        return root
    }

    suspend fun search(home: String, query: String): List<NovelBook> {
        val form = "message=${query.encodeURLParameter()}&limit=50&page=1&" + formats.joinToString("&") { "extensions%5B%5D=$it" }
        val root = api(session(home), "/eapi/book/search", form)
        return (root["books"] as? JsonArray).orEmpty().mapNotNull { item ->
            val b = item as? JsonObject ?: return@mapNotNull null
            val extension = b.text("extension").lowercase()
            val id = b.text("id"); val hash = b.text("hash")
            if (extension !in formats || !id.matches(Regex("[0-9]+")) || !hash.matches(Regex("[A-Za-z0-9]+"))) return@mapNotNull null
            NovelBook(NovelSource.ZLIBRARY, "$id/$hash/$extension", b.text("title"), b.text("author"),
                listOf(extension.uppercase(), b.text("language"), b.text("year"), b.text("filesizeString")).filter { it.isNotBlank() }.joinToString(" · "))
        }.distinctBy { it.id }
    }

    suspend fun download(home: String, book: NovelBook): Pair<String, ByteArray> {
        val parts = book.id.split('/')
        require(parts.size == 3 && parts[0].matches(Regex("[0-9]+")) && parts[1].matches(Regex("[A-Za-z0-9]+")) && parts[2] in formats)
        val root = api(session(home), "/eapi/book/${parts[0]}/${parts[1]}/file")
        val file = root["file"] as? JsonObject ?: error("没有可下载的电子书")
        val extension = file.text("extension").lowercase()
        require(extension in formats) { "该文件格式暂不支持阅读" }
        var url = file.text("downloadLink")
        // Download hosts receive no account cookies, including through redirects.
        repeat(6) {
            require(Url(url).protocol.name == "https" && Url(url).user.isNullOrEmpty() && Url(url).password.isNullOrEmpty()) { "下载地址必须使用 HTTPS" }
            val result = downloadOnce(url)
            if (result.redirect != null) {
                url = novelUrl(url, result.redirect)
            } else {
                check(result.status in 200..299) { "电子书下载失败（${result.status}）" }
                return "book.$extension" to result.bytes
            }
        }
        error("电子书下载跳转次数过多")
    }

    private data class FileResponse(val status: Int, val bytes: ByteArray, val redirect: String?)
    private suspend fun downloadOnce(url: String): FileResponse = client.use {
        val isolated = config { followRedirects = false; expectSuccess = false; install(HttpCookies) { storage = AcceptAllCookiesStorage() } }
        try {
            isolated.prepareRequest(url) {
                timeout { requestTimeoutMillis = 120_000 }
            }.execute { response ->
                val status = response.status.value
                FileResponse(status, if (status in 200..299) boundedBytes(response.bodyAsChannel(), 96 * 1024 * 1024) else byteArrayOf(),
                    if (status in 300..399) response.headers["Location"] else null)
            }
        } finally { isolated.close() }
    }

    private suspend fun transfer(url: String, session: ZlibrarySession, form: String?, limit: Int): Pair<Int, ByteArray> = client.use {
        val isolated = config { followRedirects = false; expectSuccess = false; install(HttpCookies) { storage = AcceptAllCookiesStorage() } }
        try {
            isolated.prepareRequest(url) {
                method = if (form == null) HttpMethod.Get else HttpMethod.Post
                header("User-Agent", session.userAgent)
                header("Cookie", "remix_userid=${session.userId}; remix_userkey=${session.userKey}; siteLanguageV2=en")
                if (form != null) { header("Content-Type", "application/x-www-form-urlencoded"); setBody(form) }
                timeout { requestTimeoutMillis = 30_000 }
            }.execute { it.status.value to boundedBytes(it.bodyAsChannel(), limit) }
        } finally { isolated.close() }
    }
}

private suspend fun boundedBytes(channel: ByteReadChannel, limit: Int): ByteArray {
    val result = Buffer(); val chunk = ByteArray(8192)
    while (true) {
        val count = channel.readAvailable(chunk, 0, chunk.size)
        if (count < 0) break
        require(result.size + count <= limit) { "Z-Library 文件或响应超过大小限制" }
        if (count > 0) result.write(chunk, 0, count)
    }
    return result.readByteArray()
}
private fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.content.orEmpty()
