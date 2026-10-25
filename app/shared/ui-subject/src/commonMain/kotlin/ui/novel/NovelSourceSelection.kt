/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.novel

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import me.him188.ani.app.data.repository.novel.*
import me.him188.ani.app.ui.manga.mangaTitlesMatch

internal data class NovelSearchState(val keyword: String = "", val books: List<NovelBook> = emptyList(), val loading: Boolean = false, val error: String? = null)
internal data class ReadableNovel(val book: NovelBook, val chapters: List<NovelChapter>)

internal suspend fun findReadableNovelSource(
    aliases: List<String>, sources: List<NovelSource>,
    search: suspend (NovelSource, String) -> List<NovelBook>,
    chapters: suspend (NovelBook) -> List<NovelChapter>,
    probe: suspend (NovelBook, NovelChapter) -> Unit,
    progress: (NovelSource, NovelSearchState) -> Unit,
): ReadableNovel? {
    val query = aliases.firstOrNull { it.isNotBlank() } ?: return null
    for (source in sources) {
        var books = emptyList<NovelBook>()
        progress(source, NovelSearchState(query, loading = true))
        try {
            var completed = false
            val result = withTimeoutOrNull(30_000) {
                books = search(source, query)
                var lastError: Exception? = null
                for (book in books.filter { book -> aliases.any { mangaTitlesMatch(book.title, it) } }.take(3)) {
                    try {
                        val catalog = chapters(book)
                        val first = catalog.firstOrNull() ?: continue
                        probe(book, first)
                        completed = true
                        return@withTimeoutOrNull ReadableNovel(book, catalog)
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { lastError = e }
                }
                lastError?.let { throw it }
                completed = true
                null
            }
            progress(source, NovelSearchState(query, books, error = if (completed) null else "来源检查超时，请重试或切换来源"))
            if (result != null) return result
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { progress(source, NovelSearchState(query, books, error = e.message ?: "来源不可用")) }
    }
    return null
}
