/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.novel

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.repository.novel.*
import kotlin.test.*

class NovelSourceSelectionTest {
    @Test fun automaticSelectionRespectsPriorityAndOnlyBindsReadableMatchingBooks() = runTest {
        val sources = listOf(NovelSource("unrelated"), NovelSource("broken"), NovelSource("working"))
        val checked = mutableListOf<NovelSource>()
        val states = mutableMapOf<NovelSource, NovelSearchState>()
        val result = findReadableNovelSource(listOf("全职高手"), sources,
            { source, _ -> checked += source; listOf(NovelBook(source, "book", if (source == sources[0]) "同人全职高手" else "全职高手")) },
            { listOf(NovelChapter("chapter", "第一章")) },
            { book, _ -> if (book.source == sources[1]) error("正文加载失败") },
            { source, state -> states[source] = state })
        assertEquals(sources, checked)
        assertEquals(sources[2], result!!.book.source)
        assertEquals("正文加载失败", states[sources[1]]!!.error)
    }
    @Test fun timeoutFallsThroughButExternalCancellationDoesNot() = runTest {
        val sources = listOf(NovelSource("slow"), NovelSource("fast"))
        val errors = mutableListOf<String>()
        val result = findReadableNovelSource(listOf("三体"), sources,
            { source, _ -> if (source == sources[0]) delay(60_000); listOf(NovelBook(source, "book", "三体")) },
            { listOf(NovelChapter("chapter", "第一章")) }, { _, _ -> },
            { _, state -> state.error?.let(errors::add) })
        assertEquals(sources[1], result!!.book.source)
        assertTrue(errors.any { "超时" in it })
        assertFailsWith<CancellationException> {
            findReadableNovelSource(listOf("三体"), sources, { _, _ -> throw CancellationException() }, { emptyList() }, { _, _ -> }, { _, _ -> })
        }
    }
}
