import me.him188.ani.app.ui.subject.SubjectScoreLabel
// SPDX-License-Identifier: AGPL-3.0-only
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import me.him188.ani.app.ui.foundation.widgets.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import kotlin.time.Instant
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.manga.TrackingCollectionTabs
import me.him188.ani.app.ui.subject.collection.*
import me.him188.ani.app.ui.subject.SubjectStatusStrings
import me.him188.ani.app.ui.subject.rememberSubjectStatusStrings
import me.him188.ani.app.ui.subject.renderEpAndSort
import me.him188.ani.app.ui.subject.collection.components.EditCollectionTypeDropDown
import me.him188.ani.app.ui.subject.collection.progress.SubjectProgressButtonContent
import me.him188.ani.app.ui.subject.episode.list.EpisodeListDialogContent
import me.him188.ani.client.models.AniEpisodeCollection
import me.him188.ani.client.models.AniEpisodeCollectionType
import me.him188.ani.client.models.AniEpisodeType
import me.him188.ani.datasources.api.topic.UnifiedCollectionType

internal data class WebCollectionProgress(val label: String, val button: String, val primary: Boolean,
    val episode: AniEpisodeCollection?, val done: Boolean)

/** Browser data adapts to the App's progress state and localized presentation. */
internal fun collectionProgress(subject: Subject, now: Instant, strings: SubjectStatusStrings): WebCollectionProgress {
    val status = webSubjectStatus(subject, now)
    val progress = status.progress
    return WebCollectionProgress(status.label.progressText(strings).orEmpty() +
        status.label.totalEpisodesText(strings)?.let { " · $it" }.orEmpty(),
        progress.buttonText(strings, now), progress.buttonIsPrimary,
        subject.animeCollection?.episodes?.firstOrNull { it.episodeId.toInt() == progress.episodeIdToPlay }, progress.isDone)
}

