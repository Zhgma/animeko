/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.manga

import me.him188.ani.app.domain.mediasource.web.BlockReason
import me.him188.ani.app.domain.mediasource.web.BlockedException
import me.him188.ani.app.domain.mediasource.web.LoadedPage
import me.him188.ani.app.domain.mediasource.web.PageEvaluator
import me.him188.ani.app.domain.mediasource.web.PageExpectation
import me.him188.ani.app.domain.mediasource.web.PageVerdict
import me.him188.ani.app.domain.mediasource.web.SolveRequest
import me.him188.ani.app.domain.mediasource.web.captcha.WebSourceIdentityRegistry
import me.him188.ani.app.domain.mediasource.web.captcha.WebSourceCookieJar
import com.fleeksoft.ksoup.Ksoup
import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.him188.ani.utils.ktor.ScopedHttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import io.ktor.utils.io.errors.IOException

const val KOMIIC_API_BASE_URL = "https://119.28.149.19:8443/api/komiic"

enum class MangaSource(val title: String, val subscriptionUrl: String, val home: String) {
    KOMIIC("Komiic", "https://cdn.jsdelivr.net/gh/venera-app/venera-configs@main/komiic.js", "https://komiic.com"),
    MANHUAGUI("漫画柜", "https://cdn.jsdelivr.net/gh/venera-app/venera-configs@main/manhuagui.js", "https://www.manhuagui.com"),
    BAOZI("包子漫画", "https://cdn.jsdelivr.net/gh/venera-app/venera-configs@main/baozi.js", "https://cn.bzmgcn.com"),
    GODA("G站漫画", "https://cdn.jsdelivr.net/gh/venera-app/venera-configs@main/goda.js", "https://godamh.com"),
    MANHUAREN("漫画人", "https://cdn.jsdelivr.net/gh/venera-app/venera-configs@main/manhuaren.js", "https://www.manhuaren.com"),
    YKMH("优酷漫画", "https://cdn.jsdelivr.net/gh/venera-app/venera-configs@main/ykmh.js", "https://www.ykmh.net"),
    CCC("CCC追漫台", "https://cdn.jsdelivr.net/gh/venera-app/venera-configs@main/ccc.js", "https://www.creative-comic.tw"),
    COMICK("Comick", "https://cdn.jsdelivr.net/gh/venera-app/venera-configs@main/comick.js", "https://comick.art"),
    // Kept for decoding saved offline records. No network adapter is registered.
    MANGADEX("MangaDex", "https://cdn.jsdelivr.net/gh/venera-app/venera-configs@main/manga_dex.js", "https://mangadex.org");

    val iconUrl: String get() = if (this == KOMIIC) "$KOMIIC_API_BASE_URL/icon" else "$home/favicon.ico"

    companion object {
        val available = entries.filter { it != MANGADEX }
        val maintained = available.filter { it != BAOZI }
    }
}
@Serializable
data class MangaSourceBook(val source: MangaSource, val id: String, val title: String, val cover: String = "")
@Serializable
data class MangaChapter(val id: String, val title: String, val number: Double?, val group: String, val pageCount: Int = 0)
data class MangaPage(val url: String, val referer: String, val width: Int = 0, val height: Int = 0, val cccImageId: Int? = null)

/** Native adapters for the previously selected Venera source subscriptions. No remote script execution. */
class MangaSourceRepository(private val client: ScopedHttpClient, private val cookies: WebSourceCookieJar? = null, private val identities: WebSourceIdentityRegistry? = null) {
    private val additional = AdditionalMangaSources(::fetchText)
    private val verified = VerifiedMangaSources(::fetchText)
    suspend fun search(source: MangaSource, keyword: String): List<MangaSourceBook> {
        require(keyword.isNotBlank()) { "请输入作品名称" }
        return when (source) {
            MangaSource.KOMIIC -> komiic(
                "Search",
                buildJsonObject { put("keyword", keyword) },
            ).obj("searchComicsAndAuthors").array("comics").map {
                val b = it.jsonObject
                MangaSourceBook(source, b.text("id"), b.text("title"), b.text("imageUrl"))
            }
            MangaSource.MANHUAGUI -> parseBookLinks(fetchText("${source.home}/s/${keyword.encodeURLPathPart()}_p1.html"))
            MangaSource.CCC, MangaSource.COMICK -> verified.search(source, keyword)
            else -> additional.search(source, keyword)
        }
    }

