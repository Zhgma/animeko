import androidx.compose.ui.window.Popup
import me.him188.ani.app.ui.settings.tabs.media.source.AddMediaSubscriptionDialog
import me.him188.ani.app.ui.settings.tabs.media.source.MangaSubscriptionPresentation
import me.him188.ani.app.ui.settings.tabs.media.source.MangaSubscriptionRow
import me.him188.ani.app.ui.settings.tabs.media.source.MediaSourceSubscriptionGroupFrame
import me.him188.ani.app.ui.settings.tabs.media.source.SubscriptionItemContent
import me.him188.ani.app.ui.settings.tabs.media.source.MediaSourceItemContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.Alignment
import me.him188.ani.app.tools.LocalTimeFormatter
import me.him188.ani.app.tools.TimeFormatter
import me.him188.ani.app.tools.formatDateTime
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.*
import me.him188.ani.app.ui.settings.framework.components.*
import me.him188.ani.app.ui.lang.*
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.him188.ani.app.ui.settings.combineSourceConfigurations

@Composable fun SettingsScope.WebSubscriptions() {
    val media = rememberBrowserMedia()
    var browserOptions by remember { mutableStateOf(false) }
    var selecting by remember { mutableStateOf(false) }
    var selectedSources by remember { mutableStateOf(setOf<String>()) }
    var deleteSources by remember { mutableStateOf(false) }
    val animeSubscriptions = media.state.subscriptions.filter { it.kind != "manga" }
    val remoteSubscriptions = animeSubscriptions.filter { it.url.isNotBlank() }
    val allSourceIds = animeSubscriptions.flatMap { sub -> if (sub.kind == "rss") listOf(sub.id + "/" + sub.id) else sub.sources.map { sub.id + "/" + it.id } }
    fun batchSources(operation: String, enabled: Boolean = true) = media.action("subscriptionBatch", buildJsonObject {
        put("operation", operation); put("enabled", enabled); putJsonArray("sources") { selectedSources.forEach { add(it) } }
    }) { if (operation == "delete") { selectedSources = emptySet(); selecting = false } }
    var sourceMenu by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf<Pair<String, FeedSource>?>(null) }
    var onlineSource by remember { mutableStateOf<String?>(null) }
    var keyword by remember { mutableStateOf("") }
    var remove by remember { mutableStateOf<BrowserSubscription?>(null) }
    var showResults by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var transfer by remember { mutableStateOf("") }
    var configuration by remember { mutableStateOf("") }
    var transferMessage by remember { mutableStateOf("") }
    var transferBusy by remember { mutableStateOf(false) }
    fun transferAction(block: suspend () -> Unit) {
        if (transferBusy) return
        transferBusy = true; transferMessage = ""
        scope.launch { try { block() } catch (e: CancellationException) { throw e }
            catch (e: Exception) { transferMessage = e.message ?: "配置操作失败" } finally { transferBusy = false } }
    }
    suspend fun exportConfiguration(id: String? = null, source: String? = null): String {
        val result = webJson.parseToJsonElement(request("subscriptionConfiguration", buildJsonObject { if (id != null) put("id",id); if (source != null) put("source",source) }.toString()).raw).jsonObject
        return combineSourceConfigurations(result.getValue("configurations").jsonArray.map { it.jsonPrimitive.content })
    }
    fun copyConfiguration(id: String? = null, source: String? = null) = transferAction {
        configuration = exportConfiguration(id, source); transfer = "export"
        request("configCopy",buildJsonObject { put("content",configuration) }.toString()); transferMessage = "已复制，可在 App 的数据源页面导入"
    }
    CompositionLocalProvider(LocalTimeFormatter provides remember { TimeFormatter() }) {
        MediaSourceSubscriptionGroupFrame(media.state.refreshing, { adding = true }, { media.action("subscriptionRefreshAll") }, enabled = !media.busy) {
            if (remoteSubscriptions.isEmpty()) TextItem { Text("暂无订阅") }
            remoteSubscriptions.forEachIndexed { index, sub ->
                if (index > 0) HorizontalDividerItem()
                var menu by remember(sub.id) { mutableStateOf(false) }
                val update = if (sub.error.isNotEmpty()) sub.error else if (sub.updated == 0L) "尚未更新" else "上次更新 " + formatDateTime(sub.updated)
                SubscriptionItemContent(sub.url.ifBlank { sub.title }, update) {
                    Box {
                        IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, "订阅操作") }
                        DropdownMenu(menu, { menu = false }) {
                            if (sub.url.isNotBlank()) DropdownMenuItem(text = { Text(stringResource(Lang.settings_media_source_subscription_copy_link)) }, leadingIcon = { Icon(Icons.Rounded.Share, null) }, onClick = { menu = false; transferAction { request("configCopy",buildJsonObject { put("content",sub.url) }.toString()); transferMessage = "订阅链接已复制" } })
                            DropdownMenuItem(text = { Text(stringResource(Lang.settings_media_source_subscription_export_all)) }, leadingIcon = { Icon(Icons.Rounded.Share, null) }, onClick = { menu = false; copyConfiguration(sub.id) }, enabled = !transferBusy)
                            DropdownMenuItem(text = { Text("更新订阅") }, leadingIcon = { Icon(Icons.Rounded.Refresh, null) }, onClick = { menu = false; media.action("subscriptionRefresh",mediaId(sub.id)) }, enabled = sub.url.isNotBlank() && !media.busy)
                            DropdownMenuItem(text = { Text(if(sub.enabled) "停用订阅" else "启用订阅") }, onClick = { menu = false; media.action("subscriptionToggle",buildJsonObject { put("id",sub.id); put("enabled",!sub.enabled) }) }, enabled = !media.busy)
                            DropdownMenuItem(text = { Text(stringResource(Lang.settings_media_source_subscription_delete), color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) }, onClick = { menu = false; remove = sub })
                        }
                    }
                }
            }
        }
    }
    CompositionLocalProvider(LocalTimeFormatter provides remember { TimeFormatter() }) {
        MangaSubscriptionPresentation(
            subscriptions = media.state.subscriptions.filter { it.kind == "manga" }.map { sub -> MangaSubscriptionRow(
                sub.id, sub.title, sub.url, sub.enabled, sub.sources.count { it.supported },
                sub.sources.filterNot { it.supported }.map { it.title }, sub.updated.takeIf { it > 0 }, sub.error.takeIf { it.isNotEmpty() },
            ) },
            onSave = { id, title, link -> request("mangaSubscriptionSave", buildJsonObject { if(id != null) put("id",id); put("name",title); put("url",link) }.toString()); media.refresh() },
            onRefresh = { request("subscriptionRefresh",mediaId(it).toString()); media.refresh() },
            onEnabled = { id, enabled -> request("subscriptionToggle",buildJsonObject { put("id",id); put("enabled",enabled) }.toString()); media.refresh() },
            onDelete = { request("subscriptionRemove",mediaId(it).toString()); media.refresh() },
            onCopy = { request("configCopy",buildJsonObject { put("content",it) }.toString()) },
            onPaste = { request("configReadClipboard").raw },
            description = "支持 App 漫画 sub 配置。漫画阅读适配器尚未接入网页；配置保留用于跨端导出。更新失败保留上次来源。",
        )
    }
    val sourceCount = animeSubscriptions.sumOf { if(it.kind == "rss") 1 else it.sources.size }
    Group(title = { Text(if (selecting) stringResource(Lang.settings_media_source_selected_count,selectedSources.size) else stringResource(Lang.settings_media_source_list,sourceCount)) },
        description = { Text("播放时查询已启用的数据源。可多选启用、禁用或删除；原生编辑器、测速及临时启用尚未迁移。") },
        actions = {
            if (selecting) {
                IconButton({ selecting = false; selectedSources = emptySet() }) { Icon(Icons.Rounded.Close, "退出多选") }
                IconButton({ selectedSources = if (selectedSources.size == allSourceIds.size) emptySet() else allSourceIds.toSet() }) { Icon(Icons.Rounded.SelectAll, "全选 / 取消全选") }
            } else IconButton({ selecting = true }) { Icon(Icons.Rounded.Checklist, stringResource(Lang.settings_media_source_enter_selection_mode)) }
            IconButton({ configuration = ""; transferMessage = ""; transfer = "import" }) { Icon(Icons.Rounded.Add, stringResource(Lang.settings_media_source_add)) }
            Box {
                IconButton({ sourceMenu = true }) { Icon(Icons.Rounded.MoreVert, "数据源操作") }
                DropdownMenu(sourceMenu,{ sourceMenu = false }) {
                    DropdownMenuItem(text={Text("恢复默认订阅")},onClick={sourceMenu=false;media.action("mediaRestoreDefaults",defaultMediaCatalog())},enabled=!media.busy)
                    DropdownMenuItem(text = { Text("导入配置文件") }, onClick = { sourceMenu = false; media.action("subscriptionImport") })
                    DropdownMenuItem(text = { Text("复制全部配置") }, onClick = { sourceMenu = false; copyConfiguration() }, enabled = sourceCount > 0)
                    DropdownMenuItem(text = { Text("导出配置文件") }, onClick = { sourceMenu = false; transferAction { request("configExport",buildJsonObject { put("content",exportConfiguration()); put("kind","sources") }.toString()) } }, enabled = sourceCount > 0)
                    DropdownMenuItem(text = { Text("浏览器订阅选项") }, onClick = { sourceMenu = false; browserOptions = true })
                }
            }
        }) {
        MediaStatus(media)
        if (transferMessage.isNotEmpty() && transfer.isEmpty()) Text(transferMessage, Modifier.padding(16.dp))
        animeSubscriptions.forEach { sub ->
            val sources = if(sub.kind == "rss") listOf(FeedSource(sub.id,sub.title,sub.url,sub.enabled,true)) else sub.sources
            sources.forEach { source ->
                var menu by remember(sub.id,source.id) { mutableStateOf(false) }
                val enabled = sub.enabled && source.enabled
                MediaSourceItemContent(source.title,
                    if (!source.supported) source.reason else sub.url.takeIf { it.isNotBlank() }?.let { "来自订阅 $it" } ?: "本地数据源配置",
                    enabled,
                    selectionMode = selecting, selected = sub.id + "/" + source.id in selectedSources,
                    onToggleSelected = { val key = sub.id + "/" + source.id; selectedSources = if (key in selectedSources) selectedSources - key else selectedSources + key },
                    sourceIcon = { Icon(if(source.kind=="web") Icons.Rounded.Language else Icons.Rounded.RssFeed,null,Modifier.size(28.dp)) },
                    kindIcon = { Icon(if(source.kind=="web") Icons.Rounded.PlayArrow else Icons.Rounded.Download,null,Modifier.size(20.dp)) },
                    actions = {
                        Box {
                            IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert,"数据源操作：${source.title}") }
                            DropdownMenu(menu,{menu = false}) {
                                DropdownMenuItem(text = { Text(if(source.enabled) "禁用" else "启用") }, leadingIcon = { Icon(if(source.enabled) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,null) }, onClick = {
                                    menu = false
                                    if(sub.kind == "rss") media.action("subscriptionToggle",buildJsonObject { put("id",sub.id);put("enabled",!sub.enabled) })
                                    else media.action("subscriptionSourceToggle",buildJsonObject { put("id",sub.id);put("source",source.id);put("enabled",!source.enabled) })
                                }, enabled = !media.busy)
                                DropdownMenuItem(text = { Text(if(sub.kind == "rss") "查看条目 (${sub.items.size})" else "搜索 / 浏览") }, onClick = {
                                    menu = false
                                    if(source.kind=="web") onlineSource=sub.id+"/"+source.id
                                    else if(sub.kind == "rss") selected = sub.id else { searching = sub.id to source; keyword = "" }
                                }, enabled = enabled && source.supported)
                                DropdownMenuItem(text = { Text("复制配置") }, onClick = { menu = false; copyConfiguration(sub.id, source.id) }, enabled = !transferBusy)
                            }
                        }
                    })
                HorizontalDividerItem()
            }
        }
    }
    if (selecting) Popup(alignment = Alignment.BottomCenter) {
        Surface(Modifier.padding(16.dp), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 3.dp) {
        Row(horizontalArrangement = Arrangement.Center) {
        IconButton({ batchSources("enabled",true) },enabled = selectedSources.isNotEmpty() && !media.busy) { Icon(Icons.Rounded.Visibility,stringResource(Lang.settings_media_source_enable_selected)) }
        IconButton({ batchSources("enabled",false) },enabled = selectedSources.isNotEmpty() && !media.busy) { Icon(Icons.Rounded.VisibilityOff,stringResource(Lang.settings_media_source_disable_selected)) }
        IconButton({ deleteSources = true },enabled = selectedSources.isNotEmpty() && !media.busy) { Icon(Icons.Rounded.Delete,stringResource(Lang.settings_media_source_delete_selected)) }
    }
    } }
    if (deleteSources) AlertDialog(onDismissRequest = { deleteSources = false },
        title = { Text(stringResource(Lang.settings_media_source_delete_selected_title)) },
        text = { Text(stringResource(Lang.settings_media_source_delete_selected_confirmation,selectedSources.size)) },
        confirmButton = { TextButton({ batchSources("delete"); deleteSources = false }) { Text("删除") } },
        dismissButton = { TextButton({ deleteSources = false }) { Text("取消") } })
    if (selected != null) AlertDialog(onDismissRequest = { selected = null }, title = { Text("订阅条目") },
        text = { Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState())) { FeedItems(media.state.subscriptions.find { it.id == selected }?.items.orEmpty(),media) } },
        confirmButton = { TextButton({selected = null}) { Text("关闭") } })
    if (browserOptions) AlertDialog(onDismissRequest = { browserOptions = false }, title = { Text("浏览器订阅选项") }, text = {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) { Text("网页打开时自动更新",Modifier.weight(1f)); Switch(media.state.settings.autoRefresh,{media.settings(media.state.settings.copy(autoRefresh=it))}) }
            Text("仅更新启用的远程订阅；关闭网页后停止。",style = MaterialTheme.typography.bodySmall)
            DropdownItem(selected = { media.state.settings.refreshMinutes }, values = { listOf(5,15,30,60,180,360,1440) }, itemText = { Text("$it 分钟") }, onSelect = { media.settings(media.state.settings.copy(refreshMinutes=it)) }, title = {Text("更新间隔")})
            Text("RSS v1 与 web-selector v2 可浏览；网页播放受来源策略和浏览器能力限制。其他来源保留配置用于跨端导出。",style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton({browserOptions=false}) {Text("关闭")} })
    onlineSource?.let { WebOnlineMedia("",{onlineSource=null},it) }
    if (adding) AddMediaSubscriptionDialog(
        url, { url = it }, media.busy,
        onPaste = { transferAction { url = request("configReadClipboard").raw } },
        onConfirm = { media.action("subscriptionAdd", buildJsonObject { put("url",url) }) { adding = false; url = "" } },
        onDismiss = { adding = false }, error = media.error,
    )
    searching?.let { (id,source) -> AlertDialog(onDismissRequest = { searching = null }, title = { Text(source.title) }, text = { Column { OutlinedTextField(keyword, { keyword = it }, label = { Text("关键词（可留空）") }); MediaStatus(media) } }, confirmButton = { TextButton({ media.action("subscriptionSearch", buildJsonObject { put("id",id); put("source",source.id); put("keyword",keyword) }) { searching = null; showResults = true } }, enabled = !media.busy) { Text("读取") } }, dismissButton = { TextButton({ searching = null }) { Text("取消") } }) }
    if (showResults) AlertDialog(onDismissRequest = { showResults = false }, title = { Text("订阅搜索结果") }, text = { Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState())) { MediaStatus(media); FeedItems(media.feedItems, media) } }, confirmButton = { TextButton({ showResults = false }) { Text("关闭") } })
    remove?.let { sub -> AlertDialog(onDismissRequest = { remove = null }, title = { Text("删除订阅？") }, text = { Text(sub.title) }, confirmButton = { TextButton({ media.action("subscriptionRemove",mediaId(sub.id)) { remove = null } }, enabled = !media.busy) { Text("删除") } }, dismissButton = { TextButton({ remove = null }) { Text("取消") } }) }
    if (transfer.isNotEmpty()) AlertDialog(onDismissRequest = { if (!transferBusy && !media.busy) transfer = "" },
        title = { Text(if (transfer == "import") "粘贴 App 数据源配置" else "复制 App 数据源配置") },
        text = { Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (transfer == "import") {
                Text("在 App 中复制数据源配置后粘贴到这里。导入追加一个本地配置组，不覆盖已有订阅。")
                OutlinedTextField(name, { name = it }, label = { Text("名称（可选）") })
                TextButton({ transferAction { configuration = request("configReadClipboard").raw } }, enabled = !transferBusy) { Text("从剪贴板读取") }
            }
            OutlinedTextField(configuration, { configuration = it }, readOnly = transfer == "export", label = { Text("配置 JSON") }, modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 250.dp))
            Text("剪贴板不可用时可手动粘贴、全选复制。未知源会保留，运行支持取决于目标端。", style = MaterialTheme.typography.bodySmall)
            if (transferMessage.isNotEmpty()) Text(transferMessage)
            MediaStatus(media)
        } },
        confirmButton = { TextButton({
            if (transfer == "import") media.action("subscriptionPaste",buildJsonObject { put("content",configuration); put("name",name) }) { transfer = ""; configuration = ""; name = ""; transferMessage = "配置已导入" }
            else transferAction { request("configCopy",buildJsonObject { put("content",configuration) }.toString()); transferMessage = "已复制" }
        }, enabled = configuration.isNotBlank() && !transferBusy && !media.busy) { Text(if (transfer == "import") "导入配置" else "复制") } },
        dismissButton = { TextButton({ transfer = ""; configuration = "" }, enabled = !transferBusy && !media.busy) { Text("关闭") } })
}
@Composable private fun FeedItems(items: List<FeedItem>, media: BrowserMediaController) {
    var added by remember { mutableStateOf<String?>(null) }
    if (items.isEmpty()) Text("没有条目", Modifier.padding(16.dp))
    items.forEach { item ->
        ListItem(headlineContent = { Text(item.title, maxLines = 3, overflow = TextOverflow.Ellipsis) }, supportingContent = { Text(if (added == item.id) "已添加到下载页" else if (item.supported) listOf(item.date, "BT 链接；下载仍需 WebRTC 节点或 Web Seed").filter { it.isNotBlank() }.joinToString(" · ") else "没有可识别的磁力 / 种子链接") }, colors = SettingsDefaults.listItemColors())
        if (item.supported) TextButton({ media.action("torrentAdd", buildJsonObject { put("url",item.url) }) { added = item.id } }, enabled = !media.busy && added != item.id) { Text("添加下载") }
    }
}
