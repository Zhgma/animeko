// SPDX-License-Identifier: AGPL-3.0-only
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Public
import me.him188.ani.app.ui.mediaselect.selector.*
import me.him188.ani.app.ui.foundation.AsyncImage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.*
import me.him188.ani.app.domain.media.selector.standardBrowseEpisodeIndex
import me.him188.ani.app.ui.mediaselect.*
import me.him188.ani.app.ui.mediaselect.common.*
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.utils.serialization.BigNum
import me.him188.ani.app.data.repository.subject.toEpisodeType
import me.him188.ani.client.models.AniEpisodeCollection
import me.him188.ani.client.models.AniEpisodeType
import me.him188.ani.client.models.AniEpisodeCollectionType
import me.him188.ani.datasources.api.source.BrowseEpisode

/** The detail API's episode identity and both numbering schemes travel together. */
data class WebPlaybackEpisode(val id: Int, val sort: EpisodeSort, val ep: EpisodeSort?, val name: String, val watched: Boolean = false, val image: String? = null)
fun JsonObject.playbackEpisode() = WebPlaybackEpisode(
    get("id")?.jsonPrimitive?.intOrNull ?: 0,
    EpisodeSort(get("playback_sort")?.jsonPrimitive?.contentOrNull ?: get("sort")?.jsonPrimitive?.content.orEmpty()),
    (get("playback_ep") ?: get("ep"))?.jsonPrimitive?.contentOrNull?.let(::EpisodeSort),
    get("name_cn")?.jsonPrimitive?.contentOrNull.orEmpty().ifBlank { get("name")?.jsonPrimitive?.contentOrNull.orEmpty() },
    get("watched")?.jsonPrimitive?.booleanOrNull ?: false, get("imageMedium")?.jsonPrimitive?.contentOrNull,
)

fun AniEpisodeCollection.webEpisode(): JsonObject = buildJsonObject {
    put("id",episodeId); put("sort",sort); put("ep",ep); put("name",name); put("name_cn",nameCn)
    put("type",if(type==AniEpisodeType.MAIN) 0 else 1); put("airdate",airdate)
    put("watched",collectionType==AniEpisodeCollectionType.DONE); put("imageMedium",imageMedium)
    put("playback_sort",EpisodeSort(BigNum(sort),type.toEpisodeType()).toString())
    put("playback_ep",ep?.let { EpisodeSort(BigNum(it),type.toEpisodeType()).toString() })
}

fun browserHasBt(state: BrowserMediaState): Boolean = state.subjectSources.isNotEmpty() ||
    state.subscriptions.any { sub -> sub.enabled && (sub.kind == "rss" || sub.items.any { it.supported } || sub.sources.any { it.kind == "bt" && it.enabled && it.supported }) }
fun browserOnlineSources(state: BrowserMediaState): List<FeedSource> = state.subscriptions.filter { it.enabled }
    .flatMap { sub -> sub.sources.filter { it.kind == "web" && it.enabled && it.supported }.map { it.copy(id = sub.id + "/" + it.id) } }

/** Only an unambiguous exact subject name is safe without the App's series graph. */
fun exactOnlineSubject(name: String, aliases: List<String>): Boolean = aliases.any { it.trim().equals(name.trim(), ignoreCase = true) }

private data class OnlineMatch(val channel: String, val episode: BrowseEpisode, val episodes: List<BrowseEpisode>, val index: Int)
private data class OnlineMatchResult(val loading: Boolean = false, val failed: Boolean = false, val matches: List<OnlineMatch> = emptyList(), val message: String = "")

suspend fun readOnlinePage(url: String): Pair<String,String> {
    val data = webJson.parseToJsonElement(request("mediaOnlineRead",buildJsonObject { put("url",url) }.toString()).raw).jsonObject
    val html = data["text"]?.jsonPrimitive?.content.orEmpty()
    check(!html.contains("cf-chl-") && !html.contains("challenges.cloudflare.com")) { "来源需要网页验证，可切换其他来源。" }
    return html to data["url"]?.jsonPrimitive?.content.orEmpty()
}

