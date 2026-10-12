/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.manga

import com.fleeksoft.ksoup.Ksoup
import io.ktor.http.Url
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** Public catalogs. CCC reading is limited to chapters available without an account. */
internal class VerifiedMangaSources(private val get: suspend (String) -> String) {
    private suspend fun ccc(path: String): JsonObject {
        val root = Json.parseToJsonElement(get("$CCC_API$path")).jsonObject
        check(root.number("code") == 0 && root["data"] is JsonObject) { "CCC：${root.text("message")}" }
        return root.obj("data")
    }
    private suspend fun comickPage(url: String): JsonObject {
        val element = Ksoup.parse(get(url)).getElementById("sv-data") ?: error("Comick 页面格式有变")
        return Json.parseToJsonElement(element.data()).jsonObject
    }
    suspend fun search(source: MangaSource, keyword: String): List<MangaSourceBook> = when (source) {
        MangaSource.CCC -> ccc("/book?page=1&rows_per_page=20&keyword=${keyword.encodeURLParameter()}&class=2")
            .array("data").map { it.jsonObject }.map { MangaSourceBook(source, it.text("id"), it.text("name"),
                it.text("image1").ifBlank { it.text("image2") }) }
        MangaSource.COMICK -> comickPage("${source.home}/search?q=${keyword.encodeURLParameter()}&page=1")
            .array("data").map { it.jsonObject }.map { MangaSourceBook(source, it.text("slug"), it.text("title"), it.text("default_thumbnail").ifBlank { it.text("full_image_path") }) }
        else -> error("不支持的来源")
    }.filter { it.id.isNotBlank() && it.title.isNotBlank() }.distinctBy { it.id }

    suspend fun chapters(book: MangaSourceBook): List<MangaChapter> {
        require(book.id.matches(Regex("[A-Za-z0-9_-]+")))
        val chapters: List<MangaChapter> = when (book.source) {
            MangaSource.CCC -> ccc("/book/${book.id}/chapter").array("chapters").map { it.jsonObject }.mapIndexed { index, c ->
                MangaChapter(c.text("id"), c.text("vol_name") + " " + c.text("name"),
                    c.text("sequence").toDoubleOrNull() ?: (index + 1).toDouble(), if (c.text("unit") == "卷") "单行本" else "章节")
            }
            MangaSource.COMICK -> buildList<MangaChapter> {
                var page = 1
                do {
                    val root = Json.parseToJsonElement(get("${book.source.home}/api/comics/${book.id}/chapter-list?page=$page")).jsonObject
                    val rows = root.array("data")
                    check(root["data"] != null) { "Comick 目录格式有变" }
                    for (row in rows) {
                        val c = row.jsonObject
                        val part = c.text("chap").takeUnless { it == "null" || it.isBlank() }
                        val volume = c.text("vol").takeUnless { it == "null" || it.isBlank() }
                        val kind = if (part != null) "chapter" else if (volume != null) "volume" else "no"
                        val number = part ?: volume ?: "-1"
                        val lang = c.text("lang").ifBlank { "unknown" }
                        val id = "${c.text("hid")}|$kind|$number|$lang"
                        add(MangaChapter(id, "[$lang] " + if (number == "-1") c.text("title").ifBlank { "未编号" }
                            else "第 $number ${if (kind == "volume") "卷" else "话"}",
                            number.toDoubleOrNull()?.takeIf { it >= 0 }, if (kind == "volume") "单行本" else "章节"))
                    }
                    val last = root.obj("pagination").number("last_page").coerceAtLeast(1)
                    check(last <= 100) { "Comick 章节分页过多，请选择其他来源" }
                    page++
                } while (page <= last)
            }
            else -> error("不支持的来源")
        }
        return chapters.distinctBy { it.id }.sortedWith(compareBy<MangaChapter> { it.group }.thenBy { it.number ?: Double.MAX_VALUE })
            .also { check(it.isNotEmpty()) { "${book.source.title} 未返回章节" } }
    }

    suspend fun pages(book: MangaSourceBook, chapter: MangaChapter): List<MangaPage> = when (book.source) {
        MangaSource.CCC -> {
            require(chapter.id.matches(Regex("[0-9]+")))
            val c = ccc("/book/chapter/${chapter.id}").obj("chapter")
            check(c["sales_plan"] != null && (c.number("sales_plan") == 0 || c.number("is_free") == 1)) { "CCC：该章节需要登录或购买，请在原站阅读" }
            c.array("proportion").map { it.jsonObject }.map {
                val id = it.number("id"); require(id > 0)
                MangaPage("https://storage.googleapis.com/ccc-www/fs/chapter_content/encrypt/$id/2",
                    "${MangaSource.CCC.home}/", it.number("width"), it.number("height"), cccImageId = id)
            }
        }
        MangaSource.COMICK -> {
            val parts = chapter.id.split('|')
            require(parts.size == 4 && parts.all { it.matches(Regex("[A-Za-z0-9_.-]+")) })
            require(book.id.matches(Regex("[A-Za-z0-9_-]+")))
            val suffix = if (parts[1] == "no") parts[0] else parts.joinToString("-")
            var url: String? = "${book.source.home}/comic/${book.id}/$suffix"
            val seen = mutableSetOf<String>()
            buildList {
                while (url != null) {
                    val current = url
                    check(seen.add(current) && seen.size <= 100) { "Comick 阅读分页异常" }
                    val doc = Ksoup.parse(get(current), current)
                    val element = doc.getElementById("sv-data") ?: error("Comick 阅读页面格式有变")
                    val data = Json.parseToJsonElement(element.data()).jsonObject.obj("chapter")
                    val images = data.array("images"); check(images.isNotEmpty()) { "Comick 未返回图片" }
                    images.map { it.jsonObject.text("url") }.forEach {
                        require(Url(it).protocol.name == "https") { "Comick 图片地址无效" }
                        add(MangaPage(it, "${book.source.home}/"))
                    }
                    check(size <= 2000) { "Comick 章节图片过多" }
                    val next = doc.getElementById("next-chapter")
                    url = if (next != null && ("下一页" in next.text() || "下一頁" in next.text())) next.absUrl("href") else null
                    if (url != null) require(url!!.startsWith("${book.source.home}/comic/${book.id}/")) { "Comick 阅读分页地址无效" }
                }
            }
        }
        else -> error("不支持的来源")
    }
    companion object { const val CCC_API = "https://api.creative-comic.tw" }
}
