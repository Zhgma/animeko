// SPDX-License-Identifier: AGPL-3.0-only
package me.him188.ani.app.data.repository.manga

import com.fleeksoft.ksoup.Ksoup
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.json.*

/** Shared source queries and parsers; hosts supply HTTP and source-session handling. */
class MangaSourceSearch(
    private val get: suspend (String) -> String,
    private val queryKomiic: suspend (String, JsonObject) -> JsonObject,
) {
    private val additional = AdditionalMangaSources(get)
    private val verified = VerifiedMangaSources(get)
    suspend fun search(source: MangaSource, keyword: String): List<MangaSourceBook> {
        require(keyword.isNotBlank()) { "请输入作品名称" }
        return when (source) {
            MangaSource.KOMIIC -> queryKomiic(
                "Search",
                buildJsonObject { put("keyword", keyword) },
            ).obj("searchComicsAndAuthors").array("comics").map {
                val b = it.jsonObject
                MangaSourceBook(source, b.text("id"), b.text("title"), migrateMangaApiImageUrl(b.text("imageUrl")))
            }
            MangaSource.MANHUAGUI -> parseBookLinks(get("${source.home}/s/${keyword.encodeURLPathPart()}_p1.html"))
            MangaSource.CCC, MangaSource.COMICK -> verified.search(source, keyword)
            else -> additional.search(source, keyword)
        }
    }

    companion object {
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

    }
}
