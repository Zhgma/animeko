// SPDX-License-Identifier: AGPL-3.0-only
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import me.him188.ani.app.data.repository.manga.*
import me.him188.ani.app.ui.mediaselect.MediaSelectorMode
import me.him188.ani.app.ui.mediaselect.common.MediaSelectorSearchField
import me.him188.ani.app.ui.mediaselect.common.MediaSelectorTopBar
import me.him188.ani.app.ui.mediaselect.manual.*
import me.him188.ani.app.ui.mediaselect.selector.*
import me.him188.ani.datasources.api.source.BrowseSubject

/** Only installed and enabled native adapters participate; unknown versions remain inert. */
fun browserMangaSources(state: BrowserMediaState): List<MangaSource> = state.subscriptions
    .filter { it.enabled && it.kind == "manga" }.flatMap { it.sources }.filter {
        it.enabled && it.format.text("factoryId") == "manga-native" && it.format["version"]?.jsonPrimitive?.intOrNull == 1
    }.mapNotNull { row ->
        val key = row.format["arguments"]?.jsonObject?.text("key")
        MangaSource.available.firstOrNull { it.name.lowercase() == key }
    }.distinct()

data class WebMangaSearchResult(val keyword: String, val books: List<MangaSourceBook> = emptyList(),
    val loading: Boolean = false, val error: String? = null)

/** Source-local jobs and cache prevent late results from replacing a newer query. */
@Stable
class WebMangaSourceSession(
    private val scope: CoroutineScope,
    private val searchSource: suspend (MangaSource, String) -> List<MangaSourceBook>,
    initialBook: MangaSourceBook? = null,
    private val saveBook: (MangaSourceBook) -> Unit = {},
) {
    var results by mutableStateOf<Map<MangaSource, WebMangaSearchResult>>(emptyMap()); private set
    var book by mutableStateOf(initialBook); private set
    var enabled by mutableStateOf<List<MangaSource>>(emptyList()); private set
    var message by mutableStateOf<String?>(null); private set
    private val jobs = mutableMapOf<MangaSource, Job>()
    private val generations = mutableMapOf<MangaSource, Int>()
    fun setSources(sources: List<MangaSource>) {
        enabled = sources
        (jobs.keys - sources.toSet()).forEach { jobs.remove(it)?.cancel(); generations[it] = (generations[it] ?: 0) + 1 }
        results = results.filterKeys { it in sources }
    }
    fun choose(value: MangaSourceBook) {
        if (value.source !in enabled) return
        try { saveBook(value); book = value; message = "已记住手动选择" }
        catch (e: Exception) { message = "保存来源失败：${e.message}" }
    }
    fun search(source: MangaSource, keyword: String, force: Boolean = false) {
        val query = keyword.trim()
        if (source !in enabled || query.isEmpty()) return
        if (!force && results[source]?.keyword == query) return
        jobs.remove(source)?.cancel()
        val generation = (generations[source] ?: 0) + 1
        generations[source] = generation
        results = results + (source to WebMangaSearchResult(query, loading = true))
        jobs[source] = scope.launch {
            try {
                val books = searchSource(source, query)
                if (generations[source] == generation && source in enabled)
                    results = results + (source to WebMangaSearchResult(query, books))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (generations[source] == generation && source in enabled)
                    results = results + (source to WebMangaSearchResult(query, error = e.message ?: "来源查询失败"))
            }
        }
    }
    fun match(keyword: String, force: Boolean = false) {
        message = "按作品名匹配，请选择对应漫画"
        enabled.forEach { search(it, keyword, force) }
    }
}

private val mangaHttp = HttpClient(Js) { install(HttpTimeout) { requestTimeoutMillis = 25_000 } }
private val mangaSearch = MangaSourceSearch(
    get = { url ->
        val value = webJson.parseToJsonElement(request("mediaOnlineRead", buildJsonObject { put("url", url) }.toString()).raw).jsonObject
        val html = value.text("text")
        check(!html.contains("cf-chl-") && !html.contains("challenges.cloudflare.com")) { "来源需要网页验证，请在原站查看或切换来源" }
        html
    },
    queryKomiic = { operation, variables ->
        val response = mangaHttp.post("$KOMIIC_API_BASE_URL/query") {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("operation", operation); put("variables", variables) }.toString())
        }
        check(response.status.value in 200..299) { "Komiic 查询失败（HTTP ${response.status.value}），请稍后重试或切换来源" }
        val root = webJson.parseToJsonElement(response.bodyAsText()).jsonObject
        check((root["errors"] as? JsonArray).isNullOrEmpty()) { "Komiic 未能完成查询" }
        root["data"] as? JsonObject ?: error("Komiic 返回格式有变")
    },
)

fun mangaBookUrl(book: MangaSourceBook): String? {
    if (!book.id.matches(Regex("[A-Za-z0-9_-]+"))) return null
    return when (book.source) {
        MangaSource.KOMIIC -> "${book.source.home}/comic/${book.id}"
        MangaSource.MANHUAGUI -> "${book.source.home}/comic/${book.id}/"
        MangaSource.MANHUAREN -> "${book.source.home}/manhua-${book.id}/"
        MangaSource.CCC -> "${book.source.home}/zh/book/${book.id}/content"
        MangaSource.COMICK -> "${book.source.home}/comic/${book.id}"
        MangaSource.BAOZI -> "${book.source.home}/comic/${book.id}"
        MangaSource.GODA -> "${book.source.home}/manga/${book.id}"
        MangaSource.YKMH -> "${book.source.home}/manhua/${book.id}/"
        MangaSource.MANGADEX -> null
    }
}