@Composable
fun WebPlaybackSelector(subject: Subject, episode: WebPlaybackEpisode, onDismiss: () -> Unit,
    onDownloads: () -> Unit, onSources: () -> Unit, episodeCatalog: List<WebPlaybackEpisode> = listOf(episode)) {
    var watching by remember { mutableStateOf(episode) }
    var player by remember { mutableStateOf<JsonObject?>(null) }
    var showSelector by remember { mutableStateOf(false) }
    val media = rememberBrowserMedia()
    var ready by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf<MediaSelectorMode?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        media.refresh()
        if (media.error != null) { error = media.error; return@LaunchedEffect }
        try { mode = initialMediaSelectorMode(readMediaSelectorSettings().preferKind,browserHasBt(media.state)); ready = true }
        catch (e: Exception) { error = "选源偏好无效：${e.message}" }
    }
    val body: @Composable () -> Unit = {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(title = { Text("数据源") }, actions = {
                mode?.let { value -> MediaSelectorModeChip(value,{ mode = it },showBt = browserHasBt(media.state)) }
                IconButton({ showSelector=false }) { Icon(Icons.Rounded.Close,"关闭选源") }
            },windowInsets = WindowInsets(0,0,0,0))
            WatchingEpisodeCard(WatchingEpisode(watching.sort.toString(),watching.name,watching.ep?.takeIf { it != watching.sort }?.toString()),
                Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp))
            if (!ready && error == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
            Box(Modifier.weight(1f)) {
                when (mode) {
                    MediaSelectorMode.AUTO -> WebAutoMatch(subject,watching,episodeCatalog,browserOnlineSources(media.state),onManual={mode=MediaSelectorMode.MANUAL},onSources=onSources,onPlay={player=it;showSelector=false})
                    MediaSelectorMode.MANUAL -> WebOnlineMediaContent(subject.title,episode=watching,onPlay={player=it;showSelector=false})
                    MediaSelectorMode.BT -> WebSubjectMedia(subject,watching.sort.toString(),onDismiss,onDownloads,onSources,embedded=true,showHeader=false)
                    null -> Unit
                }
            }
        }
    }
    WebPlayerScreen(subject, watching, episodeCatalog, player,
        onClose=onDismiss, onSwitchSource={showSelector=true},
        onSelectEpisode={ target ->
            watching=target
            val mapped=player?.get("episodes")?.jsonArray?.indexOfFirst { it.jsonObject["catalogId"]?.jsonPrimitive?.intOrNull==target.id } ?: -1
            if(mapped>=0) player=JsonObject(player!! + ("index" to JsonPrimitive(mapped)))
            else { player=null;showSelector=true }
        }, onPlaybackEntry={ selected ->
            val selectedId=selected?.get("catalogId")?.jsonPrimitive?.intOrNull
            episodeCatalog.firstOrNull { it.id==selectedId }?.let { watching=it }
        })
    if(showSelector) BoxWithConstraints {
        if(maxWidth < 600.dp && maxHeight >= 480.dp) {
            ModalBottomSheet(onDismissRequest={showSelector=false},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
                Box(Modifier.fillMaxWidth().fillMaxHeight(0.94f)) { body() }
            }
        } else Dialog(onDismissRequest={showSelector=false},properties=DialogProperties(usePlatformDefaultWidth=false)) {
            BoxWithConstraints {
                val compact=maxHeight < MediaSelectorLayoutDefaults.CompactDialogMaxHeight
                Surface(if(compact) Modifier.fillMaxSize() else Modifier.widthIn(max=MediaSelectorLayoutDefaults.DialogMaxWidth).fillMaxWidth(0.94f).heightIn(max=MediaSelectorLayoutDefaults.DialogMaxHeight).fillMaxHeight(0.94f),
                    shape=if(compact) MaterialTheme.shapes.extraSmall else MaterialTheme.shapes.extraLarge) { body() }
            }
        }
    }
}

