/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.manga

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import me.him188.ani.app.ui.mediaselect.MediaSelectorMode
import me.him188.ani.app.ui.mediaselect.common.MediaSelectorModeChip
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
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
import me.him188.ani.datasources.api.source.BrowseSubject
import me.him188.ani.app.ui.mediaselect.manual.ManualResultsList
import me.him188.ani.app.ui.mediaselect.manual.ManualLoadState
import me.him188.ani.app.ui.mediaselect.common.MediaSelectorSearchField

/** Uses the anime selector's dropdown and source rows in compact and wide manga hosts. */
@Composable
internal fun MangaSourcePanel(
    state: MangaDetailsUiState, keyword: String, onKeyword: (String) -> Unit,
    onSearch: (MangaSource, String) -> Unit, onBook: (MangaSourceBook) -> Unit,
    onAutoSelect: () -> Unit,
    modifier: Modifier = Modifier, manual: Boolean? = null, onManualSearch: (() -> Unit)? = null,
) {
    var source by remember(state.sourceSettings) { mutableStateOf(state.source.takeIf { it in state.sourceSettings.enabled } ?: state.sourceSettings.enabled.firstOrNull() ?: state.source) }
    var inlineMode by remember { mutableStateOf(MediaSelectorMode.AUTO) }
    val isManual = manual ?: (inlineMode == MediaSelectorMode.MANUAL)
    val actions = LocalMangaActions.current
    val choices = remember(state.sourceSettings) {
        state.sourceSettings.enabled.map { ManualBrowseSource(it.name, it.name,
            MediaSourceInfo(displayName = it.title, websiteUrl = it.home, iconUrl = it.iconUrl)) }
    }
    val booksBySource = MangaSource.available.associateWith { candidate ->
        state.sourceSearches[candidate]?.books
            ?: (if (candidate == state.source) state.results else emptyList()).ifEmpty {
                listOfNotNull(state.book?.takeIf { it.source == candidate })
            }
    }
    val rows = state.sourceSettings.enabled.map { candidate ->
        val result = state.sourceSearches[candidate]
        val books = booksBySource.getValue(candidate)
        WebSource(candidate.name, candidate.name, candidate.iconUrl, candidate.title,
            books.map { book -> WebSourceChannel(if (books.count { it.title == book.title } > 1) "${book.title} · #${book.id}" else book.title) }, isLoading = result?.loading == true,
            isError = result?.error != null, isPreferred = state.book?.source == candidate,
            captchaRequest = result?.captcha, isResolvingCaptcha = result?.resolvingCaptcha == true, isCaptchaSupported = state.captchaSupported)
    }
    LaunchedEffect(isManual, source) {
        if (isManual && source in state.sourceSettings.enabled && keyword.isNotBlank() &&
            state.sourceSearches[source]?.keyword != keyword) onSearch(source, keyword)
    }
    if (isManual) {
        val result = state.sourceSearches[source].takeIf { source in state.sourceSettings.enabled }
        val books = if (source in state.sourceSettings.enabled) booksBySource.getValue(source) else emptyList()
        // BrowseSubject is only a presentation adapter; the stable key maps back to the original manga book.
        fun MangaSourceBook.asBrowse() = BrowseSubject(title, id)
        val results = when {
            result?.loading == true -> ManualLoadState.Loading
            result?.error != null -> ManualLoadState.Failed(IllegalStateException(result.error),
                captchaUnsupported = result.captcha != null && !state.captchaSupported)
            result != null || books.isNotEmpty() -> ManualLoadState.Success(books.map { it.asBrowse() })
            else -> ManualLoadState.Idle
        }
        Column(modifier.testTag("manga-source-panel").then(if (manual == null) Modifier.height(520.dp) else Modifier.fillMaxSize())) {
            if (manual == null) MediaSelectorModeChip(inlineMode, { inlineMode = it }, showBt = false)
            ManualSourceSelector(choices, source.name, { source = MangaSource.valueOf(it) },
                Modifier.padding(horizontal = 16.dp).fillMaxWidth())
            MediaSelectorSearchField(keyword, onKeyword, { onSearch(source, keyword) },
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().testTag("manga-source-query"),
                enabled = choices.isNotEmpty())
            if (result?.error != null) Text(result.error, Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            if (result?.captcha != null && state.captchaSupported) {
                TextButton({ actions.solveCaptcha(source) }, enabled = !result.resolvingCaptcha) {
                    Text(if (result.resolvingCaptcha) "正在验证…" else "完成网页验证")
                }
            }
            ManualResultsList(results, state.book?.takeIf { it.source == source }?.asBrowse(),
                { selected -> books.firstOrNull { it.id == selected.url }?.let(onBook) },
                { if (result?.captcha != null) actions.solveCaptcha(source) else onSearch(source, keyword) },
                Modifier.weight(1f).fillMaxWidth())
        }
        return
    }
    Column(modifier.testTag("manga-source-panel"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (manual == null) MediaSelectorModeChip(inlineMode, { inlineMode = it }, showBt = false)
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onAutoSelect, enabled = !state.autoSelecting && choices.isNotEmpty(), modifier = Modifier.testTag("manga-auto-source")) { Text("自动选择") }
            Text(if (state.autoSelecting) "正在检查可下载的来源…"
                else if (state.memory?.sourcePinned == true) "已记住手动选择"
                else "按来源优先级自动选择", style = MaterialTheme.typography.bodySmall)
        }
        if (state.autoSelecting) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (choices.isEmpty()) Text("没有已启用的漫画源，请在设置 → 数据源中添加或启用漫画订阅。", Modifier.padding(16.dp))
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
            onResolveCaptcha = { actions.solveCaptcha(MangaSource.valueOf(it.instanceId)) }, onRequestManualSearch = onManualSearch ?: { inlineMode = MediaSelectorMode.MANUAL })
        for ((candidate, result) in state.sourceSearches) {
            if (result.error != null) Text("${candidate.title}：${result.error}", color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
            else if (!result.loading && result.books.isEmpty()) Text("${candidate.title}：没有匹配作品", style = MaterialTheme.typography.bodySmall)
        }
    }
}
