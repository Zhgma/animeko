// SPDX-License-Identifier: AGPL-3.0-only
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.encodeURLParameter
import io.ktor.http.encodedPath
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import me.him188.ani.app.domain.mediasource.web.WebSearchSubjectInfo
import me.him188.ani.app.domain.mediasource.web.WebSearchEpisodeInfo
import me.him188.ani.app.domain.mediasource.web.format.*
import me.him188.ani.app.ui.mediaselect.common.MediaSelectorSearchField
import me.him188.ani.app.ui.mediaselect.manual.ManualSourceSelectorContent
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.mediaselect.manual.ManualChannelChips
import me.him188.ani.app.ui.mediaselect.manual.ManualEpisodeGrid
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.app.domain.media.selector.standardBrowseEpisodeIndex
import me.him188.ani.datasources.api.source.BrowseChannel
import me.him188.ani.datasources.api.source.BrowseEpisode
import me.him188.ani.utils.xml.Html

private fun JsonObject.text(key: String, fallback: String = "") = this[key]?.jsonPrimitive?.contentOrNull ?: fallback
fun FeedSource.config() = format["arguments"]?.jsonObject?.get("searchConfig")?.jsonObject ?: error("数据源缺少搜索规则")
private fun baseUrl(config: JsonObject, url: String) = config.text("rawBaseUrl").ifBlank {
    URLBuilder(url).apply { encodedPath = "/"; parameters.clear(); fragment = "" }.buildString()
}
fun parseOnlineSubjects(config: JsonObject, html: String, url: String): List<WebSearchSubjectInfo> {
    val document = Html.parse(html,url)
    val base = baseUrl(config,url)
    val result = when(config.text("subjectFormatId","a")) {
        "a" -> SelectorSubjectFormatA.select(document,base,webJson.decodeFromJsonElement(config["selectorSubjectFormatA"] ?: buildJsonObject {}))
        "indexed" -> SelectorSubjectFormatIndexed.select(document,base,webJson.decodeFromJsonElement(config["selectorSubjectFormatIndexed"] ?: buildJsonObject {}))
        "json-path-indexed" -> SelectorSubjectFormatJsonPathIndexed.select(document,base,webJson.decodeFromJsonElement(config["selectorSubjectFormatJsonPathIndexed"] ?: buildJsonObject {}))
        else -> error("此搜索格式尚未支持")
    }
    return result ?: error("订阅中的搜索规则无效")
}
fun parseOnlineChannels(config: JsonObject, html: String, url: String): List<BrowseChannel> {
    val document=Html.parse(html,url)
    val base=baseUrl(config,url)
    val result=when(config.text("channelFormatId","no-channel")) {
        "index-grouped" -> SelectorChannelFormatIndexGrouped.select(document,base,webJson.decodeFromJsonElement(config["selectorChannelFormatFlattened"] ?: buildJsonObject {}))
        "no-channel" -> SelectorChannelFormatNoChannel.select(document,base,webJson.decodeFromJsonElement(config["selectorChannelFormatNoChannel"] ?: buildJsonObject {}))
        else -> error("此线路格式尚未支持")
    } ?: error("订阅中的线路规则无效")
    fun episodes(values: List<WebSearchEpisodeInfo>) = values.map { BrowseEpisode(it.name,it.playUrl,it.episodeSortOrEp) }
    return result.channelGroups?.map { BrowseChannel(it.name,it.label,episodes(it.episodes)) }
        ?: listOf(BrowseChannel(null,episodes=episodes(result.episodes)))
}

@Composable
fun WebOnlineMedia(initialKeyword: String, onDismiss: () -> Unit, initialSourceId: String? = null) {
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.widthIn(max=960.dp).fillMaxWidth(0.96f).fillMaxHeight(0.94f),shape=MaterialTheme.shapes.extraLarge) {
            WebOnlineMediaContent(initialKeyword, initialSourceId = initialSourceId)
        }
    }
}

