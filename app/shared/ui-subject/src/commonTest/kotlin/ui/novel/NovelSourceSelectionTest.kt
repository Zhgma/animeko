/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.novel

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.repository.novel.*
import kotlin.test.*

class NovelSourceSelectionTest {
    @Test fun numberedLightNovelCanUseShortTitleWhenSiteRejectsSubtitle() = runTest {
        val queries = mutableListOf<String>()
        val result = searchNovelSource(NovelSource.HUANMENG, listOf("86―不存在的战区―")) { source, query ->
            queries += query
            if (query == "86") listOf(NovelBook(source, "86", "86-不存在的战区--86- Eighty Six --")) else emptyList()
        }
        assertEquals(listOf("86―不存在的战区―", "86"), queries)
        assertEquals("86", result.books.single().id)
    }
    @Test fun allSourcesFinishSearchingEvenWhenTheFirstIsReadable() = runTest {
        val sources = listOf(NovelSource("first"), NovelSource("slow"), NovelSource("last"))
        val states = mutableMapOf<NovelSource, NovelSearchState>()
        val probed = mutableListOf<NovelSource>()
        val result = findReadableNovelSource(listOf("三体"), sources,
            { source, _ -> if (source == sources[1]) delay(60_000); listOf(NovelBook(source, "1", "三体")) },
            { book -> probed += book.source; listOf(NovelChapter("1", "第一章")) }, { _, _ -> },
            { source, state -> states[source] = state })
        assertEquals(sources[0], result!!.book.source)
        assertEquals(listOf(sources[0]), probed)
        assertEquals("三体", states.getValue(sources[2]).books.single().title)
        assertTrue(states.values.none { it.loading })
        assertContains(states.getValue(sources[1]).error!!, "超时")
    }

    @Test fun alternateTitleFindsBooksAndResultsStayBounded() = runTest {
        val source = NovelSource.HUANMENG
        val queries = mutableListOf<String>()
        val result = searchNovelSource(source, listOf("游戏人生", "NO GAME NO LIFE")) { _, query ->
            queries += query
            if (query == "游戏人生") (1..30).map { NovelBook(source, "$it", "无关作品$it") }
            else listOf(NovelBook(source, "match", "NO GAME NO LIFE"))
        }
        assertEquals(listOf("游戏人生", "NO GAME NO LIFE"), queries)
        assertEquals(NOVEL_SEARCH_RESULT_LIMIT, result.books.size)
        assertEquals("match", result.books.first().id)
    }
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