@Composable
fun WebAnimeCollection(session: WebResult, revision: Int, onRefresh: () -> Unit,
    avatar: @Composable (DpSize) -> Unit, onManga: () -> Unit, onLogin: () -> Unit, onOpen: (Int) -> Unit,
    onDownloads: () -> Unit, onSources: () -> Unit) {
    val pager = rememberPagerState(initialPage = 2) { 5 }
    val scope = rememberCoroutineScope()
    val counts = remember(session.userId, session.signedIn, revision) { mutableStateMapOf<Int, Int>() }
    val loading = remember { mutableStateMapOf<Int, Boolean>() }
    val types = listOf(5, 1, 3, 4, 2)
    LaunchedEffect(session.userId, session.signedIn, revision) {
        if(session.signedIn) try { counts.putAll(webCollectionCounts()) }
        catch(e: CancellationException) { throw e }
        catch(_: Exception) { /* Loaded pages still provide their own counts. */ }
    }
    val statusStrings = rememberSubjectStatusStrings()
    val original = LocalWebOriginalTitle.current
    var selected by remember { mutableStateOf<Subject?>(null) }
    var playback by remember { mutableStateOf<Pair<Subject, WebPlaybackEpisode>?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val now = rememberWebEpisodeTime()
    fun changeType(subject: Subject, type: UnifiedCollectionType) {
        if(saving) return
        scope.launch {
            saving=true; saveError=null
            try {
                val number = when(type) { UnifiedCollectionType.WISH -> 1; UnifiedCollectionType.DONE -> 2
                    UnifiedCollectionType.DOING -> 3; UnifiedCollectionType.ON_HOLD -> 4; UnifiedCollectionType.DROPPED -> 5; else -> 0 }
                request("libraryWrite",buildJsonObject { put("id",subject.id); put("type",2)
                    put("fields",buildJsonObject { put("type",number) }) }.toString())
                onRefresh()
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { saveError=e.message ?: "更新收藏失败" }
            finally { saving=false }
        }
    }
    CollectionPageLayout(
        title = { TrackingCollectionTabs(false) { if (it) onManga() } }, settingsIcon = {}, actions = {}, avatar = avatar,
        filters = { CollectionTypeScrollableTabRow(pager.currentPage, { scope.launch { pager.animateScrollToPage(it) } },
            itemLabel = { value -> val index = COLLECTION_TABS_SORTED.indexOf(value); CollectionTypeTabLabel(collectionLabel(types[index], false), counts[types[index]]) }) },
        isRefreshing = { loading[types[pager.currentPage]] == true }, onRefresh = onRefresh,
    ) { connection, padding ->
        CollectionPager(pager, Modifier.fillMaxSize()) { page ->
            val type = types[page]
            var offset by remember(session.userId, revision) { mutableIntStateOf(0) }
            var items by remember(session.userId, session.signedIn, revision) { mutableStateOf<List<Subject>>(emptyList()) }
            var error by remember(session.userId, session.signedIn) { mutableStateOf<String?>(null) }
            var retry by remember { mutableIntStateOf(0) }
            var hasMore by remember(session.userId, revision) { mutableStateOf(true) }
            val grid = rememberLazyGridState()
            LaunchedEffect(session.userId, session.signedIn, type, offset, revision, retry) {
                if (!session.signedIn) { items = emptyList(); return@LaunchedEffect }
                loading[type] = true; error = null
                try {
                    val result = request("collection", buildJsonObject { put("type", 2); put("status", type); put("offset", offset) }.toString())
                    items = (if(offset == 0) result.items else items + result.items).distinctBy { it.id }
                    counts[type] = result.total
                    hasMore = result.items.isNotEmpty() && items.size < result.total
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { error = e.message }
                finally { loading[type] = false }
            }
            val nearEnd by remember { derivedStateOf { (grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) >= items.size - 3 } }
            LaunchedEffect(nearEnd, items.size, loading[type]) {
                if(nearEnd && hasMore && items.isNotEmpty() && loading[type] != true && error == null && items.size < (counts[type] ?: 0)) offset += 30
            }
            val pullState = rememberPullToRefreshState()
            PullToRefreshBox(loading[type] == true && offset == 0, onRefresh, state=pullState,
                enabled=session.signedIn, touchOnly=true, indicator={
                    PullToRefreshDefaults.Indicator(modifier=Modifier.align(Alignment.TopCenter).padding(top=padding.calculateTopPadding()),
                        isRefreshing=loading[type] == true && offset == 0, state=pullState)
                }) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val spacing = if(maxWidth < 600.dp) 16.dp else 24.dp
                LazyVerticalGrid(GridCells.Adaptive(360.dp),
                    Modifier.fillMaxSize().then(if(connection != null) Modifier.nestedScroll(connection) else Modifier), state=grid,
                    contentPadding = PaddingValues(start=spacing/2, end=spacing/2, top=padding.calculateTopPadding()+spacing/2,
                        bottom=padding.calculateBottomPadding()+spacing/2)) {
                    item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(1.dp)) }
                    if(!session.signedIn || error != null || (items.isEmpty() && loading[type] != true)) item(span={GridItemSpan(maxLineSpan)}) {
                        Column(Modifier.padding(spacing/2)) {
                            if (!session.signedIn) { Text("登录 Animeko 账号后查看追番收藏"); Button(onLogin) { Text("登录 Animeko") } }
                            error?.let { WebError(it, { retry++ }) }
                            if(session.signedIn && error == null && items.isEmpty()) Text("这个分类还没有收藏")
                        }
                    }
                    items(items, key = { it.id }) { subject ->
                        var menu by remember { mutableStateOf(false) }
                        val progress = remember(subject,now,statusStrings) { collectionProgress(subject,now,statusStrings) }
                        WebNsfw(subject) { SubjectCollectionCard({ onOpen(subject.id) }, cover = {
                            AsyncImage(subject.animeCollection?.imageThumb?.ifBlank { subject.cover } ?: subject.cover, subject.title,
                                Modifier.height(148.dp).width(148.dp * 849f / 1200f), contentScale = ContentScale.Crop)
                        }, content = {
                            SubjectCollectionItemLayout(subject.displayTitle(original),
                                airingLabel={ Text(progress.label, style=MaterialTheme.typography.labelLarge, maxLines=2) },
                                rating={ SubjectScoreLabel(subject.score) },
                                onShowEpisodeList={selected=subject},
                                playButton={
                                    if(type != 2) SubjectProgressButtonContent(
                                        if(progress.done) "移至看过" else progress.button,progress.primary,
                                        { if(progress.done) changeType(subject,UnifiedCollectionType.DONE)
                                          else progress.episode?.let { playback=subject to it.webEpisode().playbackEpisode() } },
                                        enabled=!saving && (progress.done || progress.episode != null))
                                },
                                onShowMenu={if(!saving) menu=true},
                                menu={EditCollectionTypeDropDown(COLLECTION_TABS_SORTED[page],menu,{menu=false},{changeType(subject,it.type)})})
                        }, modifier=Modifier.padding(spacing/2), colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerHigh)) }
                    }
                    if(loading[type] == true) item(span={GridItemSpan(maxLineSpan)}) {
                        Box(Modifier.fillMaxWidth().padding(16.dp),contentAlignment=Alignment.Center) { CircularProgressIndicator() }
                    }
                }
            }
        }
        }
    }
    saveError?.let { message -> AlertDialog(onDismissRequest={saveError=null},title={Text("更新收藏失败")},text={Text(message)},
        confirmButton={TextButton({saveError=null}) { Text("关闭") }}) }
    selected?.let { subject ->
        val episodeTime = rememberWebEpisodeTime()
        EpisodeListDialogContent(subject.displayTitle(original),{selected=null},{selected=null;onDownloads()},
            onSubjectDetailsClick={onOpen(subject.id)},showMarkWatchedHint=true,episodes={
                val episodes=subject.animeCollection?.episodes.orEmpty()
                if(episodes.isEmpty()) Text("暂无剧集")
                listOf(true,false).forEach { main ->
                    val group=episodes.filter { (it.type == AniEpisodeType.MAIN) == main }
                    if(!main && group.isNotEmpty()) HorizontalDivider(Modifier.padding(vertical=16.dp))
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        group.forEach { episode ->
                            WebEpisodeStateButton(subject, episode.webEpisode(), episodeTime,
                                {selected=null;playback=subject to episode.webEpisode().playbackEpisode()},
                                { if(!saving) scope.launch {
                                    saving=true
                                    try {
                                        val watched=episode.collectionType != AniEpisodeCollectionType.DONE
                                        updateWebEpisode(subject.id,episode.episodeId,watched)
                                        selected=subject.copy(animeCollection=subject.animeCollection?.copy(episodes=episodes.map {
                                            if(it.episodeId==episode.episodeId) it.copy(collectionType=if(watched) AniEpisodeCollectionType.DONE else null) else it }))
                                        onRefresh()
                                    } catch(e: CancellationException) { throw e }
                                    catch(e: Exception) { saveError=e.message ?: "更新剧集失败" }
                                    finally { saving=false }
                                } })
                        }
                    }
                }
            })
    }
    playback?.let { (subject,episode) -> WebPlaybackSelector(subject,episode,{playback=null},onDownloads,onSources,
        subject.animeCollection?.episodes.orEmpty().map { it.webEpisode().playbackEpisode() }) }
}
