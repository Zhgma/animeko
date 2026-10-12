/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.manga

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.repository.manga.MangaSource
import me.him188.ani.app.data.repository.manga.MangaSourceBook
import me.him188.ani.app.ui.mediaselect.manual.ManualBrowseSource
import me.him188.ani.app.ui.mediaselect.manual.ManualSourceSelector
import me.him188.ani.app.ui.mediaselect.selector.MediaSelectorWebSourcesColumn
import me.him188.ani.app.ui.mediaselect.selector.WebSource
import me.him188.ani.app.ui.mediaselect.selector.WebSourceChannel
import me.him188.ani.datasources.api.source.MediaSourceInfo

/** Uses the anime selector's dropdown and source rows in compact and wide manga hosts. */
@Composable
internal fun MangaSourcePanel(
    state: MangaDetailsUiState, keyword: String, onKeyword: (String) -> Unit,
    onSearch: (MangaSource, String) -> Unit, onBook: (MangaSourceBook) -> Unit,
    onAutoSelect: () -> Unit,
) {
    var source by remember(state.source, state.sourceSettings) { mutableStateOf(state.source.takeIf { it in state.sourceSettings.enabled } ?: state.sourceSettings.enabled.firstOrNull() ?: state.source) }
    val actions = LocalMangaActions.current
    val choices = remember(state.sourceSettings) {
        state.sourceSettings.enabled.map { ManualBrowseSource(it.name, it.name,
            MediaSourceInfo(displayName = it.title, websiteUrl = it.home, iconUrl = it.iconUrl)) }
    }
    val booksBySource = MangaSource.available.associateWith { candidate ->
        state.sourceSearches[candidate]?.books ?: if (candidate == state.source) state.results else emptyList()
    }
    val rows = state.sourceSettings.enabled.map { candidate ->
        val result = state.sourceSearches[candidate]
        val books = booksBySource.getValue(candidate)
        WebSource(candidate.name, candidate.name, candidate.iconUrl, candidate.title,
            books.map { book -> WebSourceChannel(if (books.count { it.title == book.title } > 1) "${book.title} · #${book.id}" else book.title) }, isLoading = result?.loading == true,
            isError = result?.error != null, isPreferred = state.book?.source == candidate,
            captchaRequest = result?.captcha, isResolvingCaptcha = result?.resolvingCaptcha == true, isCaptchaSupported = state.captchaSupported)
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.testTag("manga-source-panel")) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onAutoSelect, enabled = !state.autoSelecting && choices.isNotEmpty(), modifier = Modifier.testTag("manga-auto-source")) { Text("自动选择") }
            Text(if (state.autoSelecting) "正在检查可下载的来源…"
                else if (state.memory?.sourcePinned == true) "此漫画已记住手动选择"
                else "首次自动选择，手动修改后记住来源", style = MaterialTheme.typography.bodySmall)
        }
        if (state.autoSelecting) LinearProgressIndicator(Modifier.fillMaxWidth())
        ManualSourceSelector(choices, source.name, { id ->
            source = MangaSource.valueOf(id)
            if (keyword.isNotBlank() && state.sourceSearches[source]?.keyword != keyword) onSearch(source, keyword)
        })
        OutlinedTextField(keyword, onKeyword, Modifier.fillMaxWidth().testTag("manga-source-query"),
            label = { Text("作品名称或作品链接") }, singleLine = true)
        if (choices.isEmpty()) Text("没有已启用的漫画源，请在设置 → 数据源中添加或启用漫画订阅。")
        TextButton({ onSearch(source, keyword) }, enabled = keyword.isNotBlank() && choices.isNotEmpty(), modifier = Modifier.testTag("manga-source-search")) { Text("搜索 / 重试") }
        MediaSelectorWebSourcesColumn(rows,
            selectedSource = { rows.firstOrNull { it.instanceId == state.book?.source?.name } },
            selectedChannel = { state.book?.let { book ->
                rows.firstOrNull { it.instanceId == book.source.name }?.channels?.getOrNull(booksBySource.getValue(book.source).indexOfFirst { it.id == book.id })
            } },
            onSelect = { row, channel ->
                val selected = MangaSource.valueOf(row.instanceId)
                booksBySource.getValue(selected).getOrNull(row.channels.indexOf(channel))?.let(onBook)
            },
            onRefresh = { onSearch(MangaSource.valueOf(it.instanceId), keyword) },
            onResolveCaptcha = { actions.solveCaptcha(MangaSource.valueOf(it.instanceId)) }, onRequestManualSearch = null)
        for ((candidate, result) in state.sourceSearches) {
            if (result.error != null) Text("${candidate.title}：${result.error}", color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
            else if (!result.loading && result.books.isEmpty()) Text("${candidate.title}：没有匹配作品", style = MaterialTheme.typography.bodySmall)
        }
    }
}
