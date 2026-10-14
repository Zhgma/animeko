// SPDX-License-Identifier: AGPL-3.0-only
@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, kotlin.js.ExperimentalWasmJsInterop::class)

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.HtmlElementView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import me.him188.ani.app.ui.subject.AiringLabel
import me.him188.ani.app.ui.subject.episode.details.EpisodePlaybackCard
import me.him188.ani.app.ui.subject.episode.details.EpisodePlaybackListItem
import me.him188.ani.app.ui.subject.episode.details.EpisodeDetailsScaffold
import me.him188.ani.app.ui.subject.episode.episodeScreenLayoutMode
import me.him188.ani.app.ui.subject.episode.EpisodeSidebarState
import me.him188.ani.app.ui.foundation.theme.LocalBrowserThemeSettings
import me.him188.ani.app.ui.foundation.theme.appColorScheme
import me.him188.ani.app.ui.mediaselect.summary.MediaSelectorSummaryBannerContent
import me.him188.ani.app.videoplayer.ui.progress.PlayerControllerBar
import me.him188.ani.app.ui.subject.episode.EpisodeScreenLayout
import me.him188.ani.app.ui.subject.episode.EpisodeScreenLayoutMode
import me.him188.ani.app.ui.subject.episode.video.topbar.EpisodePlayerTitle
import me.him188.ani.datasources.api.source.BrowseEpisode
import org.w3c.dom.HTMLElement

@JsFun("(json, callback) => window.aniCreateVideo(json, callback)")
private external fun createBrowserVideo(json: String, callback: (String) -> Unit): HTMLElement
@JsFun("(element, action, value) => element.aniCommand(action, value)")
private external fun videoCommand(element: HTMLElement, action: String, value: Double)
@JsFun("element => element.aniDispose()")
private external fun disposeVideo(element: HTMLElement)

@Serializable
private data class BrowserVideoState(
    val position: Int = 0, val time: Double = 0.0, val duration: Double = 0.0,
    val paused: Boolean = true, val muted: Boolean = false, val loading: Boolean = true,
    val message: String = "正在提取视频…", val failed: Boolean = false,
    val rate: Double = 1.0, val fullscreen: Boolean = false, val saveRate: Double? = null,
)

private fun videoTime(seconds: Double): String {
    val value = seconds.toInt().coerceAtLeast(0)
    return "${value / 60}:${(value % 60).toString().padStart(2, '0')}"
}

