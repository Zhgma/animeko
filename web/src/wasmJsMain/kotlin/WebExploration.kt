// SPDX-License-Identifier: AGPL-3.0-only
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.platform.LocalDensity
import me.him188.ani.app.ui.adaptive.HorizontalScrollControlScaffoldOnDesktop
import me.him188.ani.app.ui.foundation.HorizontalScrollControlState
import me.him188.ani.app.ui.foundation.rememberHorizontalScrollControlState
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.*
import androidx.compose.runtime.key as composeKey
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.datetime.LocalDate
import me.him188.ani.app.ui.exploration.schedule.*
import me.him188.ani.app.ui.subject.rememberSubjectStatusStrings
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant
import me.him188.ani.app.ui.adaptive.AniTopAppBar
import me.him188.ani.app.ui.adaptive.AniTopAppBarDefaults
import me.him188.ani.app.ui.adaptive.NavTitleHeader
import me.him188.ani.app.ui.exploration.trends.TrendingCarouselEntry
import me.him188.ani.app.ui.exploration.trends.TrendingSubjectsCarouselContent
import me.him188.ani.app.ui.exploration.followed.FollowedSubjectItemContent
import me.him188.ani.app.ui.exploration.followed.FollowedSubjectsDefaults
import me.him188.ani.app.ui.exploration.schedule.ScheduleItem
import me.him188.ani.app.ui.exploration.schedule.ScheduleItemDefaults
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.foundation.avatar.AvatarImage
import me.him188.ani.app.ui.foundation.session.SelfAvatarContent
import me.him188.ani.app.ui.foundation.layout.*
import me.him188.ani.app.ui.foundation.layout.plus as addPadding
import me.him188.ani.app.ui.subject.SubjectGridDefaults
import me.him188.ani.app.ui.subject.SubjectCoverCardContent
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.app.ui.lang.*
import me.him188.ani.client.models.AniAiringScheduleForDate
import me.him188.ani.datasources.api.EpisodeSort
import org.jetbrains.compose.resources.stringResource

private data class ExploreLoad(val data: WebResult = WebResult(), val loading: Boolean = true, val error: String? = null)

@Composable
private fun exploreLoad(key: Any?, retry: Int, block: suspend () -> WebResult): ExploreLoad {
    return composeKey(key, retry) { produceState(ExploreLoad(), key, retry) {
        value = ExploreLoad()
        value = try { ExploreLoad(block(), loading = false) }
        catch (e: CancellationException) { throw e }
        catch (e: Throwable) { ExploreLoad(loading = false, error = e.message ?: "加载失败") }
    }.value }
}

