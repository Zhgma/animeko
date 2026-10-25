/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.novel

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode
import io.ktor.client.request.get
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.HttpMethod
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.plugins.timeout
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Url
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.him188.ani.utils.ktor.ScopedHttpClient

/** Native protocol helpers plus remotely maintained passive HTTP/CSS rules. */
class NovelSourceRepository(
    private val request: suspend (NovelRequest) -> String,
    private val fetchImage: suspend (String) -> ByteArray,
    private val definitions: suspend () -> List<NovelSourceDefinition>,
) {
    constructor(fetch: suspend (String) -> String) : this({ fetch(it.url) }, { error("图片下载不可用") }, { DefaultNovelSources.all })
    constructor(client: ScopedHttpClient, store: NovelSourceSettingsStore? = null) : this({ req -> client.use {
        val response = request(req.url) {
            method = HttpMethod.parse(req.method)
            header("User-Agent", "Mozilla/5.0 Animecomic")
            req.headers.forEach { (key, value) -> header(key, value) }
            if (req.method == "POST") { header("Content-Type", "application/x-www-form-urlencoded"); setBody(req.body) }
            timeout { requestTimeoutMillis = 30_000 }
        }
        check(response.status.value in 200..299) { "书源请求失败 (${response.status.value})" }
        response.bodyAsText().also { require(it.length <= 8 * 1024 * 1024) { "书源响应过大" } }
    } }, { url -> client.use {
        val response = get(url) { timeout { requestTimeoutMillis = 30_000 } }
        check(response.status.value in 200..299) { "插图下载失败 (${response.status.value})" }
        check(response.headers["Content-Type"]?.startsWith("image/") == true) { "书源没有返回插图" }
        response.body<ByteArray>().also { require(it.size <= 20 * 1024 * 1024) { "插图过大" } }
    } }, { store?.read()?.definitions ?: DefaultNovelSources.all })
    private val rules = NovelRuleReader(request)
    private suspend fun definition(source: NovelSource) = definitions().firstOrNull { it.source == source } ?: error("该书源已停用或删除，请重新选择")
    private suspend fun fetch(url: String) = request(NovelRequest(url))

    suspend fun image(url: String): ByteArray {
        require(Url(url).protocol.name in setOf("https", "http"))
        return fetchImage(url)
    }

    suspend fun search(source: NovelSource, keyword: String): List<NovelBook> {
        require(keyword.isNotBlank())
        val definition = definition(source)
        return when (definition.engine) {
            "quanben" -> {
                val url = novelUrl(definition.home, novelTemplate(definition.searchUrl, key = keyword))
                Ksoup.parse(fetch(url), url).select(definition.searchList).mapNotNull { item ->
                    val a = item.selectFirst(definition.searchLink.substringBefore("@")) ?: return@mapNotNull null
                    val path = Url(a.absUrl("href")).encodedPath
                    if (!Regex("/n/[^/]+/?").matches(path)) return@mapNotNull null
                    NovelBook(source, path.trimEnd('/').substringAfterLast('/'), a.text(), item.select("[itemprop=author]").text())
                }
            }
            "huanmeng" -> api("search", "key=${keyword.encodeURLParameter()}&page=1&size=50")
                .list().map { NovelBook(source, it.s("id"), it.s("name"), it.s("author")) }
            else -> rules.search(definition, keyword)
        }.distinctBy { it.id }
    }

    suspend fun chapters(book: NovelBook): List<NovelChapter> {
        val definition = definition(book.source)
        val result = when (definition.engine) {
            "quanben" -> {
                validateId(book)
                val url = novelUrl(definition.home, novelTemplate(definition.tocUrl, id = book.id))
                Ksoup.parse(fetch(url), url).select(definition.chapters).map { a ->
                    NovelChapter(a.absUrl("href"), a.text(), explicitNumber(a.text()))
                }
            }
            "huanmeng" -> buildList<NovelChapter> {
                var page = 1
                do {
                    val data = api("chapters", "id=${book.id}&size=5000&page=$page")
                    val list = data.list()
                    // Paid chapters stay absent; never request their content or unlock them.
                    addAll(list.filter { it.s("vip") in listOf("", "0") && it.s("cion") in listOf("", "0") }
                        .map { NovelChapter(it.s("id"), it.s("name"), explicitNumber(it.s("name"))) })
                    val pages = data.s("pages").toIntOrNull() ?: 1
                    page++
                } while (list.isNotEmpty() && page <= pages && page <= 100)
            }
            else -> rules.chapters(definition, book)
        }.distinctBy { it.id }
        check(result.isNotEmpty()) { "没有可读取的免费章节，请更换书源" }
        return result
    }

    suspend fun content(book: NovelBook, chapter: NovelChapter): List<NovelBlock> {
        val definition = definition(book.source)
        val blocks = when (definition.engine) {
            "quanben" -> {
                val url = Url(chapter.id)
                require(url.protocol.name in setOf("https", "http") && url.host.removePrefix("www.") == Url(definition.home).host.removePrefix("www.") &&
                    url.encodedPath.contains("/${book.id}/")) { "无效的章节地址" }
                val document = Ksoup.parse(fetch(chapter.id), chapter.id)
                val body = document.selectFirst(definition.content) ?: error("正文未返回，请重试或更换书源")
                htmlBlocks(body, chapter.id)
            }
            "huanmeng" -> {
                require(chapter.id.all(Char::isDigit))
                val data = api("content", "bid=${book.id}&cid=${chapter.id}")
                check(data.s("vip") in listOf("", "0") && data.s("cion") in listOf("", "0")) { "该章节需要登录或购买" }
                htmlBlocks(Ksoup.parse(data.s("content"), "https://www.huanmengacg.com").body(), "https://www.huanmengacg.com")
            }
            else -> rules.content(definition, chapter)
        }
        check(blocks.any { it.text.isNotBlank() || it.image != null }) { "正文为空，请重试" }
        return blocks
    }

    private suspend fun api(action: String, parameters: String): JsonObject {
        // Public adapter key supplied by the source publisher, not a user's account credential.
        val definition = definition(NovelSource.HUANMENG)
        val args = parameters.split('&').associate { it.substringBefore('=') to it.substringAfter('=') }
        var url = when (action) { "search" -> definition.searchUrl; "chapters" -> definition.tocUrl; else -> definition.contentUrl }
        for ((key, value) in args) url = url.replace("{{$key}}", value)
        url = url.replace("{{id}}", args["bid"].orEmpty()).replace("{{chapter}}", args["cid"].orEmpty())
        val root = Json.parseToJsonElement(fetch(novelUrl(definition.home, url))).jsonObject
        check(root.s("code") == "1") { "书源暂时不可用，或章节需要登录／购买" }
        return root.getValue("data").jsonObject
    }

    private fun validateId(book: NovelBook) {
        require(book.id.isNotBlank() && book.id.all { it.isLetterOrDigit() || it == '-' || it == '_' }) { "无效的书籍编号" }
    }

    companion object {
        internal fun explicitNumber(title: String): Int? = Regex("^第\\s*(\\d+)\\s*[章话]").find(title.trim())
            ?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 }
    }
}

