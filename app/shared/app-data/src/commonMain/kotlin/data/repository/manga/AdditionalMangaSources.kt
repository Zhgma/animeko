/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.manga

import com.fleeksoft.ksoup.Ksoup
import io.ktor.http.Url
import io.ktor.http.encodeURLParameter
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64

/** Public catalogs and reading endpoints; remote subscription JavaScript is never executed. */
internal class AdditionalMangaSources(private val get: suspend (String) -> String) {
    private suspend fun json(url: String) = Json.parseToJsonElement(get(url)).jsonObject

    suspend fun search(source: MangaSource, keyword: String): List<MangaSourceBook> = when (source) {
        MangaSource.MANGADEX -> json("$DEX/manga?title=${keyword.encodeURLParameter()}&limit=30&includes%5B%5D=cover_art")
            .array("data").map { item ->
                val book = item.jsonObject
                val attrs = book.obj("attributes")
                val titles = attrs.obj("title")
                val title = listOf("zh", "zh-hk", "en", "ja-ro", "ja").firstNotNullOfOrNull {
                    titles[it]?.jsonPrimitive?.content
                } ?: titles.values.firstOrNull()?.jsonPrimitive?.content.orEmpty()
                val cover = book.array("relationships").firstOrNull { it.jsonObject.text("type") == "cover_art" }
                    ?.jsonObject?.obj("attributes")?.text("fileName")
                MangaSourceBook(source, book.text("id"), title,
                    cover?.let { "https://uploads.mangadex.org/covers/${book.text("id")}/$it.256.jpg" }.orEmpty())
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
            "mangadex.org" -> MangaSource.MANGADEX to "/title/"
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
        MangaSource.MANGADEX -> {
            val result = mutableListOf<MangaChapter>()
            var offset = 0
            do {
                val page = json("$DEX/manga/${book.id}/feed?limit=500&offset=$offset&order%5Bchapter%5D=asc&order%5BupdatedAt%5D=asc")
                val rows = page.array("data")
                for (row in rows) {
                    val c = row.jsonObject
                    val a = c.obj("attributes")
                    if (a.number("pages") <= 0 || a.text("externalUrl").let { it.isNotEmpty() && it != "null" }) continue
                    val lang = when (val language = a.text("translatedLanguage")) {
                        "zh" -> "简中"; "zh-hk" -> "繁中"; "en" -> "英语"; "ja" -> "日语"; else -> language
                    }
                    val team = c.array("relationships").filter { it.jsonObject.text("type") == "scanlation_group" }
                        .joinToString("+") { it.jsonObject.text("id").take(8) }.ifBlank { "个人" }
                    val number = a.text("chapter").toDoubleOrNull()
                    result += MangaChapter(c.text("id"), "${a.text("chapter").takeUnless { it == "null" }.orEmpty()} ${a.text("title").takeUnless { it == "null" }.orEmpty()}".trim().ifBlank { "短篇" },
                        number, "$lang · $team", a.number("pages"))
                }
                offset += rows.size
            } while (rows.isNotEmpty() && offset < page.number("total"))
            result
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
        MangaSource.MANGADEX -> {
            val root = json("$DEX/at-home/server/${chapter.id}")
            val data = root.obj("chapter")
            val base = root.text("baseUrl")
            require(base.startsWith("https://")) { "MangaDex 未返回图片服务器" }
            data.array("data").map { MangaPage("$base/data/${data.text("hash")}/${it.jsonPrimitive.content}", "${book.source.home}/") }
        }
        else -> error("不支持的来源")
    }

    private fun chapter(id: String, title: String): MangaChapter = MangaChapter(id, title.trim(),
        Regex("\\d+(?:\\.\\d+)?").find(title)?.value?.toDoubleOrNull(),
        if (title.contains("卷")) "单行本" else if (title.contains("番外")) "番外" else "章节")

    companion object {
        private const val DEX = "https://api.mangadex.org"
        private const val GODA = "https://v2.apikk.top/api/v2"

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
