/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.manga

import me.him188.ani.app.domain.mediasource.web.SolveRequest
import me.him188.ani.app.domain.mediasource.web.BlockedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import me.him188.ani.app.data.repository.manga.MangaChapter
import me.him188.ani.app.data.repository.manga.MangaSource
import me.him188.ani.app.data.repository.manga.MangaSourceBook
import me.him188.ani.app.data.repository.manga.MangaSourceSettings

data class MangaSourceSearchState(
    val keyword: String = "", val books: List<MangaSourceBook> = emptyList(),
    val loading: Boolean = false, val error: String? = null,
    val captcha: SolveRequest? = null, val resolvingCaptcha: Boolean = false,
    val retryBook: MangaSourceBook? = null,
)

internal const val MANGA_AUTO_MATCH_RESULT_LIMIT = 10

/** Search all enabled sources independently, selecting once in source priority order. */
internal suspend fun findFirstMangaSource(
    aliases: List<String>,
    search: suspend (MangaSource, String) -> List<MangaSourceBook>,
    progress: (MangaSource, MangaSourceSearchState) -> Unit,
    sources: List<MangaSource> = MangaSourceSettings().enabled,
    select: (MangaSourceBook) -> Unit = {},
): MangaSourceBook? = coroutineScope {
    val query = aliases.firstOrNull { it.isNotBlank() } ?: return@coroutineScope null
    val permits = Semaphore(3)
    val searches = sources.associateWith { source ->
        progress(source, MangaSourceSearchState(query, loading = true))
        async {
            permits.withPermit {
                searchMangaSource(source, query, search).also { progress(source, it) }
            }
        }
    }
    var selected: MangaSourceBook? = null
    for (source in sources) {
        val result = searches.getValue(source).await()
        if (selected == null) result.books.firstOrNull()?.let { book ->
            selected = book
            select(book)
        }
    }
    selected
}

internal suspend fun searchMangaSource(
    source: MangaSource, query: String, search: suspend (MangaSource, String) -> List<MangaSourceBook>,
): MangaSourceSearchState = try {
    val books = withTimeoutOrNull(30_000) { search(source, query).take(MANGA_AUTO_MATCH_RESULT_LIMIT) }
    MangaSourceSearchState(query, books.orEmpty(),
        error = if (books == null) "来源搜索超时，请重试或切换来源" else null)
} catch (e: CancellationException) { throw e }
catch (e: Exception) { MangaSourceSearchState(query, error = e.message ?: "来源不可用",
    captcha = (e as? BlockedException)?.request) }

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