@Composable
fun WebBrowse(session: WebResult, onOpen: (Int) -> Unit, onAccount: () -> Unit, onSearch: () -> Unit, onSettings: () -> Unit, onDownloads: () -> Unit, onSources: () -> Unit, onSchedule: () -> Unit, onTrending: () -> Unit) {
    var playback by remember { mutableStateOf<Pair<Subject, WebPlaybackEpisode>?>(null) }
    val now = rememberWebEpisodeTime()
    val strings = rememberSubjectStatusStrings()
    var retryTrends by remember { mutableIntStateOf(0) }
    var retryFollowed by remember { mutableIntStateOf(0) }
    var retryRecommendations by remember { mutableIntStateOf(0) }
    val trends = exploreLoad(Unit, retryTrends) { animekoTrending(0) }
    val followed = exploreLoad(session.userId to session.signedIn, retryFollowed) {
        if (!session.signedIn) WebResult() else {
            val values = mutableListOf<Subject>()
            do {
                val next = animekoCollectionRequest("collection", buildJsonObject { put("status", 3); put("offset", values.size) })
                values += next.items
            } while (next.items.isNotEmpty() && values.size < minOf(64, next.total))
            WebResult(items = values.take(64))
        }
    }
    var offset by remember(session.userId) { mutableIntStateOf(0) }
    var recommendations by remember(session.userId) { mutableStateOf<List<Subject>>(emptyList()) }
    val recommendedPage = exploreLoad(session.userId to offset, retryRecommendations) {
        if (session.signedIn) animekoRecommendations(offset) else WebResult()
    }
    LaunchedEffect(recommendedPage) {
        if (!recommendedPage.loading && recommendedPage.error == null)
            recommendations = (if (offset == 0) recommendedPage.data.items else recommendations + recommendedPage.data.items).distinctBy { it.id }
    }
    val window = currentWindowAdaptiveInfo1().windowSizeClass
    val gridParams = SubjectGridDefaults.coverLayoutParameters()
    val followedParams = FollowedSubjectsDefaults.layoutParameters()
    val originalTitle = LocalWebOriginalTitle.current
    val hideNsfw = LocalWebUISettings.current.searchSettings.nsfwMode == NsfwMode.HIDE
    val scroll = if (window.isHeightAtLeastMedium) TopAppBarDefaults.pinnedScrollBehavior() else TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest, topBar = {
        AniTopAppBar(title = { AniTopAppBarDefaults.Title(stringResource(Lang.exploration_title)) },
            actions = { if (!session.signedIn || window.isWidthAtLeastMedium) IconButton(onSettings) { Icon(Icons.Rounded.Settings, "设置") } },
            avatar = { size -> SelfAvatarContent(false, session.signedIn, size, onAccount) { AvatarImage(session.avatar.takeIf { it.isNotBlank() }, it) } },
            searchIconButton = { IconButton(onSearch) { Icon(Icons.Rounded.Search, "搜索") } },
            searchBar = { IconButton(onSearch) { Icon(Icons.Rounded.Search, "搜索") } }, scrollBehavior = scroll)
    }) { padding ->
        LazyVerticalGrid(gridParams.gridCells,
            Modifier.fillMaxSize().wrapContentWidth().widthIn(max = 1300.dp).nestedScroll(scroll.nestedScrollConnection),
            contentPadding = padding.addPadding(PaddingValues(horizontal = window.paneHorizontalPadding)),
            horizontalArrangement = gridParams.horizontalArrangement, verticalArrangement = gridParams.verticalArrangement) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    NavTitleHeader(title = { Text(stringResource(Lang.exploration_trending), softWrap = false) }, trailingActions = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onSchedule, Modifier.weight(1f, fill = false), contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                                Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(ButtonDefaults.IconSize))
                                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                Text(stringResource(Lang.exploration_schedule), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            TextButton(onTrending) {
                                Text(stringResource(Lang.exploration_trending_more), softWrap = false)
                                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(ButtonDefaults.IconSize))
                            }
                        }
                    })
                    if (trends.error != null) WebError(trends.error) { retryTrends++ }
                    else if (trends.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
                    else {
                        val values = trends.data.items.filter { !hideNsfw || !it.nsfw }.take(10)
                        val carousel = rememberCarouselState { values.size }
                        WebExploreHorizontalScroll(carousel) {
                        TrendingSubjectsCarouselContent(values.size,
                            { index -> values.getOrNull(index)?.let { TrendingCarouselEntry(it.id, it.displayTitle(originalTitle), it.cover) } },
                            { onOpen(it.id) }, contentPadding = PaddingValues(vertical = 8.dp), carouselState = carousel,
                            autoAdvance = { enabled ->
                                LaunchedEffect(carousel, enabled, values.size) {
                                    while (enabled && isActive) {
                                        delay(3000)
                                        if (browserDocumentVisible() && !carousel.isScrollInProgress && values.size > 1) {
                                            @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
                                            val next = (carousel.pagerState.currentPage + 1) % values.size
                                            carousel.animateScrollToItem(next)
                                        }
                                    }
                                }
                            })
                        }
                    }
                    NavTitleHeader(title = { Text(stringResource(Lang.exploration_continue_watching), softWrap = false) })
                    when {
                        !session.signedIn -> TextButton(onAccount) { Text("登录后查看继续观看") }
                        followed.error != null -> WebError(followed.error) { retryFollowed++ }
                        followed.loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                        followed.data.items.isEmpty() -> Text("将番剧收藏为“在看”后，就会显示在这里", Modifier.padding(vertical = 16.dp))
                        else -> {
                            val list = rememberLazyListState()
                            WebExploreHorizontalScroll(list) {
                            LazyRow(state = list, contentPadding = PaddingValues(vertical = 8.dp), horizontalArrangement = followedParams.horizontalArrangement) {
                            items(followed.data.items.filter { !hideNsfw || !it.nsfw }.sortedByDescending { webSubjectStatus(it, now).progress.buttonIsPrimary }, key = { it.id }) { item ->
                                WebNsfw(item) {
                                    val progress = collectionProgress(item, now, strings)
                                    FollowedSubjectItemContent(item.displayTitle(originalTitle), item.cover, progress.primary,
                                        { onOpen(item.id) }, { progress.episode?.let { playback = item to it.webEpisode().playbackEpisode() } }, followedParams.imageSize, followedParams.shape,
                                        supportingText = { Text(progress.button, maxLines = 1, overflow = TextOverflow.Ellipsis) })
                                }
                            }
                        }
                            }
                        }
                    }
                    NavTitleHeader(title = { Text(stringResource(Lang.exploration_recommendations), softWrap = false) })
                    if (!session.signedIn) TextButton(onAccount) { Text("登录后查看推荐") }
                }
            }
            items(recommendations, key = { "recommend-${it.id}" }) { item ->
                SubjectCoverCardContent(item.displayTitle(originalTitle), item.cover, false, { onOpen(item.id) },
                    shape = gridParams.cardShape, imageModifier = Modifier.sizeIn(maxWidth = 300.dp, maxHeight = (300f / 9 * 16).dp))
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                when {
                    recommendedPage.error != null -> WebError(recommendedPage.error) { retryRecommendations++ }
                    recommendedPage.loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    session.signedIn && recommendations.isEmpty() -> Text("暂无推荐", Modifier.padding(16.dp))
                    recommendedPage.data.items.isNotEmpty() && offset + 30 < recommendedPage.data.total -> {
                        LaunchedEffect(offset, recommendedPage) { offset += 30 }
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
    playback?.let { (subject, episode) ->
        WebPlaybackSelector(subject, episode, { playback = null }, onDownloads, onSources,
            subject.animeCollection?.episodes.orEmpty().map { it.webEpisode().playbackEpisode() })
    }
}

@Composable
fun WebTrendingRanking(onBack: () -> Unit, onOpen: (Int) -> Unit) {
    var offset by rememberSaveable { mutableIntStateOf(0) }
    var retry by remember { mutableIntStateOf(0) }
    var entries by remember { mutableStateOf<List<Subject>>(emptyList()) }
    val data = exploreLoad(offset, retry) { animekoTrending(offset) }
    LaunchedEffect(data) { if (!data.loading && data.error == null) entries = (if (offset == 0) data.data.items else entries + data.data.items).distinctBy { it.id } }
    val window = currentWindowAdaptiveInfo1().windowSizeClass
    Scaffold(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest, topBar = { AniTopAppBar(title = { Text(stringResource(Lang.exploration_trending_ranking)) }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } }) }) { padding ->
        LazyVerticalGrid(GridCells.Adaptive(360.dp), Modifier.fillMaxSize().wrapContentWidth().widthIn(max = 1300.dp),
            contentPadding = padding.addPadding(PaddingValues(horizontal = window.paneHorizontalPadding)),
            horizontalArrangement = Arrangement.spacedBy(window.paneHorizontalPadding)) {
            items(entries, key = { it.id }) { item -> WebNsfw(item) { WebSubjectItem(item, onOpen, Modifier.padding(vertical = window.paneVerticalPadding / 2)) } }
            item(span = { GridItemSpan(maxLineSpan) }) {
                when {
                    data.error != null -> WebError(data.error) { retry++ }
                    data.loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    data.data.items.isNotEmpty() && offset + 24 < data.data.total -> {
                        LaunchedEffect(offset, data) { offset += 24 }
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    entries.isEmpty() -> Text(stringResource(Lang.exploration_trending_ranking_empty), Modifier.padding(32.dp))
                }
            }
        }
    }
}

@Composable
fun WebExploreSchedule(onBack: () -> Unit, onOpen: (Int) -> Unit) {
    var records by remember { mutableStateOf<List<AniAiringScheduleForDate>?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    val now = rememberWebEpisodeTime()
    val zone = TimeZone.currentSystemDefault()
    val current = now.toLocalDateTime(zone)
    val days = remember(current.date) { ScheduleDay.generateForRecentTwoWeeks(current.date) }
    val state = remember(current.date) { ScheduleScreenState { days } }
    LaunchedEffect(retry, current.date, zone) {
        error = null
        try { records = animekoSchedule().list }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "加载失败" }
    }
    val schedules = remember(records, current.date, current.hour, current.minute, zone) {
        records?.let { webScheduleColumns(it, now, zone) }
    }
    val layout = ScheduleScreenLayoutParams.calculate()
    Scaffold(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest, topBar = { AniTopAppBar(title = { Text(stringResource(Lang.exploration_schedule)) }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            error?.let { WebError(it) { retry++ } }
            ScheduleScreenContent(state, Modifier.weight(1f), layoutParams = layout) { day ->
                val items = schedules?.firstOrNull { it.date == day.date }?.episodes
                    ?: if (records == null && error == null) (1..10).map { AiringScheduleColumnItem.PlaceholderData(it, true) } else emptyList()
                ScheduleDayColumn(items, { onOpen(it.subjectId) },
                    dayOfWeek = { if (layout.showDayOfWeekHeadline) DayOfWeekHeadline(day) },
                    layoutParams = layout.columnLayoutParams,
                    state = state.scheduleColumnLazyListStates.getValue(day),
                    itemColors = ScheduleScreenDefaults.colors().itemColors)
            }
        }
    }
}

/** The App's grouping, ordering, unknown-time handling and time indicator run on API records. */
internal fun webScheduleColumns(records: List<AniAiringScheduleForDate>, now: Instant, zone: TimeZone): List<AiringSchedule> {
    val current = now.toLocalDateTime(zone)
    return records.map { day ->
        val date = LocalDate.parse(day.date)
        val items = day.list.map { item ->
            AiringScheduleItemPresentation(
                subjectId = item.subject.subjectId.toInt(),
                subjectTitle = item.subject.nameCn.ifBlank { item.subject.name }, subjectOriginalTitle = item.subject.name,
                imageUrl = item.subject.imageLarge, episodeId = item.episode.episodeId.toInt(),
                episodeSort = EpisodeSort(item.episode.sort), episodeEp = item.episode.ep?.let(::EpisodeSort),
                episodeName = item.episode.nameCn.ifBlank { item.episode.name }, episodeOriginalName = item.episode.name,
                subjectCollectionType = UnifiedCollectionType.NOT_COLLECTED, dayOfWeek = date.dayOfWeek,
                time = if (item.timeKnown == false) null else runCatching { Instant.parse(item.airingTime).toLocalDateTime(zone).time }.getOrNull())
        }
        AiringSchedule(date, SchedulePageDataHelper.toColumnItems(items, date == current.date, current.time))
    }
}

@Composable
private fun WebExploreHorizontalScroll(state: ScrollableState, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    HorizontalScrollControlScaffoldOnDesktop(rememberHorizontalScrollControlState(state) { direction ->
        scope.launch { state.animateScrollBy(with(density) { 320.dp.toPx() } * if (direction == HorizontalScrollControlState.Direction.BACKWARD) -1 else 1) }
    }, content = content)
}
