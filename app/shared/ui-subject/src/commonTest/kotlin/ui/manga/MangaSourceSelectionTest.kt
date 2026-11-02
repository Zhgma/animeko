/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.manga

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.repository.manga.MangaSource
import me.him188.ani.app.data.repository.manga.MangaSourceBook
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MangaSourceSelectionTest {
    @Test
    fun firstResultIsSelectedAndReportedResultsAreCapped() = runTest {
        val statuses = mutableMapOf<MangaSource, MangaSourceSearchState>()
        val searched = mutableListOf<MangaSource>()
        val result = findFirstMangaSource(listOf("Target"),
            { source, _ ->
                searched += source
                (1..12).map { MangaSourceBook(source, "$it", "Returned title $it") }
            }, { source, state -> statuses[source] = state }, listOf(MangaSource.GODA, MangaSource.MANHUAGUI))
        assertEquals("1", result?.id)
        assertEquals(listOf(MangaSource.GODA, MangaSource.MANHUAGUI), searched)
        val status = statuses.getValue(MangaSource.GODA)
        assertEquals((1..10).map(Int::toString), status.books.map { it.id })
        assertNull(status.error)
        assertFalse(status.loading)
        assertEquals(10, statuses.getValue(MangaSource.MANHUAGUI).books.size)
        assertFalse(statuses.getValue(MangaSource.MANHUAGUI).loading)
    }

    @Test
    fun selectsBeforeSlowSourcesFinishButEveryEnabledSourceCompletes() = runTest {
        val sources = listOf(MangaSource.MANHUAGUI, MangaSource.GODA, MangaSource.CCC,
            MangaSource.MANHUAREN, MangaSource.COMICK)
        val states = mutableMapOf<MangaSource, MangaSourceSearchState>()
        val selected = mutableListOf<MangaSourceBook>()
        var active = 0
        var peak = 0
        val result = findFirstMangaSource(listOf("Test"), { source, _ ->
            active++
            peak = maxOf(peak, active)
            try {
                delay(if (source == sources.first()) 10 else 100)
                if (source == MangaSource.CCC) error("HTTP 503")
                listOf(MangaSourceBook(source, "first", "Test"))
            } finally { active-- }
        }, { source, state -> states[source] = state }, sources, { book ->
            assertTrue(states.getValue(MangaSource.GODA).loading)
            selected += book
        })
        assertEquals(sources.first(), result?.source)
        assertEquals(listOf(result), selected)
        assertEquals(3, peak)
        assertEquals(sources.toSet(), states.keys)
        assertTrue(states.values.none { it.loading })
        assertEquals("HTTP 503", states.getValue(MangaSource.CCC).error)
        assertEquals("first", states.getValue(MangaSource.COMICK).books.single().id)
    }

    @Test
    fun titleMatchingDistinguishesRelatedButDifferentWorks() {
        assertTrue(mangaTitlesMatch("GRANDBLUE碧藍之海", "碧蓝之海"))
        assertFalse(mangaTitlesMatch("同人碧蓝之海", "碧蓝之海"))
        assertFalse(mangaTitlesMatch("碧蓝之海公式书", "碧蓝之海"))
    }

    @Test
    fun failedAndEmptySourcesFallThroughInPriorityOrder() = runTest {
        val statuses = mutableMapOf<MangaSource, MangaSourceSearchState>()
        val result = findFirstMangaSource(listOf("Test"), { source, _ ->
            when (source) {
                MangaSource.GODA -> error("HTTP 429")
                MangaSource.MANHUAGUI -> emptyList()
                else -> listOf(MangaSourceBook(source, "first", "Test"))
            }
        }, { source, state -> statuses[source] = state },
            listOf(MangaSource.GODA, MangaSource.MANHUAGUI, MangaSource.MANHUAREN))
        assertEquals(MangaSource.MANHUAREN, result?.source)
        assertEquals("HTTP 429", statuses.getValue(MangaSource.GODA).error)
        assertTrue(statuses.getValue(MangaSource.MANHUAGUI).books.isEmpty())
    }

    @Test
    fun timeoutFallsThroughAndExternalCancellationStopsSelection() = runTest {
        val statuses = mutableMapOf<MangaSource, MangaSourceSearchState>()
        val result = findFirstMangaSource(listOf("Test"), { source, _ ->
            if (source == MangaSource.GODA) delay(40_000)
            listOf(MangaSourceBook(source, "first", "Test"))
        }, { source, state -> statuses[source] = state }, listOf(MangaSource.GODA, MangaSource.MANHUAGUI))
        assertEquals(MangaSource.MANHUAGUI, result?.source)
        assertTrue(statuses.getValue(MangaSource.GODA).error!!.contains("超时"))
        var searches = 0
        assertFailsWith<CancellationException> {
            findFirstMangaSource(listOf("Test"), { _, _ -> searches++; throw CancellationException() }, { _, _ -> }, listOf(MangaSource.GODA))
        }
        assertEquals(1, searches)
    }
}
