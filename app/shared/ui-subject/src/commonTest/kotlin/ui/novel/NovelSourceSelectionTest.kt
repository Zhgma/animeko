/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.novel

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceUntilIdle
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
    @Test fun firstResultIsSelectedBeforeOtherSearchesFinish() = runTest {
        val sources = listOf(NovelSource("first"), NovelSource("slow"), NovelSource("last"))
        val states = mutableMapOf<NovelSource, NovelSearchState>()
        val selections = mutableListOf<NovelBook>()
        val result = findFirstNovelSource(listOf("三体"), sources,
            { source, _ -> if (source == sources[1]) delay(60_000); listOf(NovelBook(source, "1", "三体")) },
            { source, state -> states[source] = state }, { book ->
                assertTrue(states.getValue(sources[1]).loading)
                selections += book
            })
        assertEquals(sources[0], result!!.source)
        assertEquals(listOf(result), selections)
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
    @Test fun firstReturnedBookUsesSourcePriorityEvenWithoutExactTitleMatch() = runTest {
        val sources = listOf(NovelSource("empty"), NovelSource("first"), NovelSource("fast"))
        val selected = mutableListOf<NovelBook>()
        val result = findFirstNovelSource(listOf("全职高手"), sources,
            { source, _ ->
                if (source == sources[1]) delay(1_000)
                if (source == sources[0]) emptyList()
                else listOf(NovelBook(source, "first", "全职高手 修订版"), NovelBook(source, "second", "Other"))
            }, { _, _ -> }, selected::add)
        assertEquals(sources[1], result!!.source)
        assertEquals("first", result.id)
        assertEquals(listOf(result), selected)
    }

    @Test fun manualSwitchCancelsPendingAutomaticSelection() = runTest {
        val selected = mutableListOf<NovelBook>()
        val job = launch {
            findFirstNovelSource(listOf("三体"), listOf(NovelSource.QUANBEN, NovelSource.HUANMENG),
                { source, _ -> delay(1_000); listOf(NovelBook(source, "auto", "三体")) },
                { _, _ -> }, selected::add)
        }
        runCurrent()
        job.cancelAndJoin()
        advanceUntilIdle()
        assertTrue(selected.isEmpty())
    }

    @Test fun timeoutFallsThroughButExternalCancellationDoesNot() = runTest {
        val sources = listOf(NovelSource("slow"), NovelSource("fast"))
        val errors = mutableListOf<String>()
        val result = findFirstNovelSource(listOf("三体"), sources,
            { source, _ -> if (source == sources[0]) delay(60_000); listOf(NovelBook(source, "book", "三体")) },
            { _, state -> state.error?.let(errors::add) }, {})
        assertEquals(sources[1], result!!.source)
        assertTrue(errors.any { "超时" in it })
        assertFailsWith<CancellationException> {
            findFirstNovelSource(listOf("三体"), sources, { _, _ -> throw CancellationException() }, { _, _ -> }, {})
        }
    }
}