@Composable
private fun WebAutoMatch(subject: Subject, episode: WebPlaybackEpisode, episodeCatalog: List<WebPlaybackEpisode>, sources: List<FeedSource>, onManual: () -> Unit, onSources: () -> Unit, onPlay: (JsonObject) -> Unit) {
    val aliases = remember(subject.id) { (listOf(subject.title,subject.original) + subject.info.filter { it.label == "别名" }.flatMap { it.value.split(" / ") }).filter { it.isNotBlank() }.distinct() }
    var results by remember(subject.id,episode.id) { mutableStateOf<Map<String,OnlineMatchResult>>(emptyMap()) }
    var revision by remember { mutableIntStateOf(0) }
    var playError by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf(false) }
    var selectedMatch by remember { mutableStateOf<Pair<String,Int>?>(null) }
    var retrySource by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(subject.id,episode.id,sources,revision) {
        val limit = Semaphore(4)
        val querying = sources.filter { retrySource == null || it.id == retrySource }
        results = results + querying.associate { it.id to OnlineMatchResult(loading=true) }
        querying.forEach { source -> launch {
            limit.withPermit {
                try {
                    val config = source.config()
                    val auto = config["autoMatch"] as? JsonObject
                    if (auto?.get("enabled")?.jsonPrimitive?.booleanOrNull == false) {
                        results = results + (source.id to OnlineMatchResult(message="此来源仅供手动查找")); return@withPermit
                    }
                    val searchUrl = config["searchUrl"]?.jsonPrimitive?.content ?: error("来源缺少搜索地址")
                    val (html,url) = readOnlinePage(searchUrl.replace("{keyword}",subject.title.encodeURLParameter()).replace("{page}","1"))
                    val matching = parseOnlineSubjects(config,html,url).filter { exactOnlineSubject(it.name,aliases) }.distinctBy { it.fullUrl }
                    check(matching.size <= 1) { "同名条目不唯一，请手动核对季度" }
                    val matches = matching.singleOrNull()?.let { found ->
                        val (page,pageUrl) = readOnlinePage(found.fullUrl)
                        parseOnlineChannels(config,page,pageUrl).mapIndexedNotNull { index,channel ->
                            // Without a series graph, split numbering needs manual confirmation.
                            val selected = if (episode.ep != null && episode.ep != episode.sort) null else
                                standardBrowseEpisodeIndex(channel.episodes,episode.sort,episode.ep)
                            selected?.let { OnlineMatch(channel.name ?: channel.label ?: "线路 ${index+1}",channel.episodes[it],channel.episodes,it) }
                        }
                    }.orEmpty()
                    results = results + (source.id to OnlineMatchResult(matches=matches,message=if(matches.isEmpty()) "未找到明确匹配的当前集，可手动查找" else ""))
                } catch(e: CancellationException) { throw e }
                catch(e: Exception) { results = results + (source.id to OnlineMatchResult(failed=true,message=e.message ?: "来源查询失败")) }
            }
        } }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        if(sources.isEmpty()) TextButton(onSources) { Text("没有启用的网站来源 · 管理订阅") }
        WebSourcesColumnContent(sources, { results[it.id]?.failed == true }, onManual) { source ->
            val result = results[source.id]
            WebSourceCardContent(source.title, icon={
                val icon = source.format["arguments"]?.jsonObject?.get("iconUrl")?.jsonPrimitive?.contentOrNull
                if (icon.isNullOrBlank()) Icon(Icons.Rounded.Public,null,Modifier.size(24.dp))
                else AsyncImage(icon,source.title,Modifier.size(24.dp))
            }, channels={
                WebSourceChannelsContent(result?.matches.orEmpty().map { it.channel },
                    selectedMatch?.takeIf { it.first == source.id }?.second,
                    result?.loading != false, result?.failed == true, { index ->
                        val match = result!!.matches[index]
                        selectedMatch=source.id to index; playing=true; playError=null
                        scope.launch { try {
                            onPlay(buildJsonObject {
                                put("url",match.episode.url);put("title",subject.title);put("index",match.index)
                                putJsonArray("episodes") { match.episodes.forEach { entry ->
                                    add(buildJsonObject {
                                        put("url",entry.url);put("name",entry.name)
                                        val mapped = episodeCatalog.filter { candidate ->
                                            (candidate.ep == null || candidate.ep == candidate.sort) &&
                                                standardBrowseEpisodeIndex(match.episodes,candidate.sort,candidate.ep)?.let { match.episodes[it].url == entry.url } == true
                                        }.singleOrNull()
                                        mapped?.let { put("catalogId",it.id) }
                                    })
                                } }
                                put("source",source.config()["searchUrl"]!!.jsonPrimitive.content)
                                put("sourceName",source.title);put("channel",match.channel)
                            })
                        } catch(e: CancellationException) { throw e }
                        catch(e: Exception) { playError=e.message }
                        finally { playing=false } }
                    }, enabled=!playing)
                if(result?.message?.isNotBlank()==true) Text(result.message,style=MaterialTheme.typography.bodySmall,
                    color=if(result.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }, trailing={
                if(result?.failed==true) WebSourceRetryButton { retrySource=source.id; revision++ }
            }, modifier=Modifier.fillMaxWidth())
        }
        playError?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        TextButton({retrySource=null;revision++},enabled=results.values.none { it.loading }) { Text("重新查询") }
    }
}
