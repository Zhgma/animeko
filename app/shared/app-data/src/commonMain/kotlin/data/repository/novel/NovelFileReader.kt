/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.novel

/** Assets are copied into the book's private storage; returned URIs never refer to the import picker. */
expect fun readNovelFile(name: String, bytes: ByteArray, saveAsset: (ByteArray) -> String): NovelDocument

internal fun textNovel(title: String, text: String): NovelDocument {
    val heading = Regex("^(第[零〇一二三四五六七八九十百千万两0-9]+[章回节卷部].{0,70}|(?:Chapter|CHAPTER)\\s+.{1,70}|序章|序言|前言|后记|尾声|终章)$")
    val groups = mutableListOf<Pair<String, MutableList<NovelBlock>>>()
    novelParagraphs(text.removePrefix("\uFEFF")).forEach { block ->
        if (heading.matches(block.text) || groups.isEmpty()) groups +=
            (if (heading.matches(block.text)) block.text else "正文") to mutableListOf<NovelBlock>()
        groups.last().second += block
    }
    require(groups.isNotEmpty()) { "文件没有可阅读的正文" }
    return NovelDocument(title, groups.mapIndexed { i, entry -> NovelChapter("local-$i", entry.first) }, groups.map { it.second })
}
