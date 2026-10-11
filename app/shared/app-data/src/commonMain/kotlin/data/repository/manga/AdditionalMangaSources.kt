/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.manga

import com.fleeksoft.ksoup.Ksoup
import io.ktor.http.Url
import io.ktor.http.encodeURLParameter
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64

/** Public catalogs and reading endpoints; remote subscription JavaScript is never executed. */
internal class AdditionalMangaSources(private val get: suspend (String) -> String) {
    private suspend fun json(url: String) = Json.parseToJsonElement(get(url)).jsonObject

    suspend fun search(source: MangaSource, keyword: String): List<MangaSourceBook> = when (source) {
        MangaSource.MANHUAREN -> {
            val doc = Ksoup.parse(get("${source.home}/search?title=${keyword.encodeURLParameter()}&language=1&page=1"), source.home)
            doc.select(".book-list > li").mapNotNull { item ->
                val link = item.selectFirst(".book-list-info > a") ?: return@mapNotNull null
                val id = link.attr("href").removePrefix("/manhua-").trim('/')
                if (!Regex("[a-zA-Z0-9_-]+").matches(id)) null else MangaSourceBook(source, id,
                    item.selectFirst(".book-list-info-title")?.text().orEmpty(),
                    item.selectFirst(".book-list-cover-img")?.absUrl("src").orEmpty())
            }.distinctBy { it.id }
        }
        MangaSource.YKMH -> {
            val doc = Ksoup.parse(get("${source.home}/search/?keywords=${keyword.encodeURLParameter()}"), source.home)
            doc.select(".list-comic a.image-link").map { link ->
                MangaSourceBook(source, Url(link.absUrl("href")).encodedPath.removePrefix("/manhua/").trim('/'),
                    link.attr("title"), link.selectFirst("img")?.absUrl("src").orEmpty())
            }.distinctBy { it.id }
        }
        MangaSource.BAOZI, MangaSource.GODA -> {
            val path = if (source == MangaSource.BAOZI) "/search?q=${keyword.encodeURLParameter()}"
                else "/s/${keyword.encodeURLPathPart()}?page=1"
            val doc = Ksoup.parse(get(source.home + path), source.home)
            val prefix = if (source == MangaSource.BAOZI) "/comic/" else "/manga/"
            doc.select("a[href^=$prefix]").mapNotNull { link ->
                val title = link.selectFirst("h3")?.text()?.trim()
                    ?: link.attr("title").ifBlank { link.text().trim() }
                if (title.isBlank()) null else MangaSourceBook(source, link.attr("href").substringAfter(prefix).trimEnd('/'),
                    title, link.selectFirst("img, amp-img")?.absUrl("src").orEmpty())
            }.distinctBy { it.id }
        }
        else -> error("不支持的来源")
    }

    fun bookFromUrl(raw: String): MangaSourceBook? {
        val url = runCatching { Url(raw.trim()) }.getOrNull() ?: return null
        if (url.protocol.name != "https") return null
        val (source, prefix) = when (url.host) {
            "cn.bzmgcn.com", "www.baozimh.com", "cn.baozimhcn.com" -> MangaSource.BAOZI to "/comic/"
            "godamh.com" -> MangaSource.GODA to "/manga/"
            "www.manhuaren.com" -> MangaSource.MANHUAREN to "/manhua-"
            "www.ykmh.net", "m.ykmh.net" -> MangaSource.YKMH to "/manhua/"
            else -> return null
        }
        if (!url.encodedPath.startsWith(prefix)) return null
        val id = url.encodedPath.removePrefix(prefix).substringBefore('/')
        if (!id.matches(Regex("[a-zA-Z0-9_-]+"))) return null
        return MangaSourceBook(source, id, "${source.title} · $id")
    }