/** The episode page exists independently of resource discovery or video extraction. */
@Composable
fun WebPlayerScreen(subject: Subject, episode: WebPlaybackEpisode, catalog: List<WebPlaybackEpisode>,
    payload: JsonObject?, onClose: () -> Unit, onSwitchSource: () -> Unit,
    onSelectEpisode: (WebPlaybackEpisode) -> Unit, onPlaybackEntry: (JsonObject?) -> Unit) {
    val density=LocalDensity.current
    var controllerHeight by remember {mutableStateOf(96.dp)}
    val scope=rememberCoroutineScope()
    var watchedOverrides by remember { mutableStateOf<Map<Int,Boolean>>(emptyMap()) }
    var watchError by remember { mutableStateOf<String?>(null) }
    var updating by remember { mutableStateOf(false) }
    fun toggleWatched(target: WebPlaybackEpisode) {
        if(updating) return
        val watched=watchedOverrides[target.id] ?: target.watched
        scope.launch {
            updating=true;watchError=null
            try {updateWebEpisode(subject.id,target.id.toLong(),!watched);watchedOverrides=watchedOverrides+(target.id to !watched)}
            catch(e: CancellationException) {throw e}
            catch(e: Exception) {watchError=e.message}
            finally {updating=false}
        }
    }
    val config = remember { readWebPlayerConfig() }
    val playerPayload = remember(payload) { payload?.let { JsonObject(it + ("playerConfig" to webJson.encodeToJsonElement(config))) } }
    var state by remember(payload) { mutableStateOf(BrowserVideoState(position=payload?.get("index")?.jsonPrimitive?.intOrNull ?: 0,
        loading=payload!=null, message=if(payload==null) "请选择数据源" else "正在提取视频…")) }
    var element by remember { mutableStateOf<HTMLElement?>(null) }
    val fullscreen = rememberUpdatedState(state.fullscreen)
    val sidebar = remember { EpisodeSidebarState { fullscreen.value } }
    var speedMenu by remember { mutableStateOf(false) }
    var seeking by remember { mutableStateOf<Float?>(null) }
    var expandedEpisodes by remember { mutableStateOf(true) }
    var comments by remember { mutableStateOf(false) }
    val entry=payload?.get("episodes")?.jsonArray?.getOrNull(state.position)?.jsonObject
    val mapped=catalog.firstOrNull { it.id==entry?.get("catalogId")?.jsonPrimitive?.intOrNull }
    val displayEpisode=if(payload==null) episode else mapped
    val title=subject.displayTitle(LocalWebOriginalTitle.current)
    val episodeName=displayEpisode?.name ?: entry?.get("name")?.jsonPrimitive?.content.orEmpty()
    val playlist=payload?.get("episodes")?.jsonArray.orEmpty()
    LaunchedEffect(entry) { if(entry!=null) onPlaybackEntry(entry) }
    fun command(action: String, value: Double=0.0) { element?.let { videoCommand(it,action,value) } }
    val scheme=if(LocalBrowserThemeSettings.current.alwaysDarkInEpisodePage) appColorScheme(true) else MaterialTheme.colorScheme
    MaterialTheme(colorScheme=scheme) {
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide=maxWidth>=840.dp || (maxWidth>=600.dp && maxHeight<480.dp)
            val compactPlayerHeight=maxWidth*9f/16f+56.dp+controllerHeight
            val mode=episodeScreenLayoutMode(state.fullscreen,wide,sidebar.isVisible,wide)
            Surface(Modifier.fillMaxSize()) {
                EpisodeScreenLayout(mode, video={
                    Box((if(mode==EpisodeScreenLayoutMode.COMPACT) Modifier.fillMaxWidth().height(compactPlayerHeight) else Modifier.fillMaxSize()).background(Color.Black)) {
                        if(playerPayload!=null) key(playerPayload) {
                            HtmlElementView(factory={createBrowserVideo(playerPayload.toString()) { json ->
                                val next=webJson.decodeFromString<BrowserVideoState>(json)
                                state=if(next.saveRate!=null && config.rememberPlaybackSpeed) runCatching {
                                    val current=readWebPlayerConfig()
                                    val saved=current.copy(playbackSpeed=next.saveRate.toFloat().coerceIn(current.minPlaybackSpeed..current.maxPlaybackSpeed))
                                    check(aniWrite("retainedSettings",mergeWatchingSettings(readWatchingSettings(),"videoScaffoldConfig",webJson.encodeToJsonElement(saved).jsonObject).toString()))
                                    next
                                }.getOrElse { next.copy(message="倍速已调整，但配置保存失败。") } else next
                            }.also {element=it}},
                                // HTML interop is above the Compose canvas; keep its bounds clear of shared controls.
                                modifier=Modifier.fillMaxSize().padding(top=56.dp,bottom=controllerHeight),
                                update={it.style.visibility=if(state.message.isNotBlank()) "hidden" else "visible"},onRelease={disposeVideo(it);if(element==it) element=null})
                        }
                        CompositionLocalProvider(LocalContentColor provides Color.White) {
                            Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha=.7f),Color.Transparent))).height(56.dp).padding(end=8.dp),verticalAlignment=Alignment.CenterVertically) {
                                IconButton(onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack,"返回番剧详情") }
                                EpisodePlayerTitle(displayEpisode?.sort?.toString().orEmpty(),episodeName,title,Modifier.weight(1f))
                                if(wide) IconButton({sidebar.isVisible=!sidebar.isVisible}) { Icon(if(sidebar.isVisible) Icons.Rounded.ChevronRight else Icons.Rounded.ChevronLeft,if(sidebar.isVisible) "收起详情" else "展开详情") }
                            }
                            if(state.message.isNotBlank()) Column(Modifier.align(Alignment.Center).padding(horizontal=24.dp,vertical=56.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                                if(state.loading) CircularProgressIndicator(Modifier.size(24.dp))
                                Text(state.message,style=MaterialTheme.typography.bodySmall,maxLines=3,overflow=TextOverflow.Ellipsis)
                                if(payload==null) TextButton(onSwitchSource) { Text("选择数据源") }
                                else if(state.failed) Row { TextButton({command("retry")}) {Text("重试")};TextButton(onSwitchSource) {Text("切换数据源")} }
                            }
                            PlayerControllerBar(
                                startActions={
                                    IconButton({command("toggle")},enabled=payload!=null && !state.failed) { Icon(if(state.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,if(state.paused) "播放" else "暂停") }
                                    if(wide) IconButton({command("episode",(state.position+1).toDouble())},enabled=payload!=null && state.position<playlist.lastIndex) {Icon(Icons.Rounded.SkipNext,"下一集")}
                                },progressIndicator={Text("${videoTime(state.time)} / ${videoTime(state.duration)}")},
                                progressSlider={Slider(seeking ?: state.time.toFloat(),{seeking=it},
                                    onValueChangeFinished={seeking?.let {command("seek",it.toDouble())};seeking=null},
                                    valueRange=0f..state.duration.toFloat().coerceAtLeast(1f),enabled=state.duration>0,
                                    modifier=Modifier.fillMaxWidth().height(24.dp).semantics {contentDescription="播放进度"})},
                                danmakuEditor={Text("弹幕暂未接入",color=Color.White.copy(alpha=.6f))},
                                endActions={
                                    Box { TextButton({speedMenu=true},enabled=payload!=null) {Text("${state.rate}×",color=Color.White)}
                                        DropdownMenu(speedMenu,{speedMenu=false}) {
                                            ((config.minPlaybackSpeed*4).toInt()..(config.maxPlaybackSpeed*4).toInt()).forEach {step ->
                                                DropdownMenuItem(text={Text("${step/4.0}×")},onClick={command("rate",step/4.0);speedMenu=false})
                                            }
                                        }
                                    }
                                    if(wide) IconButton({command("mute")},enabled=payload!=null) {Icon(if(state.muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,"静音切换")}
                                    IconButton({command("fullscreen")},enabled=payload!=null) {Icon(if(state.fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,"全屏")}
                                },expanded=wide,
                                modifier=Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged {controllerHeight=with(density) {it.height.toDp()}}.background(Brush.verticalGradient(listOf(Color.Transparent,Color.Black.copy(alpha=.85f)))))
                        }
                    }
                }, secondary={
                    Column(Modifier.fillMaxSize()) {
                        SecondaryScrollableTabRow(if(comments) 1 else 0,edgePadding=0.dp,containerColor=MaterialTheme.colorScheme.surfaceContainerLow) {
                            Tab(!comments,{comments=false},Modifier.height(44.dp),text={Text("详情")})
                            Tab(comments,{comments=true},Modifier.height(44.dp),text={Text("评论")})
                        }
                        if(comments) {
                            if(displayEpisode!=null) WebEpisodeComments(displayEpisode.id)
                            else Text("此来源的集号尚未与番剧匹配，请先从剧集列表选择。",Modifier.padding(16.dp))
                        } else EpisodeDetailsScaffold(
                            subjectTitle={Text(title,maxLines=1,overflow=TextOverflow.Ellipsis)},favoriteButton={},loadError={watchError?.let {Text(it,color=MaterialTheme.colorScheme.error)}},airingStatus={ AiringLabel(webSubjectStatus(subject, rememberWebEpisodeTime()).label, style=MaterialTheme.typography.bodyMedium) },
                            episodeInfo={Text(listOf(displayEpisode?.sort?.toString(),episodeName).filterNotNull().joinToString("  "),Modifier.padding(vertical=8.dp),style=MaterialTheme.typography.bodyLarge)},
                            mediaSelectorItem={padding -> MediaSelectorSummaryBannerContent(onSwitchSource,Modifier.padding(padding)) {
                                Column(Modifier.padding(vertical=4.dp)) {
                                    Text(payload?.get("sourceName")?.jsonPrimitive?.content ?: "请选择数据源",style=MaterialTheme.typography.titleSmall)
                                    payload?.get("channel")?.jsonPrimitive?.content?.let {Text(it,style=MaterialTheme.typography.bodySmall)}
                                }
                            }},danmakuStatisticsSummary={Text("弹幕暂未接入",color=MaterialTheme.colorScheme.outline,style=MaterialTheme.typography.bodySmall)},danmakuStatistics={},
                            episodeListSection={
                                Column(Modifier.fillMaxWidth().padding(horizontal=16.dp)) {
                                    Row(Modifier.fillMaxWidth().clickable {expandedEpisodes=!expandedEpisodes}.padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
                                        Text("剧集列表",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                                        Text("共 ${catalog.size} 集",style=MaterialTheme.typography.bodySmall)
                                        Icon(if(expandedEpisodes) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,"展开或收起剧集")
                                    }
                                    if(expandedEpisodes) {
                                        if(wide) Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {catalog.forEach { candidate ->
                                            EpisodePlaybackListItem(candidate.sort.toString(),candidate.name,displayEpisode?.id==candidate.id,
                                                watchedOverrides[candidate.id] ?: candidate.watched,{onSelectEpisode(candidate)},{toggleWatched(candidate)})
                                        }} else LazyRow(horizontalArrangement=Arrangement.spacedBy(12.dp)) {items(catalog,key={it.id}) {candidate ->
                                            val watched=watchedOverrides[candidate.id] ?: candidate.watched
                                            EpisodePlaybackCard(candidate.sort.toString(),candidate.name,candidate.image,watched,watched,
                                                displayEpisode?.id==candidate.id,{onSelectEpisode(candidate)},{toggleWatched(candidate)})
                                        }}
                                    }
                                }
                            },subjectRecommendations={},onExpandSubject=onClose,modifier=Modifier.weight(1f))
                    }
                },modifier=Modifier.fillMaxSize())
            }
        }
    }
}

}
