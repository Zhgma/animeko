/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.manga

import me.him188.ani.app.domain.mediasource.web.SolveRequest
import me.him188.ani.app.domain.mediasource.web.BlockedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import me.him188.ani.app.data.repository.manga.MangaChapter
import me.him188.ani.app.data.repository.manga.MangaSource
import me.him188.ani.app.data.repository.manga.MangaSourceBook

data class MangaSourceSearchState(
    val keyword: String = "", val books: List<MangaSourceBook> = emptyList(),
    val loading: Boolean = false, val error: String? = null,
    val captcha: SolveRequest? = null, val resolvingCaptcha: Boolean = false,
    val retryBook: MangaSourceBook? = null,
)

internal data class ReadableManga(val book: MangaSourceBook, val chapters: List<MangaChapter>)

/** Select only a matching title whose catalog and first page can actually be downloaded. */
internal suspend fun findReadableMangaSource(
    aliases: List<String>,
    search: suspend (MangaSource, String) -> List<MangaSourceBook>,
    chapters: suspend (MangaSourceBook) -> List<MangaChapter>,
    probe: suspend (MangaSourceBook, MangaChapter) -> Unit,
    progress: (MangaSource, MangaSourceSearchState) -> Unit,
    sources: List<MangaSource> = MangaSource.entries,
): ReadableManga? {
    val query = aliases.first()
    for (source in sources) {
        progress(source, MangaSourceSearchState(query, loading = true))
        var books = emptyList<MangaSourceBook>()
        try {
            var completed = false
            val result = withTimeoutOrNull(30_000) {
                books = search(source, query)
                val candidates = books.filter { book -> aliases.any { mangaTitlesMatch(book.title, it) } }
                var lastFailure: Exception? = null
                for (book in candidates.take(3)) {
                    try {
                        val catalog = chapters(book)
                        val first = catalog.firstOrNull { it.group == "章节" } ?: catalog.firstOrNull() ?: continue
                        probe(book, first)
                        completed = true
                        return@withTimeoutOrNull ReadableManga(book, catalog)
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { lastFailure = e }
                }
                lastFailure?.let { throw it }
                completed = true
                null
            }
            progress(source, MangaSourceSearchState(query, books,
                error = if (!completed) "来源检查超时，请重试或切换来源" else null))
            if (result != null) return result
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { progress(source, MangaSourceSearchState(query, books, error = e.message ?: "来源不可用", captcha = (e as? BlockedException)?.request)) }
    }
    return null
}

internal fun mangaTitlesMatch(title: String, alias: String): Boolean {
    fun normalized(value: String) = value.lowercase().filter { it.isLetterOrDigit() }
        .replace('藍', '蓝').replace('薰', '熏').replace('凜', '凛')
    val expected = normalized(alias)
    val actual = normalized(title)
    return expected.isNotEmpty() && (actual == expected || expected.length >= 4 && actual.endsWith(expected) &&
        actual.removeSuffix(expected).all { it in 'a'..'z' || it in '0'..'9' })
}

/** A source switch only resumes an unambiguous chapter in the same catalog group. */
internal fun matchingMangaChapter(previous: MangaChapter?, chapters: List<MangaChapter>): MangaChapter? {
    val number = previous?.number?.takeIf { it.isFinite() && it > 0 } ?: return null
    return chapters.singleOrNull { it.group == previous.group && it.number == number }
}