@Composable
fun WebOnlineMediaContent(initialKeyword: String, initialSourceId: String? = null,
    episode: WebPlaybackEpisode? = null, onPlay: ((JsonObject) -> Unit)? = null) {
    var player by remember { mutableStateOf<JsonObject?>(null) }
    val media=rememberBrowserMedia()
    val scope=rememberCoroutineScope()
    var keyword by remember { mutableStateOf(initialKeyword) }
    var selectedSourceId by remember { mutableStateOf(initialSourceId) }
    var operation by remember { mutableStateOf<Job?>(null) }
    var operationVersion by remember { mutableIntStateOf(0) }
    var results by remember { mutableStateOf<List<WebSearchSubjectInfo>>(emptyList()) }
    var opened by remember { mutableStateOf<WebSearchSubjectInfo?>(null) }
    var channels by remember { mutableStateOf<List<BrowseChannel>>(emptyList()) }
    var channel by remember { mutableIntStateOf(0) }
    var selectedEpisode by remember { mutableStateOf<Int?>(null) }
    var loading by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val sources=media.state.subscriptions.filter { it.enabled }.flatMap { sub -> sub.sources.filter { it.kind=="web" && it.enabled && it.supported }.map { it.copy(id=sub.id+"/"+it.id) } }
    val selected=sources.firstOrNull { it.id==selectedSourceId } ?: sources.firstOrNull()
    fun run(block: suspend () -> Unit) {
        operation?.cancel()
        val version=++operationVersion
        loading=true;error=null
        operation=scope.launch { try { block() } catch(e:CancellationException){throw e} catch(e:Throwable){error=e.message ?: "来源请求失败"} finally{if(version==operationVersion)loading=false} }
    }
    suspend fun page(url:String): Pair<String,String> {
        val result=webJson.parseToJsonElement(request("mediaOnlineRead",buildJsonObject{put("url",url)}.toString()).raw).jsonObject
        val html=result.text("text")
        check(!html.contains("cf-chl-") && !html.contains("challenges.cloudflare.com")) { "来源需要网页验证，请在来源站点完成验证；网页暂不共享原生验证会话。" }
        return html to result.text("url")
    }
    fun search() { val source=selected ?: return; if(keyword.isBlank())return
        run { opened=null;channels=emptyList();results=emptyList();searched=false
            val url=source.config().text("searchUrl").replace("{keyword}",keyword.trim().encodeURLParameter()).replace("{page}","1")
            val (html,actual)=page(url);results=parseOnlineSubjects(source.config(),html,actual);searched=true
        }
    }
    LaunchedEffect(selected?.id) { if (selected != null) search() }
    fun preselect(index: Int): Int? = episode?.let { standardBrowseEpisodeIndex(channels.getOrNull(index)?.episodes.orEmpty(), it.sort, it.ep) }
            BoxWithConstraints {
                val columns=if(maxWidth>=600.dp) 6 else 3
                Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    if(sources.isEmpty()) {
                        Text("尚无已启用的在线来源。可添加 App 默认在线订阅，或在数据源管理中导入已有配置。")
                        Button({media.action("mediaRestoreDefaults",defaultMediaCatalog())},enabled=!media.busy){Text("恢复默认订阅")}
                    } else {
                        ManualSourceSelectorContent(sources, selected?.id, { selectedSourceId=it;opened=null;results=emptyList();channels=emptyList();searched=false },
                            id={it.id},name={it.title},icon={source ->
                                AsyncImage(source.format["arguments"]?.jsonObject?.get("iconUrl")?.jsonPrimitive?.contentOrNull,
                                    source.title,Modifier.size(24.dp))
                            },tier={source,_ -> source.format["arguments"]?.jsonObject?.get("tier")?.jsonPrimitive?.intOrNull?.let {
                                Text("T$it",style=MaterialTheme.typography.labelSmall)
                            }},modifier=Modifier.fillMaxWidth())
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            MediaSelectorSearchField(keyword,{keyword=it},::search,Modifier.weight(1f),enabled=!loading)
                            TextButton(::search,enabled=!loading&&keyword.isNotBlank()){Text("搜索")}
                        }
                    }
                    if(loading||media.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
                    (error ?: media.error)?.let { Text(it,color=MaterialTheme.colorScheme.error) }
                    if(opened==null) {
                        if(searched)Text("共 ${results.size} 条结果",style=MaterialTheme.typography.labelMedium)
                        LazyColumn(Modifier.weight(1f)) {
                            itemsIndexed(results) { _,subject -> ListItem(headlineContent={Text(subject.name,maxLines=1,overflow=TextOverflow.Ellipsis)},modifier=Modifier.clickable(enabled=!loading){
                                val source=selected ?: return@clickable
                                run {val (html,url)=page(subject.fullUrl);channels=parseOnlineChannels(source.config(),html,url);check(channels.any{it.episodes.isNotEmpty()}){"没有解析出线路或剧集；来源页面可能改变或需要验证"};opened=subject;channel=0;selectedEpisode=preselect(0)}
                            }) }
                        }
                    } else {
                        TextButton({opened=null;channels=emptyList()},enabled=!loading){Text("返回搜索 · ${opened?.name}",maxLines=1,overflow=TextOverflow.Ellipsis)}
                        ManualChannelChips(channels,channel,{channel=it;selectedEpisode=preselect(it)})
                        ManualEpisodeGrid(channels.getOrNull(channel)?.episodes.orEmpty(),selectedEpisode,{index ->
                            val episode=channels[channel].episodes[index]
                            run {(onPlay ?: { player=it })(buildJsonObject{put("sourceName",selected!!.title);put("channel",channels[channel].name ?: channels[channel].label.orEmpty());put("url",episode.url);put("title",opened?.name.orEmpty());put("source",selected!!.config().text("searchUrl"));put("index",index);putJsonArray("episodes") { channels[channel].episodes.forEach { entry -> add(buildJsonObject { put("url",entry.url);put("name",entry.name) }) }}});selectedEpisode=index}
                        },null,{},columns,Modifier.weight(1f))
                    }
                }
            }
    player?.let { payload -> WebPlayerScreen(
        Subject(id=0,title=payload["title"]?.jsonPrimitive?.content.orEmpty()),
        WebPlaybackEpisode(0,EpisodeSort(""),null,""),emptyList(),payload,
        onClose={player=null},onSwitchSource={player=null},onSelectEpisode={},onPlaybackEntry={}) }
}
