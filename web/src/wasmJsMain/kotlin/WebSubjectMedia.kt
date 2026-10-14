import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import me.him188.ani.app.ui.mediaselect.WatchingEpisode
import me.him188.ani.app.ui.mediaselect.bt.*
import me.him188.ani.app.ui.mediaselect.common.MediaSelectorSearchField
import me.him188.ani.app.ui.mediaselect.common.WatchingEpisodeCard

private data class WebBtItem(val source: String, val item: FeedItem) {
    val id get() = source + "/" + item.url
}

/** RSS sessions supply values to the same list, source rows and filter controls as the App. */
@Composable
fun WebSubjectMedia(subject: Subject, episode: String, onDismiss: () -> Unit, onDownloads: () -> Unit,
                    onSources: () -> Unit, modifier: Modifier = Modifier, embedded: Boolean = false,
                    useDialog: Boolean = false, showHeader: Boolean = true) {
    val media = rememberBrowserMedia()
    var keyword by remember { mutableStateOf(subject.title.ifBlank { subject.original }) }
    var submitted by remember { mutableStateOf(keyword) }
    var sourceId by remember { mutableStateOf<String?>(null) }
    var ready by remember { mutableStateOf(false) }
    var page by remember { mutableIntStateOf(1) }
    var retry by remember { mutableIntStateOf(0) }
    var results by remember { mutableStateOf<Map<String, SubjectMediaSearch>>(emptyMap()) }
    var failures by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var loading by remember { mutableStateOf<Set<String>>(emptySet()) }
    var initError by remember { mutableStateOf<String?>(null) }
    var chosen by remember { mutableStateOf<WebBtItem?>(null) }
    var selected by remember { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var revealExcluded by remember { mutableStateOf(false) }
    var episodeFilter by remember { mutableStateOf(episode.isNotBlank() && subject.type != 1) }
    var resolution by remember { mutableStateOf<String?>(null) }
    var subtitle by remember { mutableStateOf<String?>(null) }
    var alliance by remember { mutableStateOf<String?>(null) }
    val preferenceResult = remember { runCatching { readMediaPreference() } }
    val selectorResult = remember { runCatching { readMediaSelectorSettings() } }
    val preference = preferenceResult.getOrDefault(MediaPreference.PlatformDefault)
    val selectorSettings = selectorResult.getOrDefault(MediaSelectorSettings.Default)
    val preferenceComparator = remember(preference,selectorSettings,subject.finished) { watchingComparator(preference,selectorSettings,subject.finished) }
    val sources = media.state.subjectSources
    val scope = rememberCoroutineScope()
    val aliases = remember(subject.id) { (listOf(subject.title, subject.original) + subject.info.filter { it.label == "别名" }.flatMap { it.value.split(" / ") }).filter { it.isNotBlank() }.distinct().take(6) }
    fun search(text: String = keyword) { if (text.isNotBlank()) { keyword = text; submitted = text; page = 1; retry++; revealExcluded = false } }
    suspend fun fetchSource(id: String) {
        val query = Triple(submitted, page, retry)
        fun isCurrent() = query == Triple(submitted, page, retry)
        loading = loading + id; failures = failures - id
        try {
            val response: BrowserMediaState = webJson.decodeFromString(request("mediaSubjectSearch", buildJsonObject {
                put("source", id); put("keyword", query.first); put("kind", if (subject.type == 1) "book" else "anime"); put("page", query.second)
            }.toString()).raw)
            if (isCurrent()) results = results + (id to response.subjectSearch)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { if (isCurrent()) failures = failures + (id to (e.message ?: "查询失败")) }
        finally { if (isCurrent()) loading = loading - id }
    }
    LaunchedEffect(ready, retry) {
        if (!ready) try {
            request("mediaSubjectSources"); media.refresh(); initError = null; ready = true
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { initError = e.message }
    }
    LaunchedEffect(ready, submitted, page, retry) {
        if (!ready) return@LaunchedEffect
        results = emptyMap(); failures = emptyMap(); loading = sources.map { it.id }.toSet()
        coroutineScope { sources.forEach { source -> launch { fetchSource(source.id) } } }
    }
    val all = sources.flatMap { source -> results[source.id]?.items.orEmpty().map { WebBtItem(source.id, it) } }
    val resolutions = all.map { it.item.resolution }.filter { it.isNotBlank() }.distinct()
    val subtitles = all.flatMap { it.item.subtitles }.distinct()
    val alliances = all.map { it.item.alliance }.filter { it.isNotBlank() }.distinct()
    val activeResolution = resolution?.takeIf { it in resolutions }
    val activeSubtitle = subtitle?.takeIf { it in subtitles }
    val activeAlliance = alliance?.takeIf { it in alliances }
    val activeCount = listOfNotNull(activeResolution, activeSubtitle, activeAlliance).size
    val visible = all.filter { sourceId == null || it.source == sourceId }.sortedWith { a, b -> preferenceComparator.compare(a.item,b.item) }
    val rows = visible.map { entry ->
        val item = entry.item
        val number = episode.toDoubleOrNull()
        val exclusion = when {
            episodeFilter && number != null && (item.episodeStart < 0 || number < item.episodeStart || number > item.episodeEnd) -> if (item.episodeStart < 0) "集号未知" else "集数不符"
            activeResolution != null && item.resolution != activeResolution || activeSubtitle != null && activeSubtitle !in item.subtitles || activeAlliance != null && item.alliance != activeAlliance -> "低于偏好"
            else -> if(subject.type == 1) null else watchingExclusion(item,preference,selectorSettings,subject.finished)
        }
        BtResourceRow(entry.id, item.title, item.alliance, item.resolution, (item.subtitles + item.subtitleKind).filter { it.isNotBlank() }.joinToString(" "),
            if (item.size > 0) mediaBytes(item.size) else "", item.published,
            listOfNotNull(exclusion) + listOf(item.resolution).filter { it.isNotBlank() } + item.subtitles + listOf(item.subtitleKind).filter { it.isNotBlank() } + if (item.episodeEnd > item.episodeStart && item.episodeStart >= 0) listOf("合集") else emptyList(),
            exclusion, selected == entry.id)
    }
    val included = rows.filter { !it.isExcluded }
    val excluded = rows.filter { it.isExcluded }
    val content: @Composable () -> Unit = {
        BoxWithConstraints(modifier.fillMaxSize()) {
            val isTable = maxWidth >= 720.dp
            val horizontal = if (isTable) 24.dp else 20.dp
            Column(Modifier.fillMaxSize()) {
                if (showHeader) TopAppBar(title = { Text("数据源") }, actions = {
                    AssistChip({ panel = "help" }, label = { Text("BT 资源") })
                    Box {
                        IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, "更多选源操作") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text("重新搜索") }, onClick = { menu = false; search() })
                            aliases.forEach { alias -> DropdownMenuItem(text = { Text(alias, maxLines = 1, overflow = TextOverflow.Ellipsis) }, onClick = { menu = false; search(alias) }) }
                            DropdownMenuItem(text = { Text("管理订阅") }, onClick = { menu = false; onSources() })
                            DropdownMenuItem(text = { Text("下载 / 导入种子") }, onClick = { menu = false; onDownloads() })
                            DropdownMenuItem(text = { Text("浏览器下载说明") }, onClick = { menu = false; panel = "help" })
                        }
                    }
                    IconButton(onDismiss) { Icon(Icons.Rounded.Close, "关闭选源") }
                }, windowInsets = WindowInsets(0,0,0,0), colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow))
                if (showHeader) WatchingEpisodeCard(episode.takeIf { it.isNotBlank() && subject.type != 1 }?.let { WatchingEpisode(it, "") }, Modifier.padding(horizontal = horizontal).padding(bottom = 12.dp).fillMaxWidth())
                MediaSelectorSearchField(keyword, { keyword = it }, { search() }, Modifier.padding(horizontal = horizontal).padding(bottom = 12.dp).fillMaxWidth())
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = horizontal).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (episode.isNotBlank() && subject.type != 1) FilterChip(episodeFilter, { episodeFilter = !episodeFilter }, label = { Text("第 $episode 话") })
                    FilterChip(sourceId != null, { panel = "sources" }, label = { Text(sources.find { it.id == sourceId }?.title ?: "数据源", maxLines = 1, overflow = TextOverflow.Ellipsis) }, trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, null, Modifier.size(18.dp)) })
                    FilterChip(activeCount > 0, { panel = "filters" }, label = { Text(if (activeCount > 0) "筛选 ($activeCount)" else "筛选") }, leadingIcon = { Icon(Icons.Rounded.FilterList, null, Modifier.size(18.dp)) })
                }
                HorizontalDivider()
                if (loading.isNotEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (preferenceResult.isFailure || selectorResult.isFailure) Text("偏好配置无法读取，暂用默认值；请检查设置备份。", color = MaterialTheme.colorScheme.error)
                if (failures.isNotEmpty()) TextButton({ panel = "sources" }) { Text("${failures.size} 个数据源查询失败 · 查看 / 重试", style = MaterialTheme.typography.labelSmall) }
                initError?.let { TextButton({ retry++ }) { Text("初始化失败 · 重试", color = MaterialTheme.colorScheme.error) } }
                if (ready && sources.isEmpty()) TextButton(onSources) { Text("没有启用的 BT 数据源 · 管理订阅") }
                val click: (BtResourceRow) -> Unit = { row -> selected = row.id; chosen = visible.find { it.id == row.id }; copied = false }
                val longClick: (BtResourceRow) -> Unit = { row -> visible.find { it.id == row.id }?.let { entry -> media.action("torrentCopy", buildJsonObject { put("url", entry.item.url) }) { copied = true } } }
                if (isTable) BtTableContent(included, excluded, revealExcluded, { revealExcluded = true }, click, longClick, Modifier.fillMaxWidth().weight(1f), isLoading = !ready || loading.isNotEmpty())
                else BtCompactListContent(included, excluded, revealExcluded, { revealExcluded = true }, click, longClick, Modifier.fillMaxWidth().weight(1f), isLoading = !ready || loading.isNotEmpty())
                MediaStatus(media)
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${included.size} 条 · 第 $page 页", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                    TextButton({ page--; revealExcluded = false }, enabled = page > 1 && loading.isEmpty()) { Text("上一页") }
                    TextButton({ page++; revealExcluded = false }, enabled = results.filterKeys { sourceId == null || it == sourceId }.values.any { it.hasNext } && loading.isEmpty()) { Text("下一页") }
                }
            }
            BtInlineSheet(panel == "sources", { panel = "" }, "bt_source_sheet", Modifier.matchParentSize()) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
                    Text("数据源", Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.titleLarge)
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                        BtSourceItem({ Icon(Icons.Rounded.AllInclusive, null) }, "全部数据源", { Text("${all.size} 条") }, sourceId == null, true, { sourceId = null; panel = "" }, null, false, Modifier)
                        sources.forEach { source ->
                            BtSourceItem({ Icon(Icons.Rounded.RssFeed, null) }, source.title, {
                                Text(when { source.id in loading -> "查询中"; source.id in failures -> "查询失败"; else -> "${results[source.id]?.items?.size ?: 0} 条" })
                            }, sourceId == source.id, true, { sourceId = source.id; panel = "" }, if (source.id in failures) { { TextButton({ scope.launch { fetchSource(source.id) } }) { Text("重试") } } } else null, false, Modifier)
                            failures[source.id]?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                        }
                    }
                    TextButton(onSources) { Text("管理订阅") }
                }
            }
            BtInlineSheet(panel == "filters", { panel = "" }, "bt_filter_sheet", Modifier.matchParentSize()) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("筛选", style = MaterialTheme.typography.titleLarge); Spacer(Modifier.weight(1f))
                        TextButton({ resolution = null; subtitle = null; alliance = null }) { Text("重置") }
                    }
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        BtFilterGroup("分辨率", resolutions, activeResolution, { resolution = it })
                        BtFilterGroup("字幕", subtitles, activeSubtitle, { subtitle = it })
                        BtFilterGroup("字幕组", alliances, activeAlliance, { alliance = it })
                    }
                    Button({ panel = "" }, Modifier.fillMaxWidth()) { Text("完成") }
                }
            }
        }
    }
    val dismiss = { if (panel.isNotEmpty()) panel = "" else onDismiss() }
    if (embedded) Surface(modifier, color = MaterialTheme.colorScheme.surfaceContainerLow) { content() }
    else if (useDialog) Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLow) { content() }
    }
    else ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.94f)) { content() }
    }
    if (panel == "help") AlertDialog(onDismissRequest = { panel = "" }, title = { Text("浏览器下载") }, text = { Text("当前提供 BT 订阅选源。RSS 标题用于提取集号、分辨率与字幕；未知集号可通过关闭按集筛选查看，季度仍需自行核对。WebTorrent 仅连接 WebRTC / Web Seed，普通 BT 节点不能直连。") }, confirmButton = { TextButton({ panel = "" }) { Text("关闭") } })
    chosen?.let { entry -> val item = entry.item
        AlertDialog(onDismissRequest = { if (!media.busy) chosen = null }, title = { Text("添加到下载") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(item.title); if (item.size > 0) Text(mediaBytes(item.size))
                Text("整个种子会加入下载，可在下载页选择文件。没有 WebRTC 节点或 Web Seed 时无法传输。")
                TextButton({ media.action("torrentCopy", buildJsonObject { put("url", item.url) }) { copied = true } }, enabled = !media.busy) { Text(if (copied) "已复制链接" else "复制链接") }
                MediaStatus(media)
            }
        }, confirmButton = { TextButton({ media.action("torrentAdd", buildJsonObject { put("url",item.url); put("title",item.title); put("subjectTitle",subject.title); put("subjectId",subject.id); put("cover",subject.cover); put("episode",episode) }) { chosen = null; onDownloads() } }, enabled = !media.busy) { Text("开始下载") } }, dismissButton = { TextButton({ chosen = null }, enabled = !media.busy) { Text("取消") } })
    }
}