    /** A pasted book URL makes source selection explicit and also works for ambiguous search titles. */
    fun bookFromUrl(url: String): MangaSourceBook? {
        Regex("https://komiic\\.com/comic/(\\d+)/?(?:[?#].*)?").matchEntire(url.trim())?.let {
            return MangaSourceBook(MangaSource.KOMIIC, it.groupValues[1], "Komiic #${it.groupValues[1]}")
        }
        Regex("https://(?:www\\.)?manhuagui\\.com/comic/(\\d+)/?(?:[?#].*)?").matchEntire(url.trim())?.let {
            return MangaSourceBook(MangaSource.MANHUAGUI, it.groupValues[1], "漫画柜 #${it.groupValues[1]}")
        }
        val parsed = runCatching { Url(url.trim()) }.getOrNull()
        if (parsed?.protocol?.name == "https") {
            val source = when (parsed.host) {
                "www.creative-comic.tw" -> MangaSource.CCC
                "comick.art" -> MangaSource.COMICK
                else -> null
            }
            if (source != null) {
                val pattern = if (source == MangaSource.CCC) Regex("/zh/book/([0-9]+)(?:/content)?/?")
                    else Regex("/comic/([A-Za-z0-9_-]+)/?")
                pattern.matchEntire(parsed.encodedPath)?.let {
                    return MangaSourceBook(source, it.groupValues[1], "${source.title} · ${it.groupValues[1]}")
                }
            }
        }
        return additional.bookFromUrl(url)
    }

    suspend fun chapters(book: MangaSourceBook): List<MangaChapter> = when (book.source) {
        MangaSource.KOMIIC -> komiic(
            "Chapters",
            buildJsonObject { put("comicId", book.id) },
        ).array("chaptersByComicId").map {
            val c = it.jsonObject
            val group = if (c.text("type") == "book") "单行本" else "章节"
            MangaChapter(c.text("id"), "第 ${c.text("serial")} ${if (group == "单行本") "卷" else "话"}",
                c.text("serial").toDoubleOrNull(), group, c.number("size"))
        }.distinctBy { it.id }.sortedWith(compareBy<MangaChapter> { it.group == "单行本" }.thenBy { it.number ?: Double.MAX_VALUE })
        MangaSource.MANHUAGUI -> {
            var html = fetchText("${book.source.home}/comic/${book.id}/")
            Regex("""id="__VIEWSTATE"[^>]*value="([^"]+)""", RegexOption.IGNORE_CASE).find(html)?.let {
                html += ManhuaguiDecoder.decompress(it.groupValues[1])
            }
            parseChapters(book.id, html).also { check(it.isNotEmpty()) { "漫画柜未返回章节，可能需要验证或页面格式有变" } }
        }
        MangaSource.CCC, MangaSource.COMICK -> verified.chapters(book)
        else -> additional.chapters(book)
    }

    suspend fun pages(book: MangaSourceBook, chapter: MangaChapter): List<MangaPage> = when (book.source) {
        MangaSource.KOMIIC -> komiic(
            "Images",
            buildJsonObject { put("comicId", book.id); put("chapterId", chapter.id) },
        ).array("imagesByChapterId").map {
            val p = it.jsonObject
            val url = p.text("url")
            check(url.startsWith("$KOMIIC_API_BASE_URL/images/")) { "Komiic 未返回有效的图片地址" }
            MangaPage(url, "$KOMIIC_API_BASE_URL/", p.number("width"), p.number("height"))
        }
        MangaSource.MANHUAGUI -> {
            val data = ManhuaguiDecoder.chapter(fetchText("${book.source.home}/comic/${book.id}/${chapter.id}.html"))
            data.array("files").map {
                MangaPage("https://us.hamreus.com${data.text("path")}${it.jsonPrimitive.content}?e=${data.obj("sl").text("e")}&m=${data.obj("sl").text("m")}",
                    "${book.source.home}/")
            }
        }
        MangaSource.CCC, MangaSource.COMICK -> verified.pages(book, chapter)
        else -> additional.pages(book, chapter)
    }.also { check(it.isNotEmpty()) { "该章节没有返回图片" } }

