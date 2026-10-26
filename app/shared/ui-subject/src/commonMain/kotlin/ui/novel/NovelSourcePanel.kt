/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.novel

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.repository.novel.*
import me.him188.ani.app.ui.mediaselect.MediaSelectorMode
import me.him188.ani.app.ui.mediaselect.common.MediaSelectorDialog
import me.him188.ani.app.ui.mediaselect.common.MediaSelectorSearchField
import me.him188.ani.app.ui.mediaselect.common.MediaSelectorTopBar
import me.him188.ani.app.ui.mediaselect.manual.*
import me.him188.ani.app.ui.mediaselect.selector.*
import me.him188.ani.datasources.api.source.BrowseSubject
import me.him188.ani.datasources.api.source.MediaSourceInfo

@Composable
internal fun NovelSourcesDialog(state: NovelState, keyword: String, onDismiss: () -> Unit,
    onSearch: (NovelSource, String) -> Unit, onBook: (NovelBook) -> Unit, onAuto: () -> Unit,
    onSettings: (NovelSourceSettings) -> Unit,
    onZlibraryLogin: () -> Unit = {},
) {
    var query by rememberSaveable { mutableStateOf(keyword) }
    var manage by rememberSaveable { mutableStateOf(false) }
    var mode by rememberSaveable { mutableStateOf(MediaSelectorMode.AUTO) }
    val uri = LocalUriHandler.current
    MediaSelectorDialog(onDismiss) { _ ->
        Column(Modifier.fillMaxSize()) {
            MediaSelectorTopBar(if (manage) "管理来源" else "数据源", mode, { mode = it; manage = false }, onDismiss, showBt = false,
                extraActions = { IconButton({ manage = !manage }, Modifier.testTag("novel-source-manage")) {
                    Icon(Icons.Rounded.Settings, if (manage) "返回选源" else "管理来源")
                } })
            if (!manage) NovelSourcePanel(state, query, { query = it }, onSearch, onBook, onAuto,
                mode == MediaSelectorMode.MANUAL, { mode = MediaSelectorMode.MANUAL },
                Modifier.weight(1f).then(if (mode == MediaSelectorMode.AUTO) Modifier.verticalScroll(rememberScrollState()) else Modifier))
            else LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text("订阅的新增、编辑和刷新：设置 → 数据源 → 小说源订阅") }
                items(state.sourceSettings.all, key = { it.name }) { source ->
                    Column {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(state.sourceSettings.title(source))
                            Switch(source in state.sourceSettings.enabled, { enabled ->
                                onSettings(state.sourceSettings.copy(disabled = if (enabled) state.sourceSettings.disabled - source else state.sourceSettings.disabled + source))
                            }, Modifier.testTag("novel-source-enabled-${source.name}"), enabled = state.sourceSettings.definition(source)?.unavailableReason.isNullOrBlank())
                        }
                        state.sourceSettings.definition(source)?.unavailableReason?.takeIf { it.isNotBlank() }?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        Row {
                            TextButton({
                                val order = state.sourceSettings.all.toMutableList()
                                val index = order.indexOf(source)
                                if (index > 0) { order.removeAt(index); order.add(index - 1, source); onSettings(state.sourceSettings.copy(order = order)) }
                            }, enabled = state.sourceSettings.all.first() != source, modifier = Modifier.testTag("novel-source-up-${source.name}")) { Text("上移") }
                            TextButton({ state.sourceSettings.definition(source)?.home?.let(uri::openUri) }) { Text("打开网站") }
                            if (source == NovelSource.ZLIBRARY) {
                                TextButton(onZlibraryLogin, Modifier.testTag("zlibrary-login-action")) { Text("登录／更新登录") }
                            }
                            state.sourceSettings.subscriptionUrl(source)?.let { link -> TextButton({ uri.openUri(link) }) { Text("订阅链接") } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun NovelSourcePanel(state: NovelState, keyword: String, onKeyword: (String) -> Unit,
    onSearch: (NovelSource, String) -> Unit, onBook: (NovelBook) -> Unit, onAuto: () -> Unit,
    manual: Boolean, onManual: () -> Unit, modifier: Modifier = Modifier,
) {
    var selectedKey by rememberSaveable { mutableStateOf(state.book?.source?.name.orEmpty()) }
    val settings = state.sourceSettings
    val selected = settings.enabled.firstOrNull { it.name == selectedKey } ?: settings.enabled.firstOrNull()
    val choices = settings.enabled.map { source ->
        ManualBrowseSource(source.name, source.name, MediaSourceInfo(displayName = settings.title(source), websiteUrl = settings.definition(source)!!.home))
    }
    val books = settings.enabled.associateWith { source -> state.sourceSearches[source]?.books ?: listOfNotNull(state.book?.takeIf { it.source == source }) }
    LaunchedEffect(manual, selected) {
        if (manual && selected != null && keyword.isNotBlank() && state.sourceSearches[selected]?.keyword != keyword) onSearch(selected, keyword)
    }
    if (manual) {
        val result = state.sourceSearches[selected]
        val current = books[selected].orEmpty()
        fun NovelBook.browse() = BrowseSubject(listOf(title, author, edition).filter { it.isNotBlank() }.joinToString(" · "), id)
        val load = when {
            result?.loading == true -> ManualLoadState.Loading
            result?.error != null -> ManualLoadState.Failed(IllegalStateException(result.error))
            result != null || current.isNotEmpty() -> ManualLoadState.Success(current.map { it.browse() })
            else -> ManualLoadState.Idle
        }
        Column(modifier.fillMaxSize().testTag("novel-source-manual")) {
            ManualSourceSelector(choices, selected?.name, { selectedKey = it }, Modifier.padding(horizontal = 16.dp).fillMaxWidth())
            MediaSelectorSearchField(keyword, onKeyword, { selected?.let { onSearch(it, keyword) } },
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().testTag("novel-source-query"), enabled = selected != null)
            if (selected == null) Text("没有已启用的小说源，请在设置 → 数据源中添加或启用小说订阅。", Modifier.padding(16.dp))
            ManualResultsList(load, state.book?.takeIf { it.source == selected }?.browse(),
                { value -> current.firstOrNull { it.id == value.url }?.let(onBook) },
                { selected?.let { onSearch(it, keyword) } }, Modifier.weight(1f).fillMaxWidth())
        }
    } else {
        val rows = settings.enabled.map { source ->
            val result = state.sourceSearches[source]
            WebSource(source.name, source.name, "", settings.title(source), books.getValue(source).mapIndexed { index, book ->
                WebSourceChannel(if (books.getValue(source).count { it.title == book.title } > 1) "${book.title} · ${book.author.ifBlank { (index + 1).toString() }} · ${book.edition} · ${index + 1}" else listOf(book.title, book.edition).filter { it.isNotBlank() }.joinToString(" · "))
            },
                isLoading = result?.loading == true, isError = result?.error != null, isPreferred = state.book?.source == source)
        }
        Column(modifier.testTag("novel-source-auto"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onAuto, enabled = !state.autoSelecting && choices.isNotEmpty(), modifier = Modifier.testTag("novel-auto-source")) { Text("自动选择") }
                Text(if (state.autoSelecting) "正在检查可读来源…" else if (state.sourcePinned) "已记住手动选择" else "按来源优先级自动选择", style = MaterialTheme.typography.bodySmall)
            }
            if (state.autoSelecting) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (choices.isEmpty()) Text("没有已启用的小说源，请在设置 → 数据源中添加或启用小说订阅。", Modifier.padding(16.dp))
            MediaSelectorWebSourcesColumn(rows,
                selectedSource = { rows.firstOrNull { it.instanceId == state.book?.source?.name } },
                selectedChannel = { state.book?.let { book -> rows.firstOrNull { it.instanceId == book.source.name }?.channels?.getOrNull(books[book.source].orEmpty().indexOfFirst { it.id == book.id }) } },
                onSelect = { row, channel -> books[NovelSource(row.instanceId)]?.getOrNull(row.channels.indexOf(channel))?.let(onBook) },
                onRefresh = { onSearch(NovelSource(it.instanceId), keyword) }, onResolveCaptcha = {}, onRequestManualSearch = onManual)
            settings.enabled.forEach { source -> state.sourceSearches[source]?.let { result ->
                if (result.error != null) Text("${settings.title(source)}：${result.error}", Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error)
                else if (!result.loading && result.books.isEmpty()) Text("${settings.title(source)}：没有匹配作品", Modifier.padding(horizontal = 16.dp))
            } }
        }
    }
}