    suspend fun chapters(book: MangaSourceBook): List<MangaChapter> = when (book.source) {
        MangaSource.BAOZI -> {
            val doc = Ksoup.parse(get("${book.source.home}/comic/${book.id}"))
            // Chapter indices are source IDs, never derive them from the reversed visual order.
            val links = doc.select("#chapter-items .comics-chapters > a, #chapters_other_list .comics-chapters > a")
            links.mapIndexed { index, link ->
                val href = link.attr("href")
                val id = Regex("chapter_id=(\\d+)").find(href)?.groupValues?.get(1) ?: index.toString()
                chapter(id, link.text())
            }
        }
        MangaSource.GODA -> {
            val doc = Ksoup.parse(get("${book.source.home}/manga/${book.id}"))
            val id = doc.selectFirst("#mangachapters")?.attr("data-mid").orEmpty()
            check(id.matches(Regex("[a-zA-Z0-9_-]+"))) { "G站未返回作品 ID" }
            json("$GODA/manga/get?mid=$id&mode=all").obj("data").array("chapters").map {
                val c = it.jsonObject
                chapter("$id@${c.text("id")}", c.obj("attributes").text("title"))
            }
        }
        MangaSource.MANHUAREN -> {
            val doc = Ksoup.parse(get("${book.source.home}/manhua-${book.id}/"))
            doc.select("a.chapteritem").mapNotNull { link ->
                val id = Regex("/m([0-9]+)/?").matchEntire(link.attr("href"))?.groupValues?.get(1)
                id?.let { chapter(it, link.text()) }
            }
        }
        MangaSource.YKMH -> {
            val doc = Ksoup.parse(get("https://m.ykmh.net/manhua/${book.id}/"))
            doc.select("a[href]").mapNotNull { link ->
                val path = link.attr("href").removePrefix("https://m.ykmh.net").removePrefix(book.source.home)
                if (!Regex("/manhua/${Regex.escape(book.id)}/[0-9]+\\.html").matches(path)) null
                else chapter(path.substringAfterLast('/').removeSuffix(".html"), link.text())
            }
        }
        else -> error("不支持的来源")
    }.distinctBy { it.id }.sortedWith(compareBy<MangaChapter> { it.group }.thenBy { it.number ?: Double.MAX_VALUE })
        .also { check(it.isNotEmpty()) { "${book.source.title} 未返回可阅读章节，可能需要登录、网页验证或该作品只有外链" } }

    suspend fun pages(book: MangaSourceBook, chapter: MangaChapter): List<MangaPage> = when (book.source) {
        MangaSource.BAOZI -> {
            val url = "https://appcn.baozimh.com/baozimhapp/comic/chapter/${book.id}/0_${chapter.id}.html"
            Ksoup.parse(get(url), url).select(".comic-contain .comic-contain__item").mapNotNull {
                val image = it.attr("data-src").ifBlank { it.attr("src") }
                image.takeIf { it.startsWith("https://") }?.let { MangaPage(it, "${book.source.home}/") }
            }
        }
        MangaSource.GODA -> {
            val ids = chapter.id.split('@')
            require(ids.size == 2)
            val images = json("$GODA/chapter/getinfo?m=${ids[0]}&c=${ids[1]}").obj("data").obj("info").obj("images")["images"]
            val decoded = if (images is JsonArray) images else decodeGodaImages((images as? JsonPrimitive)?.content.orEmpty())
            decoded.map { row ->
                val path = row.jsonObject.text("url")
                MangaPage(if (path.startsWith("https://")) path else "https://c-nd3-1.6wm.top/$path".replace("top//", "top/"), "${book.source.home}/")
            }
        }
        MangaSource.MANHUAREN -> {
            val url = "${book.source.home}/m${chapter.id}/"
            unpackManhuarenImages(get(url)).map { MangaPage(it, url) }
        }
        MangaSource.YKMH -> {
            val home = "https://m.ykmh.net"
            val html = get("$home/manhua/${book.id}/${chapter.id}.html")
            val raw = Regex("var\\s+chapterImages\\s*=\\s*(\\[.*?]);").find(html)?.groupValues?.get(1)
                ?: error("优酷漫画未返回章节图片，可能需要登录或该章节不可用")
            val config = get("$home/js/config.js")
            val domain = Regex("\"domain\"\\s*:\\s*\\[\"(https://[^\"]+)\"").find(config)?.groupValues?.get(1)
                ?: error("优酷漫画图片服务器配置格式有变")
            Json.parseToJsonElement(raw).jsonArray.map {
                val path = it.jsonPrimitive.content
                MangaPage(if (path.startsWith("https://")) path else domain.trimEnd('/') + "/" + path.trimStart('/'), "$home/")
            }
        }
        else -> error("不支持的来源")
    }

