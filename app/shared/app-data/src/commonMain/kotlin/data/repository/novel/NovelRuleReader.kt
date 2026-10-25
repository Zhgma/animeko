/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.novel

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import io.ktor.http.Url
import io.ktor.http.encodeURLParameter

data class NovelRequest(val url: String, val method: String = "GET", val body: String = "", val headers: Map<String, String> = emptyMap())

internal fun novelUrl(base: String, value: String): String {
    val absolute = Ksoup.parse("", base).body().also { it.attr("href", value) }.absUrl("href")
    return NovelSubscriptionParser.validateUrl(absolute)
}

internal fun novelTemplate(value: String, key: String = "", id: String = "", chapter: String = "", page: Int = 1) = value
    .replace("{{key}}", key.encodeURLParameter()).replace("{{id}}", id).replace("{{chapter}}", chapter).replace("{{page}}", page.toString())

/** A deliberately bounded subset of static CSS/Legado selectors; no source code is evaluated. */
internal fun novelElements(root: Element, rule: String): List<Element> {
    if (rule.isBlank()) return listOf(root)
    var elements = listOf(root)
    for (part in rule.removePrefix("@css:").split('@').filter { it.isNotBlank() }) {
        var selector = part.replace(Regex("^class\\."), ".").replace(Regex("^id\\."), "#").replace(Regex("^tag\\."), "")
        if (selector.startsWith("text.")) selector = "a:containsOwn(${selector.removePrefix("text.")})"
        val index = Regex("^(.*)\\.(-?\\d+)$").matchEntire(selector)
        val exclude = Regex("^(.*)!(\\d+)$").matchEntire(selector)
        elements = elements.flatMap { parent ->
            val matches = parent.select(index?.groupValues?.get(1) ?: exclude?.groupValues?.get(1) ?: selector).toList()
            when {
                index != null -> listOfNotNull(matches.getOrNull(index.groupValues[2].toInt().let { if (it < 0) matches.size + it else it }))
                exclude != null -> matches.filterIndexed { i, _ -> i != exclude.groupValues[2].toInt() }
                else -> matches
            }
        }
    }
    return elements
}

internal fun novelValue(root: Element, rule: String): String {
    if (rule.isBlank()) return ""
    val transformations = rule.split("##")
    val expression = transformations[0].removePrefix("@css:")
    val attr = expression.substringAfterLast('@', "text")
    val selector = if ('@' in expression) expression.substringBeforeLast('@') else expression
    val element = novelElements(root, selector).firstOrNull() ?: return ""
    var value = when (attr) {
        "text" -> element.text()
        "ownText" -> element.ownText()
        "html" -> element.html()
        "textNodes" -> element.textNodes().joinToString("\n") { it.getWholeText() }
        "href", "src" -> element.absUrl(attr)
        else -> element.attr(attr)
    }
    if (transformations.size > 1 && transformations[1].isNotBlank()) value = value.replace(Regex(transformations[1]), transformations.getOrElse(2) { "" })
    return value.trim()
}

internal class NovelRuleReader(private val fetch: suspend (NovelRequest) -> String) {
    suspend fun search(d: NovelSourceDefinition, keyword: String): List<NovelBook> {
        val url = novelUrl(d.home, novelTemplate(d.searchUrl, key = keyword))
        val doc = Ksoup.parse(fetch(NovelRequest(url, d.searchMethod, novelTemplate(d.searchBody, key = keyword), d.headers)), url)
        return novelElements(doc, d.searchList).mapNotNull { element ->
            val title = novelValue(element, d.searchName)
            val link = novelValue(element, d.searchLink)
            if (title.isBlank() || link.isBlank()) null else NovelBook(d.source, novelUrl(url, link), title, novelValue(element, d.searchAuthor))
        }.distinctBy { it.id }.take(200)
    }

    suspend fun chapters(d: NovelSourceDefinition, book: NovelBook): List<NovelChapter> {
        var url = if (d.tocUrl.isNotBlank()) novelUrl(d.home, novelTemplate(d.tocUrl, id = book.id)) else novelUrl(d.home, book.id)
        if (d.tocLink.isNotBlank()) {
            val doc = page(d, url)
            val link = novelValue(doc, d.tocLink)
            check(link.isNotBlank()) { "目录入口未返回" }
            url = novelUrl(url, link)
        }
        val visited = mutableSetOf<String>()
        val result = mutableListOf<NovelChapter>()
        while (visited.add(url)) {
            check(visited.size <= 200) { "目录分页超过 200 页，请检查订阅规则" }
            val doc = page(d, url)
            novelElements(doc, d.chapters).forEach { item ->
                val title = novelValue(item, d.chapterName)
                val link = novelValue(item, d.chapterLink)
                if (title.isNotBlank() && link.isNotBlank() && !title.contains(Regex("(?i)VIP|付费|购买|解锁"))) {
                    result += NovelChapter(novelUrl(url, link), title, NovelSourceRepository.explicitNumber(title))
                }
            }
            val next = novelValue(doc, d.nextToc)
            if (next.isBlank()) break
            url = sameSite(d, url, next)
        }
        check(result.isNotEmpty()) { "没有可读取的免费章节" }
        return result.distinctBy { it.id }
    }

    suspend fun content(d: NovelSourceDefinition, chapter: NovelChapter): List<NovelBlock> {
        var url = novelUrl(d.home, chapter.id)
        val visited = mutableSetOf<String>()
        val result = mutableListOf<NovelBlock>()
        while (visited.add(url)) {
            check(visited.size <= 30) { "章节分页超过 30 页，请检查订阅规则" }
            val doc = page(d, url)
            val next = novelValue(doc, d.nextContent)
            doc.select(d.remove.ifBlank { "script,style" }).remove()
            if (d.exclude.isNotBlank()) doc.select(d.exclude).remove()
            val selector = d.content.substringBefore("##").removeSuffix("@html").removeSuffix("@text").removeSuffix("@textNodes")
            val bodies = novelElements(doc, selector)
            check(bodies.isNotEmpty()) { "正文未返回，请检查书源或更换来源" }
            val transforms = d.content.split("##")
            val blocks = bodies.flatMap { body ->
                val cleaned = if (transforms.size > 1 && transforms[1].isNotBlank()) {
                    Ksoup.parse(body.html().replace(Regex(transforms[1]), transforms.getOrElse(2) { "" }), url).body()
                } else body
                htmlBlocks(cleaned, url)
            }
            check(blocks.any { it.text.isNotBlank() }) { "正文为空" }
            check(!blocks.joinToString { it.text }.contains(Regex("登录后阅读|购买本章|开通VIP|解锁本章|订阅本章"))) { "该章节需要登录或购买" }
            result += blocks
            if (next.isBlank()) break
            url = sameSite(d, url, next)
        }
        return result
    }

    private suspend fun page(d: NovelSourceDefinition, url: String) = Ksoup.parse(fetch(NovelRequest(url, headers = d.headers)), url)
    private fun sameSite(d: NovelSourceDefinition, base: String, next: String): String = novelUrl(base, next).also {
        require(Url(it).host == Url(base).host || Url(it).host == Url(d.home).host) { "分页跳转到其他站点" }
    }
}
