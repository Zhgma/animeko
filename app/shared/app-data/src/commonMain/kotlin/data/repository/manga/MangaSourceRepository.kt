/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.manga

import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
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

enum class MangaSource(val title: String, val subscriptionUrl: String, val home: String) {
    KOMIIC("Komiic", "https://cdn.jsdelivr.net/gh/venera-app/venera-configs@main/komiic.js", "https://komiic.com"),
    MANHUAGUI("漫画柜", "https://cdn.jsdelivr.net/gh/venera-app/venera-configs@main/manhuagui.js", "https://www.manhuagui.com"),
}
@Serializable
data class MangaSourceBook(val source: MangaSource, val id: String, val title: String, val cover: String = "")
@Serializable
data class MangaChapter(val id: String, val title: String, val number: Double?, val group: String, val pageCount: Int = 0)
data class MangaPage(val url: String, val referer: String, val width: Int = 0, val height: Int = 0)

/** Native adapters for the previously selected Venera source subscriptions. No remote script execution. */
class MangaSourceRepository(private val client: ScopedHttpClient) {
    suspend fun search(source: MangaSource, keyword: String): List<MangaSourceBook> {
        require(keyword.isNotBlank()) { "请输入作品名称" }
        return when (source) {
            MangaSource.KOMIIC -> query(
                "query Search(\$keyword: String!) { searchComicsAndAuthors(keyword: \$keyword) { comics { id title imageUrl } } }",
                buildJsonObject { put("keyword", keyword) },
            ).obj("searchComicsAndAuthors").array("comics").map {
                val b = it.jsonObject
                MangaSourceBook(source, b.text("id"), b.text("title"), b.text("imageUrl"))
            }
            MangaSource.MANHUAGUI -> parseBookLinks(fetchText("${source.home}/s/${keyword.encodeURLPathPart()}_p1.html"))
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
        return null
    }

    suspend fun chapters(book: MangaSourceBook): List<MangaChapter> = when (book.source) {
        MangaSource.KOMIIC -> query(
            "query Chapters(\$comicId: ID!) { chaptersByComicId(comicId: \$comicId) { id serial type size } }",
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
    }

    suspend fun pages(book: MangaSourceBook, chapter: MangaChapter): List<MangaPage> = when (book.source) {
        MangaSource.KOMIIC -> query(
            "query Images(\$chapterId: ID!) { imagesByChapterId(chapterId: \$chapterId) { kid width height } }",
            buildJsonObject { put("chapterId", chapter.id) },
        ).array("imagesByChapterId").map {
            val p = it.jsonObject
            MangaPage("${book.source.home}/api/image/${p.text("kid").encodeURLPathPart()}",
                "${book.source.home}/comic/${book.id}/chapter/${chapter.id}/images/all", p.number("width"), p.number("height"))
        }
        MangaSource.MANHUAGUI -> {
            val data = ManhuaguiDecoder.chapter(fetchText("${book.source.home}/comic/${book.id}/${chapter.id}.html"))
            data.array("files").map {
                MangaPage("https://us.hamreus.com${data.text("path")}${it.jsonPrimitive.content}?e=${data.obj("sl").text("e")}&m=${data.obj("sl").text("m")}",
                    "${book.source.home}/")
            }
        }
    }.also { check(it.isNotEmpty()) { "该章节没有返回图片" } }

    suspend fun image(page: MangaPage): ByteArray = client.use {
        val response = get(page.url) { expectSuccess = false; header("Referer", page.referer); header("User-Agent", USER_AGENT) }
        check(response.status.value in 200..299) { "图片 HTTP ${response.status.value}，可重试或切换漫画源" }
        check(response.contentType()?.contentType == "image") { "漫画源未返回图片，可能需要登录或已达到阅读额度" }
        response.body<ByteArray>().also { check(it.size in 1..25_000_000) { "图片大小异常" } }
    }

    private suspend fun query(query: String, variables: JsonObject): JsonObject = client.use {
        val response = post("https://komiic.com/api/query") {
            expectSuccess = false
            contentType(ContentType.Application.Json)
            header("Referer", "https://komiic.com/")
            header("User-Agent", USER_AGENT)
            setBody(buildJsonObject { put("query", query); put("variables", variables) }.toString())
        }
        check(response.status.value in 200..299) { "Komiic HTTP ${response.status.value}" }
        val root = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        check(root.array("errors").isEmpty()) { "Komiic：${root.array("errors").first().jsonObject.text("message")}" }
        check(root["data"] is JsonObject) { "Komiic 返回格式有变" }
        root.obj("data")
    }

    private suspend fun fetchText(url: String): String = client.use {
        val response = get(url) { expectSuccess = false; header("Referer", "https://www.manhuagui.com/"); header("User-Agent", USER_AGENT) }
        check(response.status.value in 200..299) { "漫画柜 HTTP ${response.status.value}" }
        response.bodyAsText()
    }

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0.0.0 Safari/537.36"
        internal fun cleanHtml(text: String): String = text.replace(Regex("<[^>]*>"), "").replace("&amp;", "&")
            .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ").trim()

        internal fun parseBookLinks(html: String): List<MangaSourceBook> =
            Regex("""<a\b[^>]*href="/comic/(\d+)/"[^>]*>(.*?)</a>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
                .findAll(html).mapNotNull {
                    val title = cleanHtml(it.groupValues[2])
                    title.takeIf(String::isNotBlank)?.let { title -> MangaSourceBook(MangaSource.MANHUAGUI, it.groupValues[1], title) }
                }.distinctBy { it.id }.toList()

        internal fun parseChapters(bookId: String, html: String): List<MangaChapter> =
            Regex("""<a\b[^>]*href="/comic/${Regex.escape(bookId)}/(\d+)\.html"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
                .findAll(html).mapNotNull {
                    val name = cleanHtml(it.groupValues[2])
                    if (name.contains("开始阅读") || name.isBlank()) return@mapNotNull null
                    val number = Regex("第?\\s*(\\d+(?:\\.\\d+)?)").find(name)?.groupValues?.get(1)?.toDoubleOrNull()
                    MangaChapter(it.groupValues[1], name, number, when { name.contains("卷") -> "单行本"; name.contains("番外") -> "番外"; else -> "章节" })
                }.distinctBy { it.id }.sortedWith(compareBy<MangaChapter> { it.group != "章节" }.thenBy { it.group }.thenBy { it.number ?: Double.MAX_VALUE }).toList()
    }
}
