/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.novel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.repository.manga.MangaCollection
import me.him188.ani.app.data.repository.novel.NovelChapter
import me.him188.ani.app.ui.manga.MangaDetailsContent
import me.him188.ani.app.ui.manga.MangaDetailsUiState
import me.him188.ani.app.ui.rating.EditableRatingActions
import me.him188.ani.app.ui.subject.details.sections.PagedEpisodesGrid
import me.him188.ani.app.ui.subject.details.sections.SectionHeader
import me.him188.ani.app.ui.subject.episode.list.EpisodeListItem
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.topic.UnifiedCollectionType

/** Book metadata uses the shared subject layout; reading actions use the novel catalog. */
@Composable
internal fun NovelDetailsContent(
    book: MangaCollection, details: MangaDetailsUiState, state: NovelState,
    onBack: () -> Unit, onRetry: () -> Unit, onRead: () -> Unit,
    onChapter: (NovelChapter) -> Unit, onSources: () -> Unit, onChapters: () -> Unit,
    onImport: () -> Unit, onFiles: () -> Unit, onCache: () -> Unit,
    modifier: Modifier = Modifier,
    ratingActions: EditableRatingActions = EditableRatingActions.Noop,
    onRelations: () -> Unit = {}, onRetryRelations: () -> Unit = {}, onRelatedSubject: (Int) -> Unit = {},
) {
    MangaDetailsContent(
        book, details, book.title, onBack, onRetry, {}, { _, _ -> }, {}, {}, modifier,
        ratingActions = ratingActions, onRelations = onRelations, onRetryRelations = onRetryRelations,
        onRelatedSubject = onRelatedSubject, contentLabel = "小说", chapterUnit = "章", testTagPrefix = "novel",
        readingAction = {
            Button(if (state.chapters.isEmpty()) onSources else onRead,
                modifier = Modifier.fillMaxWidth().testTag("novel-read"), enabled = !state.loading) {
                Text(if (state.chapters.isEmpty()) "选择书源" else "继续阅读")
            }
        },
        chapterContent = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                details.libraryError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                details.ratingError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (state.readAhead.isNotBlank()) Text(state.readAhead, style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onImport, Modifier.testTag("novel-import"), enabled = !state.loading) { Text("导入电子书") }
                    if (state.localBooks.isNotEmpty()) TextButton(onFiles) { Text("已导入文件") }
                    TextButton(onChapters, enabled = state.chapters.isNotEmpty()) { Text("完整目录（${state.chapters.size}）") }
                }
                NovelChapterGrid(state, details.library?.chapters ?: 0, onChapter, onChapters, onSources, onCache)
            }
        },
    )
}

@Composable
private fun NovelChapterGrid(
    state: NovelState, readCount: Int, onChapter: (NovelChapter) -> Unit,
    onChapters: () -> Unit, onSources: () -> Unit, onCache: () -> Unit,
) {
    var descending by rememberSaveable(state.book?.id, state.book?.source) { mutableStateOf(false) }
    val chapters = if (descending) state.chapters.reversed() else state.chapters
    val items = chapters.mapIndexed { index, chapter ->
        EpisodeListItem(index, EpisodeSort(chapter.number?.toString().orEmpty()), null, chapter.title, chapter.title,
            if (chapter.number?.let { it <= readCount } == true) UnifiedCollectionType.DONE else UnifiedCollectionType.NOT_COLLECTED,
            true)
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton({ descending = !descending }, Modifier.testTag("novel-chapter-sort")) { Text(if (descending) "倒序" else "正序") }
        TextButton(onCache, Modifier.testTag("novel-cache-open")) { Text("缓存") }
        TextButton(onSources, Modifier.testTag("novel-sources-open")) { Text("数据源") }
    }
    key(state.book?.source, state.book?.id, descending) {
        PagedEpisodesGrid(items, chapters.indexOfFirst { it.id == state.chapter?.id }.takeIf { it >= 0 },
            { onChapter(chapters[it.episodeId]) }, { onChapters() }, rowsPerPage = 2, showImages = false,
            header = { pager -> SectionHeader("章节") {
                pager?.invoke()
            } })
    }
}