    private fun chapter(id: String, title: String): MangaChapter = MangaChapter(id, title.trim(),
        Regex("\\d+(?:\\.\\d+)?").find(title)?.value?.toDoubleOrNull(),
        if (title.contains("卷")) "单行本" else if (title.contains("番外")) "番外" else "章节")

    companion object {
        private const val GODA = "https://v2.apikk.top/api/v2"

        /** Decodes only the bounded literal dictionary and URL array; never evaluates JavaScript. */
        internal fun unpackManhuarenImages(html: String): List<String> {
            require(html.length <= 2_000_000)
            val match = Regex("""\}\('(.+?)',(\d+),(\d+),'(.*?)'\.split""", RegexOption.DOT_MATCHES_ALL).find(html)
                ?: error("漫画人未返回可阅读图片，该章节可能需要付费或已失效")
            val radix = match.groupValues[2].toInt()
            require(radix in 2..62)
            val words = match.groupValues[4].split('|')
            require(words.size <= 50_000)
            val alphabet = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
            var outputSize = match.groupValues[1].length
            val unpacked = Regex("\\b[a-zA-Z0-9_]+\\b").replace(match.groupValues[1]) { token ->
                var index = 0L
                for (ch in token.value) {
                    val digit = alphabet.indexOf(ch)
                    if (digit < 0 || digit >= radix) return@replace token.value
                    index = index * radix + digit
                    if (index > words.lastIndex) return@replace token.value
                }
                val replacement = words[index.toInt()].ifBlank { token.value }
                outputSize += replacement.length - token.value.length
                require(outputSize <= 8_000_000) { "漫画人章节数据过大" }
                replacement
            }
            val array = Regex("\\[(.*?)]", RegexOption.DOT_MATCHES_ALL).find(unpacked)?.groupValues?.get(1)
                ?: error("漫画人章节格式有变")
            return array.split(',').map { it.trim().removePrefix("\\'").removeSuffix("\\'")
                .removeSurrounding("'").removeSurrounding("\"").replace("\\/", "/") }
                .also { urls -> require(urls.isNotEmpty() && urls.size <= 2_000 && urls.all { it.startsWith("https://") }) }
        }

        /** Bounded decoding of the catalog's encoded JSON; no JavaScript execution. */
        internal fun decodeGodaImages(input: String): JsonArray {
            require(input.length in 10..2_000_000 && input.startsWith("J7r") && input.endsWith("nQ")) { "G站章节格式有变" }
            val body = input.substring(3, input.length - 2)
            val length = body.length - 5
            val a = length / 3
            val b = (length - a) / 2
            val c = length - a - b
            require(body.substring(b, b + 2) == "kD" && body.substring(b + 2 + c, b + 5 + c) == "W4s")
            val reordered = body.substring(b + 5 + c) + body.substring(0, b) + body.substring(b + 2, b + 2 + c)
            val alphabet = "_-9876543210abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
            val standard = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
            val encoded = reordered.chunked(7).mapIndexed { i, chunk -> if (i % 2 == 1) chunk.reversed() else chunk }
                .joinToString("").map { ch ->
                    val index = alphabet.indexOf(ch)
                    require(index >= 0) { "G站章节编码无效" }
                    standard[index]
                }.joinToString("")
            return Json.parseToJsonElement(Base64.UrlSafe.decode(encoded.padEnd((encoded.length + 3) / 4 * 4, '=')).decodeToString()) as JsonArray
        }
    }
}