    suspend fun image(page: MangaPage): ByteArray = retryTransient {
        val cccKey = page.cccImageId?.let { id ->
            require(id > 0 && page.url == "https://storage.googleapis.com/ccc-www/fs/chapter_content/encrypt/$id/2")
            val key = Json.parseToJsonElement(fetchText("${VerifiedMangaSources.CCC_API}/book/chapter/image/$id")).jsonObject
            check(key.number("code") == 0) { "CCC：该图片需要登录或购买" }
            key.obj("data").text("key").also { check(it.isNotBlank()) { "CCC 未返回图片密钥" } }
        }
        client.use {
            val url = page.url
            val response = get(page.url) {
                expectSuccess = false
                header("Referer", page.referer)
                header("User-Agent", identities?.userAgentFor(Url(url).host) ?: USER_AGENT)
                if (!url.startsWith("$KOMIIC_API_BASE_URL/")) cookies?.getCookieHeaderValues(url)?.forEach { header("Cookie", it) }
                header("Accept", "image/avif,image/webp,image/png,image/jpeg,*/*;q=0.8")
                timeout { requestTimeoutMillis = 45_000; connectTimeoutMillis = 15_000; socketTimeoutMillis = 20_000 }
            }
            checkStatus(response.status.value, "图片")
            if (cccKey == null && response.contentType()?.contentType != "image") throw MangaSourceAccessException(
                "来源返回的不是图片，可能需要登录、网页验证或阅读额度已用完；请切换来源")
            response.body<ByteArray>().also { check(it.size in 1..25_000_000) { "图片大小异常" } }
                .let { if (cccKey == null) it else decodeCccImage(cccKey, it) }
        }
    }

    private suspend fun komiic(operation: String, variables: JsonObject): JsonObject = client.use {
        val url = "$KOMIIC_API_BASE_URL/query"
        val response = post(url) {
            expectSuccess = false
            contentType(ContentType.Application.Json)
            header("User-Agent", USER_AGENT)
            header("Accept", "application/json")
            timeout { requestTimeoutMillis = 25_000; connectTimeoutMillis = 10_000 }
            setBody(buildJsonObject { put("operation", operation); put("variables", variables) }.toString())
        }
        checkStatus(response.status.value, "Komiic")
        val root = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        check(root.array("errors").isEmpty()) { "Komiic：${root.array("errors").first().jsonObject.text("message")}" }
        check(root["data"] is JsonObject) { "Komiic 返回格式有变" }
        root.obj("data")
    }

    private suspend fun fetchText(url: String): String = client.use {
        val origin = Url(url).let { if (it.host == "v2.apikk.top") "https://godamh.com/" else "${it.protocol.name}://${it.host}/" }
        val response = get(url) {
            expectSuccess = false; header("Referer", origin); header("User-Agent", identities?.userAgentFor(Url(url).host) ?: USER_AGENT)
            cookies?.getCookieHeaderValues(url)?.forEach { header("Cookie", it) }
            header("Accept", "text/html,application/json;q=0.9,*/*;q=0.8")
            if (Url(url).host == "api.creative-comic.tw") { header("device", "web_desktop"); header("uuid", "null") }
            timeout { requestTimeoutMillis = 25_000; connectTimeoutMillis = 10_000 }
        }
        val body = response.bodyAsText()
        if (response.contentType()?.contentType == "text") {
            val verdict = PageEvaluator().evaluate(LoadedPage(url, body, response.status.value), PageExpectation.AnyContent)
            if (verdict is PageVerdict.Blocked && verdict.reason is BlockReason.Captcha) {
                val source = MangaSource.entries.firstOrNull { Url(it.home).host == Url(origin).host }
                throw BlockedException(verdict.reason, SolveRequest("manga:${source?.name ?: Url(url).host}", url,
                    verdict.reason.kind, PageExpectation.AnyContent))
            }
        }
        checkStatus(response.status.value, Url(url).host)
        body
    }

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        internal fun cleanHtml(text: String): String = text.replace(Regex("<[^>]*>"), "").replace("&amp;", "&")
            .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ").trim()