@Composable
fun WebMangaSources(subject: Subject, onDismiss: () -> Unit, onSources: () -> Unit,
    modifier: Modifier = Modifier, embedded: Boolean = false, useDialog: Boolean = false) {
    val media = rememberBrowserMedia()
    val scope = rememberCoroutineScope()
    val session = remember(subject.id) {
        val key = "manga.source.${subject.id}"
        WebMangaSourceSession(scope, mangaSearch::search,
            initialBook = runCatching { webJson.decodeFromString<MangaSourceBook>(aniRead(key)) }.getOrNull(),
            saveBook = { check(aniWrite(key, webJson.encodeToString(it))) { "浏览器存储不可用" } })
    }
    var mode by remember(subject.id) { mutableStateOf(MediaSelectorMode.AUTO) }
    var keyword by remember(subject.id) { mutableStateOf(subject.title.ifBlank { subject.original }) }
    var selectedSource by remember(subject.id) { mutableStateOf(session.book?.source) }
    val sources = browserMangaSources(media.state)
    val selected = selectedSource?.takeIf { it in sources } ?: sources.firstOrNull()
    LaunchedEffect(sources) { session.setSources(sources) }
    LaunchedEffect(mode, selected, sources) {
        if (mode == MediaSelectorMode.MANUAL && selected != null) session.search(selected, keyword)
        else if (mode == MediaSelectorMode.AUTO && session.book == null) session.match(keyword)
    }
    val content: @Composable () -> Unit = {
        BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < 480.dp
        Column(Modifier.fillMaxSize().testTag("web-manga-sources")) {
            MediaSelectorTopBar("数据源", mode, { mode = it }, onDismiss, showBt = false, extraActions = {
                IconButton({ onDismiss(); onSources() }) { Icon(Icons.Rounded.Settings, "管理来源") }
            })
            if (sources.isEmpty()) TextButton(onSources, Modifier.padding(horizontal = 16.dp)) { Text("没有启用的漫画来源 · 管理订阅") }
            media.error?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
            if (mode == MediaSelectorMode.MANUAL) {
                ManualSourceSelectorContent(sources, selected?.name, { selectedSource = MangaSource.valueOf(it) },
                    id = { it.name }, name = { it.title }, icon = { Icon(Icons.Rounded.MenuBook, null, Modifier.size(24.dp)) },
                    tier = { _, _ -> }, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth())
                MediaSelectorSearchField(keyword, { keyword = it }, { selected?.let { session.search(it, keyword, true) } },
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().testTag("manga-source-query"), enabled = selected != null)
                val result = session.results[selected]
                val books = result?.books ?: listOfNotNull(session.book?.takeIf { it.source == selected })
                val display = when {
                    result?.loading == true -> ManualLoadState.Loading
                    result?.error != null -> ManualLoadState.Failed(IllegalStateException(result.error))
                    result != null || books.isNotEmpty() -> ManualLoadState.Success(books.map { BrowseSubject(it.title, it.id) })
                    else -> ManualLoadState.Idle
                }
                result?.error?.let { Text(it, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error) }
                ManualResultsList(display, session.book?.takeIf { it.source == selected }?.let { BrowseSubject(it.title, it.id) },
                    { row -> books.firstOrNull { it.id == row.url }?.let(session::choose) },
                    { selected?.let { session.search(it, keyword, true) } }, Modifier.fillMaxWidth().weight(1f))
            } else {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton({ session.match(keyword, true) }, enabled = sources.isNotEmpty() && session.results.values.none { it.loading }) { Text("自动匹配") }
                        Text(session.message ?: if (session.book != null) "已记住手动选择" else "", style = MaterialTheme.typography.bodySmall)
                    }
                    WebSourcesColumnContent(sources, { session.results[it]?.error != null }, { mode = MediaSelectorMode.MANUAL }) { source ->
                        val result = session.results[source]
                        val books = result?.books ?: listOfNotNull(session.book?.takeIf { it.source == source })
                        Column {
                            WebSourceCardContent(source.title, { Icon(Icons.Rounded.MenuBook, null, Modifier.size(24.dp)) }, channels = {
                                WebSourceChannelsContent(books.map { it.title }, books.indexOf(session.book).takeIf { it >= 0 },
                                    result?.loading == true, result?.error != null, { session.choose(books[it]) })
                            }, trailing = { if (result?.error != null) WebSourceRetryButton { session.search(source, keyword, true) } },
                                modifier = Modifier.fillMaxWidth().background(if (session.book?.source == source) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 16.dp, vertical = 8.dp))
                            result?.error?.let { Text(it, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
            session.book?.takeIf { it.source in sources }?.let { book ->
                HorizontalDivider()
                if (compact) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("已选择：${book.title}", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall)
                    mangaBookUrl(book)?.let { url -> TextButton({ aniOpen(url) }) { Text("在来源网站查看") } }
                } else Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("已选择：${book.title} · ${book.source.title}", style = MaterialTheme.typography.bodySmall)
                    Text("网页阅读器尚未接入，可先在来源网站阅读。", style = MaterialTheme.typography.bodySmall)
                    mangaBookUrl(book)?.let { url -> TextButton({ aniOpen(url) }) { Text("在来源网站查看") } }
                }
            }
        }
        }
    }
    if (embedded) Surface(modifier, color = BottomSheetDefaults.ContainerColor) { content() }
    else if (useDialog) Dialog(onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = BottomSheetDefaults.ContainerColor) { content() }
    } else ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = BottomSheetDefaults.ContainerColor) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.94f)) { content() }
    }
}
