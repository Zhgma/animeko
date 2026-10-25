/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.novel

import kotlinx.serialization.Serializable

@Serializable
enum class NovelSource(val title: String, val subscriptionUrl: String) {
    QUANBEN("全本小说", "https://shuyuan-api.yiove.com/import/book-source/b4f96ebd-8fc7-451e-84bb-e8cf4af1b0fa"),
    HUANMENG("幻梦轻小说", "https://shuyuan-api.yiove.com/import/book-source/eeaf7376-5b54-47ef-aade-c0d0cfed4843"),
    LOCAL("本地文件", ""),
}

@Serializable
data class NovelBook(val source: NovelSource, val id: String, val title: String, val author: String = "")

@Serializable
data class NovelChapter(val id: String, val title: String, val number: Int? = null)

@Serializable
data class NovelBlock(val text: String = "", val image: String? = null)

@Serializable
data class NovelDocument(val title: String, val chapters: List<NovelChapter>, val contents: List<List<NovelBlock>>)

@Serializable
data class NovelReaderSettings(val fontSize: Int = 20, val lineHeight: Float = 1.7f, val theme: Int = 0)

@Serializable
data class NovelMemory(
    val book: NovelBook, val chapters: List<NovelChapter>, val chapterId: String? = null,
    val paragraph: Int = 0, val offset: Int = 0,
)

/** Stable paragraph boundaries are independent of screen width and font settings. */
fun novelParagraphs(text: String): List<NovelBlock> = text.replace("\r\n", "\n").replace('\r', '\n')
    .lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.flatMap { it.chunked(1500) }
    .map { NovelBlock(it) }.toList()
