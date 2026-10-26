/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.novel

import io.ktor.http.Url
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import me.him188.ani.app.data.repository.manga.MangaSubscriptionParser

/** Versioned, passive rules: new HTTP/CSS sources do not require another application build. */
@Serializable
data class NovelSourceDefinition(
    val key: String, val name: String, val home: String,
    val engine: String = "html", val reference: String = "",
    val unavailableReason: String = "",
    val searchUrl: String, val searchMethod: String = "GET", val searchBody: String = "",
    val searchList: String = "", val searchName: String = "", val searchLink: String = "", val searchAuthor: String = "",
    val tocUrl: String = "", val tocLink: String = "", val chapters: String = "",
    val chapterName: String = "a@text", val chapterLink: String = "a@href", val nextToc: String = "",
    val contentUrl: String = "", val content: String = "", val nextContent: String = "",
    val remove: String = "script,style,iframe,form,nav", val exclude: String = "",
    val headers: Map<String, String> = emptyMap(),
) {
    val source get() = NovelSource(key)
    fun validate(): NovelSourceDefinition {
        require(key.matches(Regex("[A-Za-z0-9_.-]{1,160}")) && key != "LOCAL" && name.isNotBlank()) { "书源名称或标识不正确" }
        NovelSubscriptionParser.validateUrl(home)
        if (engine == "zlibrary") ZlibraryApi.origin(home)
        require(engine in setOf("html", "quanben", "huanmeng", "zlibrary")) { "不支持的书源协议：$engine" }
        require(engine == "html" || key == engine.uppercase()) { "原生协议的书源标识不匹配" }
        require(searchMethod in setOf("GET", "POST")) { "仅支持 GET / POST 书源" }
        require(searchUrl.isNotBlank()) { "缺少搜索规则" }
        require(headers.keys.none { it.lowercase() in setOf("cookie", "authorization", "proxy-authorization", "host") }) { "订阅不能携带账号凭据" }
        if (engine == "html") {
            require(listOf(searchList, searchName, searchLink, chapters, content).all { it.isNotBlank() }) { "缺少搜索、目录或正文规则" }
            listOf(searchList, searchName, searchLink, searchAuthor, tocLink, chapters, chapterName, chapterLink, nextToc, content, nextContent).forEach {
                require(it.length <= 2000 && !it.contains(Regex("(?i)<js>|@js|@json|javascript:|webview|^//|^/"))) { "此书源需要暂不支持的动态或 XPath 规则" }
            }
        }
        require(listOf(searchUrl, searchBody, tocUrl, contentUrl).none { it.contains(Regex("(?i)<js>|@js|javascript:")) }) { "不支持执行书源脚本" }
        return this
    }
}

object NovelSubscriptionParser {
    private val json = Json { ignoreUnknownKeys = true }
    fun validateUrl(value: String) = MangaSubscriptionParser.validateUrl(value)
    fun parse(text: String): NovelSubscriptionContents {
        require(text.length <= 4 * 1024 * 1024) { "订阅文件超过 4 MB" }
        val root = json.parseToJsonElement(text.trim().removePrefix("\uFEFF"))
        val entries = when (root) {
            is JsonArray -> root
            is JsonObject -> root["exportedMediaSourceDataList"]?.jsonObject?.get("mediaSources")?.jsonArray
                ?: error("请使用小说 sub 或阅读书源 JSON 链接")
            else -> error("订阅格式不正确")
        }
        require(entries.size <= 500) { "订阅最多支持 500 个来源" }
        val sources = mutableListOf<NovelSourceDefinition>()
        val unsupported = mutableListOf<String>()
        for (item in entries) {
            val entry = item.jsonObject
            val name = entry["bookSourceName"]?.jsonPrimitive?.content ?: entry["arguments"]?.jsonObject?.get("name")?.jsonPrimitive?.content ?: "未知书源"
            try {
                val source = if ("bookSourceUrl" in entry) legado(entry) else {
                    require(entry["factoryId"]?.jsonPrimitive?.content == "novel-rules" && entry["version"]?.jsonPrimitive?.int == 1) { "不支持的协议版本" }
                    json.decodeFromJsonElement<NovelSourceDefinition>(entry.getValue("arguments"))
                }
                sources += source.validate()
            } catch (e: Exception) { unsupported += "$name：${e.message}" }
        }
        require(entries.isEmpty() || sources.isNotEmpty()) { unsupported.joinToString("；").take(500).ifBlank { "没有可支持的小说源" } }
        require(sources.map { it.key }.distinct().size == sources.size) { "订阅包含重复书源标识" }
        return NovelSubscriptionContents(sources, unsupported)
    }

    private fun legado(entry: JsonObject): NovelSourceDefinition {
        fun s(key: String) = entry[key]?.jsonPrimitive?.content.orEmpty()
        fun rule(group: String, key: String) = entry[group]?.jsonObject?.get(key)?.jsonPrimitive?.content.orEmpty()
        val home = validateUrl(s("bookSourceUrl"))
        // These publishers use scripts for static protocol details already represented by our maintained rules.
        val known = DefaultNovelSources.all.firstOrNull { Url(it.home).host == Url(home).host }
        if (known != null) return known
        require(s("loginUrl").isBlank()) { "需要登录的书源未导入" }
        val raw = s("searchUrl").replace("\n", " ")
        val parts = raw.split(Regex(",\\s*(?=\\{)"), limit = 2)
        val options = parts.getOrNull(1)?.let { json.parseToJsonElement(it.replace('\'', '"')).jsonObject }
        val key = Url(home).host.lowercase().replace(Regex("[^a-z0-9.-]"), "-")
        return NovelSourceDefinition(key, s("bookSourceName"), home,
            searchUrl = parts[0], searchMethod = options?.get("method")?.jsonPrimitive?.content?.uppercase() ?: "GET",
            searchBody = options?.get("body")?.jsonPrimitive?.content.orEmpty(),
            searchList = rule("ruleSearch", "bookList"), searchName = rule("ruleSearch", "name"),
            searchLink = rule("ruleSearch", "bookUrl"), searchAuthor = rule("ruleSearch", "author"),
            tocLink = rule("ruleBookInfo", "tocUrl"), chapters = rule("ruleToc", "chapterList"),
            chapterName = rule("ruleToc", "chapterName"), chapterLink = rule("ruleToc", "chapterUrl"), nextToc = rule("ruleToc", "nextTocUrl"),
            content = rule("ruleContent", "content"), nextContent = rule("ruleContent", "nextContentUrl"))
    }
}