        internal fun parseBookLinks(html: String): List<MangaSourceBook> =
            Ksoup.parse(html, MangaSource.MANHUAGUI.home).select("a[href^=/comic/]").mapNotNull { link ->
                val id = Regex("/comic/(\\d+)/").matchEntire(link.attr("href"))?.groupValues?.get(1) ?: return@mapNotNull null
                val image = link.selectFirst("img")
                val title = link.attr("title").ifBlank { image?.attr("alt").orEmpty() }
                    .ifBlank { if (image == null) link.text() else "" }.trim()
                title.takeIf { it.isNotBlank() && it !in listOf("详情", "开始阅读") }?.let {
                    MangaSourceBook(MangaSource.MANHUAGUI, id, it, image?.absUrl("src").orEmpty())
                }
            }.distinctBy { it.id }

        internal fun parseChapters(bookId: String, html: String): List<MangaChapter> =
            Ksoup.parse(html).select("a[href]").mapNotNull { link ->
                val id = Regex("/comic/${Regex.escape(bookId)}/(\\d+)\\.html").matchEntire(link.attr("href"))
                    ?.groupValues?.get(1) ?: return@mapNotNull null
                if (link.text().contains("开始阅读")) return@mapNotNull null
                val name = link.attr("title").ifBlank { link.text().replace(Regex("\\s*\\d+\\s*p$"), "") }.trim()
                if (name.isBlank()) return@mapNotNull null
                val number = Regex("\\d+(?:\\.\\d+)?").find(name)?.value?.toDoubleOrNull()
                val group = when {
                    name.contains("卷") -> "单行本"
                    name.contains("番外") -> "番外"
                    Regex("^第?\\s*\\d").containsMatchIn(name) -> "章节"
                    else -> "其他"
                }
                val count = link.selectFirst("i")?.text()?.removeSuffix("p")?.trim()?.toIntOrNull() ?: 0
                MangaChapter(id, name, number, group, count)
            }.distinctBy { it.id }.sortedWith(compareBy<MangaChapter> { it.group != "章节" }
                .thenBy { it.group }.thenBy { it.number ?: Double.MAX_VALUE })

    }
}

private class MangaTransientHttpError(message: String) : IOException(message)
class MangaSourceAccessException(message: String) : IllegalStateException(message)

private fun checkStatus(status: Int, source: String) {
    when (status) {
        in 200..299 -> Unit
        401 -> throw MangaSourceAccessException("$source 需要登录，请切换来源")
        403 -> throw MangaSourceAccessException("$source 拒绝访问，可能需要网页验证、登录或图片地址已过期；请刷新章节或切换来源")
        429 -> throw MangaSourceAccessException("$source 请求过于频繁或阅读额度已用完，请稍后重试或切换来源")
        in 500..599 -> throw MangaTransientHttpError("$source 暂时不可用（HTTP $status）")
        else -> error("$source HTTP $status，请重试或切换来源")
    }
}

/** Retry only transient transport/server failures; authorization and quotas require user action. */
private suspend fun <T> retryTransient(block: suspend () -> T): T {
    repeat(2) { attempt ->
        try { return block() }
        catch (e: CancellationException) { throw e }
        catch (e: IOException) {
            if (attempt == 1) throw e
            delay(1_000)
        }
    }
    error("Unreachable")
}
