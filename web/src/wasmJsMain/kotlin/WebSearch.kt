import me.him188.ani.app.data.repository.manga.BOOK_SEARCH_PAGE_SIZE
import me.him188.ani.app.data.models.preference.NsfwMode
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.him188.ani.app.domain.search.SearchSort
import me.him188.ani.app.domain.search.SubjectType
import me.him188.ani.app.ui.adaptive.AniTopAppBar
import me.him188.ani.app.ui.exploration.search.*
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.paneHorizontalPadding
import me.him188.ani.app.ui.foundation.layout.paneVerticalPadding
import me.him188.ani.app.ui.subject.SubjectCoverCardContent
import me.him188.ani.app.ui.subject.SubjectGridDefaults
import me.him188.ani.app.ui.lang.*
import org.jetbrains.compose.resources.stringResource

/** Browser paging and history adapters feed the App's search presentation. */
@Composable
fun WebSearch(onOpen: (Int) -> Unit) {
    var keyword by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(SubjectType.ANIME) }
    var sort by rememberSaveable { mutableStateOf(SearchSort.MATCH) }
    var layout by rememberSaveable { mutableStateOf(SearchResultLayoutKind.COVER) }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    var expanded by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf(WebResult()) }
    var history by remember { mutableStateOf(runCatching {
        webJson.decodeFromString<List<String>>(aniRead("searchHistory"))
    }.getOrDefault(emptyList())) }
    val focus = LocalFocusManager.current
    val grid = rememberLazyGridState()
    val window = currentWindowAdaptiveInfo1()
    val coverLayout = SubjectGridDefaults.coverLayoutParameters(window)
    val nsfwMode = LocalWebUISettings.current.searchSettings.nsfwMode
    val ignoreDone = LocalWebUISettings.current.searchSettings.ignoreDoneAndDroppedSubjects
    val visibleItems = result.items.filter { !it.nsfw || nsfwMode != NsfwMode.HIDE }
    val original = LocalWebOriginalTitle.current
    val pageSize = if (type == SubjectType.BOOK) BOOK_SEARCH_PAGE_SIZE else 24
    fun saveHistory(values: List<String>) {
        history = values.take(20)
        aniWrite("searchHistory", webJson.encodeToString(history))
    }
    fun submit(value: String = keyword) {
        keyword = value.trim(); query = keyword; offset = 0; retry++
        expanded = false; focus.clearFocus()
        if (query.isNotBlank()) saveHistory(listOf(query) + history.filterNot { it == query })
    }
    LaunchedEffect(query, type, sort, offset, retry, nsfwMode, ignoreDone) {
        result = WebResult(); error = null; loading = false
        if (query.isBlank()) return@LaunchedEffect
        loading = true
        try {
            result = request("search", buildJsonObject {
                put("keyword", query); put("type", if (type == SubjectType.BOOK) 1 else 2)
                put("sort", when (sort) { SearchSort.MATCH -> "match"; SearchSort.RANK -> "rank"; SearchSort.COLLECTION -> "collect"; SearchSort.DATE -> "date" })
                put("offset", offset)
                put("hideNsfw", nsfwMode == NsfwMode.HIDE); put("ignoreDone", ignoreDone)
            }.toString())
            grid.scrollToItem(0)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message }
        finally { loading = false }
    }
    Column(Modifier.fillMaxSize()) {
        AniTopAppBar(title = { Text(stringResource(Lang.exploration_search)) })
        SearchPageColumn(Modifier.fillMaxSize().padding(horizontal = window.windowSizeClass.paneHorizontalPadding)) {
            SearchBar(
                inputField = {
                    SubjectSearchInput(keyword, { keyword = it }, { submit() }, type,
                        onTypeChange = { type = it; sort = SearchSort.MATCH; submit() },
                        onClear = { keyword = ""; query = ""; offset = 0; expanded = false; focus.clearFocus() },
                        expanded = expanded, onExpandedChange = { expanded = it },
                        placeholder = { Text(stringResource(if (type == SubjectType.BOOK) Lang.search_book_placeholder else Lang.settings_mediasource_test_keyword)) })
                },
                expanded = expanded,
                onExpandedChange = { expanded = it },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                windowInsets = WindowInsets(0),
            ) {
                LazyColumn {
                    items(history.filter { keyword.isBlank() || it.contains(keyword, ignoreCase = true) }) { text ->
                        ListItem(headlineContent = { Text(text) },
                            leadingContent = { Icon(Icons.Default.History, null) },
                            modifier = Modifier.clickable { submit(text) },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            trailingContent = { IconButton({ saveHistory(history - text) }) { Icon(Icons.Default.Close, "删除 $text") } })
                    }
                }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyVerticalGrid(
                columns = if (layout == SearchResultLayoutKind.COVER) coverLayout.gridCells else GridCells.Adaptive(360.dp),
                state = grid, modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = window.windowSizeClass.paneVerticalPadding),
                horizontalArrangement = coverLayout.horizontalArrangement,
                verticalArrangement = coverLayout.verticalArrangement,
            ) {
                if (error != null) item(span = { GridItemSpan(maxLineSpan) }) { WebError(error!!, { retry++ }) }
                if (result.items.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        SearchResultsToolbar(result.items.size, layout, sort, { layout = it }, { sort = it; offset = 0 },
                            sortOptions = if (type == SubjectType.BOOK) listOf(SearchSort.MATCH, SearchSort.COLLECTION, SearchSort.RANK) else SearchSort.entries)
                        if (result.cached) Text("离线缓存，可能不是最新数据", style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (query.isNotBlank() && !loading && error == null && result.items.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                    ListItem(headlineContent = { Text(stringResource(Lang.foundation_load_error_no_results)) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest))
                }
                items(visibleItems, key = { it.id }) { subject ->
                    WebNsfw(subject) {
                    if (layout == SearchResultLayoutKind.COVER) SubjectCoverCardContent(
                        subject.displayTitle(original), subject.cover, false, { onOpen(subject.id) }, shape = coverLayout.cardShape)
                    else WebSubjectItem(subject, onOpen)
                    }
                }
                if (result.total > pageSize || result.hasMore || offset > 0) item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        TextButton({ offset = (offset - pageSize).coerceAtLeast(0) }, enabled = offset > 0 && !loading) { Text("上一页") }
                        TextButton({}, enabled = false) { Text("${offset / pageSize + 1}") }
                        TextButton({ offset += pageSize }, enabled = (result.hasMore || offset + pageSize < result.total) && !loading) { Text("下一页") }
                    }
                }
            }
        }
    }
}
