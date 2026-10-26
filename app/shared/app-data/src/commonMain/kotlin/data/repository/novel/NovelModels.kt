/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.novel

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class NovelSource(val name: String) {
    val title: String get() = when (this) { QUANBEN -> "全本小说"; HUANMENG -> "幻梦轻小说"; ZLIBRARY -> "Z-Library"; LOCAL -> "本地文件"; else -> name }
    override fun toString() = name
    companion object {
        val QUANBEN = NovelSource("QUANBEN")
        val HUANMENG = NovelSource("HUANMENG")
        val LOCAL = NovelSource("LOCAL")
        val ZLIBRARY = NovelSource("ZLIBRARY")
    }
}

@Serializable
data class NovelBook(val source: NovelSource, val id: String, val title: String, val author: String = "", val edition: String = "")

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
    val paragraph: Int = 0, val offset: Int = 0, val sourcePinned: Boolean = true,
)

/** Stable paragraph boundaries are independent of screen width and font settings. */
fun novelParagraphs(text: String): List<NovelBlock> = text.replace("\r\n", "\n").replace('\r', '\n')
    .lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.flatMap { it.chunked(1500) }
    .map { NovelBlock(it) }.toList()
