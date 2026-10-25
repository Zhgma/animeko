/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.novel

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.parser.Parser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.net.URLDecoder
import java.util.zip.ZipInputStream

private const val MAX_FILE_BYTES = 96 * 1024 * 1024
internal const val MAX_NOVEL_TEXT = 64 * 1024 * 1024

actual fun readNovelFile(name: String, bytes: ByteArray, saveAsset: (ByteArray) -> String): NovelDocument {
    require(bytes.size <= MAX_FILE_BYTES) { "文件过大，最大支持 96 MB" }
    val title = name.substringBeforeLast('.')
    return when (name.substringAfterLast('.', "").lowercase()) {
        "txt" -> textNovel(title, decodeNovelText(bytes))
        "epub" -> readEpub(title, bytes, saveAsset)
        "mobi", "azw3", "azw" -> readMobi(title, bytes, saveAsset)
        else -> error("请选择 TXT、EPUB、MOBI 或 AZW3 文件")
    }
}

internal fun decodeNovelText(bytes: ByteArray): String {
    if (bytes.take(2) == listOf(0xff.toByte(), 0xfe.toByte())) return String(bytes, Charsets.UTF_16LE).removePrefix("\uFEFF")
    if (bytes.take(2) == listOf(0xfe.toByte(), 0xff.toByte())) return String(bytes, Charsets.UTF_16BE).removePrefix("\uFEFF")
    return try { Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF") }
    catch (_: CharacterCodingException) { String(bytes, Charset.forName("GB18030")) }
}

private fun archive(bytes: ByteArray): Map<String, ByteArray> {
    val entries = linkedMapOf<String, ByteArray>()
    var total = 0
    ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            require(entries.size < 20_000) { "电子书文件数量过多" }
            if (entry.isDirectory) continue
            val path = safeBookPath("", entry.name)
            require(path !in entries) { "电子书包含重复文件" }
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = zip.read(buffer)
                if (count < 0) break
                total += count
                require(total <= 192 * 1024 * 1024 && output.size() + count <= MAX_NOVEL_TEXT) { "电子书解压后过大" }
                output.write(buffer, 0, count)
            }
            entries[path] = output.toByteArray()
        }
    }
    return entries
}

internal fun safeBookPath(base: String, href: String): String {
    require(!href.contains('\\') && !href.contains('\u0000') && !href.startsWith('/') && !href.contains("://")) { "电子书资源路径无效" }
    val clean = URLDecoder.decode(href.substringBefore('#').substringBefore('?').replace("+", "%2B"), "UTF-8")
    val result = base.substringBeforeLast('/', "").split('/').filter { it.isNotEmpty() }.toMutableList()
    clean.split('/').forEach { part -> when (part) {
        "", "." -> Unit
        ".." -> { require(result.isNotEmpty()) { "电子书资源越过根目录" }; result.removeAt(result.lastIndex) }
        else -> { require(!part.contains(':') && !part.contains('\\')) { "电子书资源路径无效" }; result += part }
    } }
    return result.joinToString("/")
}

private fun readEpub(fallbackTitle: String, bytes: ByteArray, saveAsset: (ByteArray) -> String): NovelDocument {
    val files = archive(bytes)
    check("META-INF/encryption.xml" !in files || !String(files.getValue("META-INF/encryption.xml"), Charsets.UTF_8).contains("EncryptedData")) {
        "这本 EPUB 含加密资源，请导入未加密版本"
    }
    val container = files["META-INF/container.xml"] ?: error("缺少 EPUB 容器信息")
    val root = Ksoup.parse(String(container, Charsets.UTF_8), Parser.xmlParser(), "").selectFirst("rootfile")?.attr("full-path")
        ?: error("缺少 EPUB 正文目录")
    val opfPath = safeBookPath("", root)
    val opf = Ksoup.parse(String(files[opfPath] ?: error("EPUB 主文件不存在"), Charsets.UTF_8), Parser.xmlParser(), "")
    val title = opf.getElementsByTag("dc:title").firstOrNull()?.text()?.ifBlank { null } ?: fallbackTitle
    val manifest = opf.select("manifest item").associateBy { it.attr("id") }
    val labels = mutableMapOf<String, String>()
    manifest.values.filter { it.attr("properties").split(' ').contains("nav") || it.attr("media-type").contains("ncx") }.forEach { entry ->
        val path = safeBookPath(opfPath, entry.attr("href"))
        val nav = files[path]?.let { Ksoup.parse(String(it, Charsets.UTF_8), Parser.xmlParser(), "") } ?: return@forEach
        nav.select("a[href]").forEach { a -> labels.putIfAbsent(safeBookPath(path, a.attr("href")), a.text()) }
        nav.select("navPoint").forEach { point -> point.selectFirst("content")?.attr("src")?.let {
            labels.putIfAbsent(safeBookPath(path, it), point.selectFirst("navLabel")?.text().orEmpty())
        } }
    }
    val chapters = mutableListOf<NovelChapter>()
    val contents = mutableListOf<List<NovelBlock>>()
    val assets = mutableMapOf<String, String>()
    opf.select("spine itemref").filter { it.attr("linear") != "no" }.forEach { ref ->
        val entry = manifest[ref.attr("idref")] ?: error("EPUB 阅读顺序引用不存在的文件")
        val path = safeBookPath(opfPath, entry.attr("href"))
        val html = files[path] ?: error("EPUB 章节不存在：$path")
        val doc = Ksoup.parse(decodeNovelText(html))
        val blocks = htmlBlocks(doc.body(), "") { image ->
            if (image.isBlank() || image.contains(":") || image.startsWith('/')) null else {
                val asset = safeBookPath(path, image)
                files[asset]?.let { data -> assets.getOrPut(asset) { saveAsset(data) } }
            }
        }
        if (blocks.isNotEmpty()) {
            chapters += NovelChapter(path, labels[path]?.takeIf { it.isNotBlank() }
                ?: doc.selectFirst("h1,h2,h3,title")?.text()?.takeIf { it.isNotBlank() } ?: "第 ${chapters.size + 1} 节")
            contents += blocks
        }
    }
    require(chapters.isNotEmpty()) { "EPUB 没有可阅读的正文" }
    return NovelDocument(title, chapters, contents)
}