private fun JsonObject.s(key: String) = get(key)?.jsonPrimitive?.content.orEmpty()
private fun JsonObject.list() = get("list")?.jsonArray?.map { it.jsonObject }.orEmpty()

/** Render passive text/images only. No scripts, embedded frames, stylesheet or remote document execution. */
internal fun htmlBlocks(root: Element, baseUrl: String, resolveImage: (String) -> String? = { url ->
    runCatching { Url(url) }.getOrNull()?.takeIf { it.protocol.name in setOf("https", "http") }?.toString()
}): List<NovelBlock> {
    root.select("script,style,iframe,object,form,nav").remove()
    val result = mutableListOf<NovelBlock>()
    val buffer = StringBuilder()
    fun flush() { result += novelParagraphs(buffer.toString().replace(Regex("\\s+"), " ")); buffer.clear() }
    fun visit(node: Node) {
        when (node) {
            is TextNode -> buffer.append(node.getWholeText())
            is Element -> {
                val block = node.tagName() in setOf("p", "div", "section", "h1", "h2", "h3", "li", "br", "mbp:pagebreak")
                if (block) flush()
                if (node.tagName() == "img") {
                    flush()
                    resolveImage(node.attr("src"))?.let { result += NovelBlock(image = it) }
                        ?: resolveImage(node.absUrl("src"))?.let { result += NovelBlock(image = it) }
                } else node.childNodes().forEach(::visit)
                if (block) flush()
            }
        }
    }
    visit(root); flush()
    return result
}
