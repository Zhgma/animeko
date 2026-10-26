/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.novel

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
): ReadableNovel? = coroutineScope {
    val query = aliases.firstOrNull { it.isNotBlank() } ?: return@coroutineScope null
    val permits = Semaphore(3)
    val searches = sources.associateWith { source ->
        progress(source, NovelSearchState(query, loading = true))
        async {
            permits.withPermit {
                val result = searchNovelSource(source, aliases, search)
                progress(source, result)
                result
            }
        }
    }
    var selected: ReadableNovel? = null
    for (source in sources) {
        val searched = searches.getValue(source).await()
        if (selected != null || searched.error != null) continue
        val books = searched.books
        try {
            var completed = false
            val result = withTimeoutOrNull(30_000) {
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
            if (result != null) selected = result
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { progress(source, NovelSearchState(query, books, error = e.message ?: "来源不可用")) }
    }
    selected
}

/** Search every source independently; alternate titles are tried when the first query has no exact match. */
internal suspend fun searchNovelSource(
    source: NovelSource, aliases: List<String>, search: suspend (NovelSource, String) -> List<NovelBook>,
): NovelSearchState {
    val query = aliases.firstOrNull { it.isNotBlank() }.orEmpty()
    val titles = aliases.filter { it.isNotBlank() }.distinct().take(3)
    val numberedTitle = Regex("^\\d{2,}(?=[^\\p{L}\\p{N}])").find(query)?.value
    val queries = (titles + listOfNotNull(numberedTitle)).distinct()
    var books = emptyList<NovelBook>()
    return try {
        val completed = withTimeoutOrNull(30_000) {
            for (keyword in queries) {
                books = (books + search(source, keyword)).distinctBy { it.id }
                if (books.any { book -> aliases.any { mangaTitlesMatch(book.title, it) } }) break
            }
            true
        }
        val ranked = books.sortedBy { book -> if (aliases.any { mangaTitlesMatch(book.title, it) }) 0 else 1 }
        NovelSearchState(query, ranked.take(NOVEL_SEARCH_RESULT_LIMIT), error = if (completed == true) null else "搜索超时，请重试")
    } catch (e: CancellationException) { throw e }
    catch (e: Exception) { NovelSearchState(query, books.take(NOVEL_SEARCH_RESULT_LIMIT), error = e.message ?: "搜索失败") }
}
