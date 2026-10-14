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
import io.ktor.client.statement.HttpResponse
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

/** Native adapters for the previously selected Venera source subscriptions. No remote script execution. */
class MangaSourceRepository internal constructor(
    private val client: ScopedHttpClient,
    private val cookies: WebSourceCookieJar?,
    private val identities: WebSourceIdentityRegistry?,
    private val komiicImages: KomiicImageRequests,
) {
    constructor(client: ScopedHttpClient, cookies: WebSourceCookieJar? = null, identities: WebSourceIdentityRegistry? = null) :
        this(client, cookies, identities, sharedKomiicImageRequests)
    private val additional = AdditionalMangaSources(::fetchText)
    private val verified = VerifiedMangaSources(::fetchText)
    private val searchAdapter = MangaSourceSearch(::fetchText, ::komiic)
    suspend fun search(source: MangaSource, keyword: String): List<MangaSourceBook> = searchAdapter.search(source, keyword)

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
            val url = migrateMangaApiImageUrl(p.text("url"))
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
        if (page.url.startsWith("$KOMIIC_API_BASE_URL/images/")) {
            komiicImages.request { fetchImage(page) }
        } else fetchImage(page)
    }

    private suspend fun fetchImage(page: MangaPage): ByteArray {
        val cccKey = page.cccImageId?.let { id ->
            require(id > 0 && page.url == "https://storage.googleapis.com/ccc-www/fs/chapter_content/encrypt/$id/2")
            val key = Json.parseToJsonElement(fetchText("${VerifiedMangaSources.CCC_API}/book/chapter/image/$id")).jsonObject
            check(key.number("code") == 0) { "CCC：该图片需要登录或购买" }
            key.obj("data").text("key").also { check(it.isNotBlank()) { "CCC 未返回图片密钥" } }
        }
        return client.use {
            val url = migrateMangaApiImageUrl(page.url)
            val response = get(url) {
                expectSuccess = false
                header("Referer", page.referer)
                header("User-Agent", identities?.userAgentFor(Url(url).host) ?: USER_AGENT)
                if (!url.startsWith("$KOMIIC_API_BASE_URL/")) cookies?.getCookieHeaderValues(url)?.forEach { header("Cookie", it) }
                header("Accept", "image/avif,image/webp,image/png,image/jpeg,*/*;q=0.8")
                timeout { requestTimeoutMillis = 45_000; connectTimeoutMillis = 15_000; socketTimeoutMillis = 20_000 }
            }
            if (url.startsWith("$KOMIIC_API_BASE_URL/")) checkKomiicStatus(response)
            else checkStatus(response.status.value, "图片")
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
        checkKomiicStatus(response)
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

        internal fun parseBookLinks(html: String): List<MangaSourceBook> = MangaSourceSearch.parseBookLinks(html)

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
class MangaSourceAccessException(message: String, val retryAfterMillis: Long? = null) : IllegalStateException(message)

private suspend fun checkKomiicStatus(response: HttpResponse) {
    if (response.status.value == 429) {
        val body = response.bodyAsText()
        val code = runCatching { Json.parseToJsonElement(body).jsonObject.text("error") }.getOrDefault("")
        val seconds = (response.headers["Retry-After"]?.toLongOrNull() ?: 30).coerceIn(30, 86_400)
        val message = if (code == "komiic_reading_limit") {
            "Komiic 源站阅读额度已用完，已暂停图片下载；请等待源站额度恢复或切换来源"
        } else {
            "Komiic 请求过于频繁，已暂停图片下载；请至少 $seconds 秒后重试"
        }
        throw MangaSourceAccessException(message, seconds * 1_000)
    }
    checkStatus(response.status.value, "Komiic")
}

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
