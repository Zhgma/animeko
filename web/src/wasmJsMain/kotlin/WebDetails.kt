import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.episode.areEpisodeDatesCompleted
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.toLocalDateOrNull
import kotlinx.serialization.json.*
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.foundation.widgets.BackNavigationIconButton
import me.him188.ani.app.ui.subject.AiringLabel
import me.him188.ani.app.ui.subject.renderSubjectSeason
import me.him188.ani.app.ui.subject.rememberSubjectStatusStrings
import me.him188.ani.app.ui.subject.details.sections.PagedEpisodesGrid
import me.him188.ani.app.ui.subject.details.sections.EpisodesRow
import me.him188.ani.app.ui.subject.episode.list.EpisodeListItem
import me.him188.ani.client.models.AniEpisodeType
import me.him188.ani.client.models.AniEpisodeCollectionType
import me.him188.ani.app.ui.subject.details.components.SubjectDetailsHeaderCompactContent
import me.him188.ani.app.ui.subject.details.layout.MultiColumnScaffold
import me.him188.ani.app.ui.subject.details.layout.RailCard
import me.him188.ani.app.ui.subject.details.layout.SubjectDetailsLayoutParams
import me.him188.ani.app.ui.subject.details.sections.SubjectCollectionStatsValues
import me.him188.ani.app.ui.subject.details.sections.SubjectRatingSummaryValues
import me.him188.ani.app.ui.subject.details.components.RatingHistogramValues
import me.him188.ani.app.ui.subject.details.sections.SubjectInfoTable
import me.him188.ani.app.ui.subject.details.sections.SubjectSummarySection
import me.him188.ani.app.ui.subject.collection.components.SubjectCollectionTypeButton
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.app.ui.subject.details.components.SelectEpisodeButtonsContent
import me.him188.ani.app.ui.subject.collection.progress.SubjectProgressButtonContent
import me.him188.ani.app.ui.subject.episode.list.EpisodeListDialogContent

private fun JsonObject.int(key: String) = this[key]?.jsonPrimitive?.intOrNull ?: 0

@Composable
fun WebDetails(id: Int, session: WebResult, onBack: () -> Unit, onOpen: (Int) -> Unit, onLogin: () -> Unit, onChanged: () -> Unit, onDownloads: () -> Unit, onSources: () -> Unit) {
    var subject by remember(id) { mutableStateOf<Subject?>(null) }
    var related by remember(id) { mutableStateOf<List<Subject>>(emptyList()) }
    var library by remember(id, session.userId) { mutableStateOf<JsonObject?>(null) }
    var episodes by remember(id) { mutableStateOf<JsonArray>(JsonArray(emptyList())) }
    var loading by remember(id) { mutableStateOf(true) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    var relatedError by remember(id) { mutableStateOf<String?>(null) }
    var libraryError by remember(id, session.userId) { mutableStateOf<String?>(null) }
    var episodeError by remember(id) { mutableStateOf<String?>(null) }
    var episodeOffset by rememberSaveable(id) { mutableIntStateOf(0) }
    var episodeTotal by remember(id) { mutableIntStateOf(0) }
    var episodeLoading by remember(id) { mutableStateOf(false) }
    var expandedRelated by rememberSaveable(id) { mutableStateOf(false) }
    var showComments by rememberSaveable(id) { mutableStateOf(false) }
    var showEpisodeList by rememberSaveable(id) { mutableStateOf(false) }
    var playback by remember(id) { mutableStateOf<WebPlaybackEpisode?>(null) }
    var retry by remember(id) { mutableIntStateOf(0) }
    var libraryRevision by remember(id) { mutableIntStateOf(0) }
    var cached by remember(id) { mutableStateOf(false) }
    var edit by remember(id) { mutableStateOf(false) }
    var resources by remember(id) { mutableStateOf(false) }
    var mangaBtResources by remember(id) { mutableStateOf(false) }
    var selectedEpisode by remember(id) { mutableStateOf("") }
    var libraryLoading by remember(id) { mutableStateOf(false) }
    var collectionSaving by remember(id) { mutableStateOf(false) }
    var collectionSaveError by remember(id) { mutableStateOf<String?>(null) }
    val collectionScope = rememberCoroutineScope()
    val payload = remember(id) { buildJsonObject { put("id", id) }.toString() }
    LaunchedEffect(id, retry) {
        loading = true; error = null
        try { val result = request("detail", payload); subject = result.subject; cached = result.cached }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message }
        finally { loading = false }
    }
    LaunchedEffect(id, retry) {
        relatedError = null
        try { related = request("related", payload).items }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { relatedError = e.message }
    }
    LaunchedEffect(id, subject?.type, session.userId, session.hasBangumi, session.signedIn, libraryRevision) {
        library = null; libraryError = null
        if (subject == null || !(if (subject?.type == 1) session.hasBangumi else session.signedIn)) return@LaunchedEffect
        libraryLoading = true
        try {
            val result = request("libraryRead", buildJsonObject { put("id", id); put("type", subject!!.type) }.toString())
            library = webJson.parseToJsonElement(result.raw) as? JsonObject
            result.subject?.let { stats -> subject = subject?.copy(score = stats.score, rank = stats.rank, votes = stats.votes, histogram = stats.histogram, collection = stats.collection) }
        }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { libraryError = e.message }
        finally { libraryLoading = false }
    }
    LaunchedEffect(subject?.id, retry, session.userId, libraryRevision) {
        if (subject?.type != 2) return@LaunchedEffect
        episodeError = null; episodeLoading = true; episodes = JsonArray(emptyList())
        try {
            val catalog = animekoEpisodeSubject(id)
            subject = subject?.copy(animeCollection = catalog)
            episodes = JsonArray(catalog.episodes.map { it.webEpisode() })
            episodeTotal = episodes.size
            val main = episodes.map { it.jsonObject }.filter { it.int("type") == 0 }
            if (episodeOffset == 0 && episodes.size >= episodeTotal && main.isNotEmpty() && main.size >= (subject?.count ?: 0)) {
                val dates = episodes.map { PackedDate.parseFromDate(it.jsonObject["airdate"]?.jsonPrimitive?.content.orEmpty()) }
                val known = dates.all { it.toLocalDateOrNull() != null }
                subject = subject?.copy(finished = if (known) areEpisodeDatesCompleted(dates.asSequence()) else null)
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { episodeError = e.message }
        finally { episodeLoading = false }
    }
    val data = subject
    if (data == null) {
        Scaffold(topBar = { TopAppBar(title = { Text("作品详情") }, navigationIcon = { BackNavigationIconButton(onBack) }) }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (loading) CircularProgressIndicator() else WebError(error ?: "作品不存在", { retry++ })
            }
        }
        return
    }
    val original = LocalWebOriginalTitle.current
    val book = data.type == 1
    val canCollect = if (book) session.hasBangumi else session.signedIn
    val title = data.displayTitle(original)
    val episodeTime = rememberWebEpisodeTime()
    val status = remember(data, episodeTime) { webSubjectStatus(data, episodeTime) }
    val statusStrings = rememberSubjectStatusStrings()
    val episodeItems = data.animeCollection?.episodes.orEmpty().filter { it.type == AniEpisodeType.MAIN }.map { value ->
        val ep = value.webEpisode().playbackEpisode()
        EpisodeListItem(ep.id, ep.sort, ep.ep, value.name, value.nameCn,
            if(value.collectionType == AniEpisodeCollectionType.DONE) UnifiedCollectionType.DONE else UnifiedCollectionType.NOT_COLLECTED,
            webEpisodeBroadcast(value.airdate, data.animeCollection?.airingInfo?.recurrence, episodeTime), value.imageMedium, value.imageLarge)
    }.sortedBy { it.sort }
    fun selectEpisode(item: EpisodeListItem) { playback = episodes.firstOrNull { it.jsonObject.int("id") == item.episodeId }?.jsonObject?.playbackEpisode() }
    fun toggleEpisode(item: EpisodeListItem) { collectionScope.launch {
        try { updateWebEpisode(id, item.episodeId.toLong(), !item.isDoneOrDropped); libraryRevision++; onChanged() }
        catch(e: CancellationException) { throw e }
        catch(e: Exception) { episodeError = e.message }
    } }
    val metadata: @Composable () -> Unit = {
        if(book) Text(data.platform.ifBlank { "书籍" }) else FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(renderSubjectSeason(PackedDate.parseFromDate(data.date)))
            Text("·")
            AiringLabel(status.label, style=MaterialTheme.typography.bodyMedium, progressColor=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    val navigation: @Composable () -> Unit = { BackNavigationIconButton(onBack) }
    val external = { aniOpen("https://bgm.tv/subject/$id") }
    val cover: @Composable (Modifier) -> Unit = { modifier -> AsyncImage(data.cover, title, modifier.clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop) }
    val rating: @Composable () -> Unit = {
        SubjectRatingSummaryValues(data.score.ifBlank { "0.0" }, data.rank, data.votes)
    }
    val collectionActions: @Composable () -> Unit = {
        Column {
            if (!book && canCollect) SubjectCollectionTypeButton(
                type = listOf(UnifiedCollectionType.NOT_COLLECTED, UnifiedCollectionType.WISH, UnifiedCollectionType.DONE,
                    UnifiedCollectionType.DOING, UnifiedCollectionType.ON_HOLD, UnifiedCollectionType.DROPPED)[library?.int("type") ?: 0],
                onEdit = { selected -> collectionScope.launch {
                    collectionSaving = true; collectionSaveError = null
                    try {
                        val number = when (selected) {
                            UnifiedCollectionType.WISH -> 1; UnifiedCollectionType.DONE -> 2; UnifiedCollectionType.DOING -> 3
                            UnifiedCollectionType.ON_HOLD -> 4; UnifiedCollectionType.DROPPED -> 5; UnifiedCollectionType.NOT_COLLECTED -> 0
                        }
                        request("libraryWrite", buildJsonObject { put("id", id); put("type", 2); put("fields", buildJsonObject { put("type", number) }) }.toString())
                        libraryRevision++; onChanged()
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { collectionSaveError = e.message; libraryRevision++ }
                    finally { collectionSaving = false }
                } },
                modifier = Modifier.fillMaxWidth(),
                enabled = !libraryLoading && !collectionSaving && libraryError == null,
                showDelete = library != null,
            ) else Button({ if (canCollect) edit = true else onLogin() }, enabled = !libraryLoading && libraryError == null) {
                Text(if (!canCollect) "登录后收藏" else library?.int("type")?.let { collectionLabel(it, book) } ?: "加入收藏")
            }
            if (!book && library != null) TextButton({ edit = true }, enabled = !collectionSaving && !libraryLoading) { Text("编辑进度与评价") }
            if (collectionSaving) LinearProgressIndicator(Modifier.fillMaxWidth())
            collectionSaveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            library?.let { Text("${if (book) "已读" else "已看"} ${it.int("ep_status")} / ${data.count.takeIf { n -> n > 0 } ?: "—"} 话", style = MaterialTheme.typography.bodySmall) }
        }
    }
    val statistics: @Composable () -> Unit = {
        SubjectCollectionStatsValues(data.collection.values.sum(), data.collection["doing"] ?: 0, data.collection["wish"] ?: 0,
            doingLabel = if (book) "在读" else "在看", wishLabel = if (book) "想读" else "想看")
    }
    val information: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("作品信息", style = MaterialTheme.typography.titleMedium)
            SubjectInfoTable(data.info.map { it.label to it.value })
        }
    }
    val tags: @Composable () -> Unit = {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            data.tags.forEach { Surface(shape = MaterialTheme.shapes.small, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                Text(it, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium)
            } }
        }
    }
    val playControls: @Composable () -> Unit = {
        if (!book) {
            val next = episodes.firstOrNull { it.jsonObject.int("id") == status.progress.episodeIdToPlay }?.jsonObject
            SubjectProgressButtonContent(if(episodeLoading) "加载剧集" else status.progress.buttonText(statusStrings, episodeTime),
                status.progress.buttonIsPrimary, { next?.let { playback=it.playbackEpisode() } }, Modifier.fillMaxWidth(), enabled=next!=null && !episodeLoading)
        }
    }
    val body: @Composable (Boolean) -> Unit = { compactLayout ->
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            if (cached) Text("当前显示离线缓存，可能不是最新数据。", color = MaterialTheme.colorScheme.tertiary)
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { WebError(it, { retry++ }) }
            libraryError?.let { WebError("收藏加载失败：$it", { libraryRevision++ }) }
            if(book) TextButton({ showComments = true }) { Text("查看评论") }
            SubjectSummarySection(data.summary.ifBlank { "暂无简介" })
            if (book) {
                Text("章节与阅读", style = MaterialTheme.typography.titleLarge)
                Text("选择漫画数据源，可搜索并记住所选作品。网页正文阅读器尚未接入，可在来源网站阅读；漫画种子也可下载后用本机阅读器打开。")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ selectedEpisode = ""; mangaBtResources = false; resources = true }) { Text("数据源") }
                    OutlinedButton({ selectedEpisode = ""; mangaBtResources = true; resources = true }) { Text("查找漫画种子") }
                    OutlinedButton(onDownloads) { Text("查看下载") }
                }
            } else {
                episodeError?.let { WebError(it, { retry++ }) }
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if(compactLayout) Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        Text("选集",style=MaterialTheme.typography.titleMedium)
                        EpisodesRow(episodeItems, playback?.id, ::selectEpisode, ::toggleEpisode, contentPadding=PaddingValues(0.dp))
                    } else PagedEpisodesGrid(episodeItems, playback?.id, ::selectEpisode, ::toggleEpisode,
                        header = { pager -> Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                            Text("选集",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                            pager?.invoke() ?: AiringLabel(status.label, style=MaterialTheme.typography.bodySmall)
                        } })
                }
            }
            if(!book) TextButton({ showComments = true }) { Text("查看评论") }
            Text("关联作品", style = MaterialTheme.typography.titleLarge)
            relatedError?.let { WebError(it, { retry++ }) }
            if (related.isEmpty() && relatedError == null) Text("暂无关联作品")
            (if (expandedRelated) related else related.take(6)).forEach { WebSubjectItem(it, onOpen) }
            if (related.size > 6) TextButton({ expandedRelated = !expandedRelated }) { Text(if (expandedRelated) "收起关联作品" else "查看全部 ${related.size} 个关联作品") }

        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        playback?.let { episode -> WebPlaybackSelector(data,episode,{playback=null},onDownloads,onSources,episodes.map { it.jsonObject.playbackEpisode() }) }
        val sidePanel = resources && maxWidth >= 600.dp && maxHeight >= 480.dp
        val panelWidth = (maxWidth * 0.4f).coerceIn(300.dp, 400.dp)
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
        val layout = SubjectDetailsLayoutParams.calculate(maxWidth)
        if (layout.isMultiColumn) {
            MultiColumnScaffold(layout, navigationIcon = navigation, topBarTitle = title, onClickOpenExternal = external) {
                Column(Modifier.width(layout.sidebarWidth), verticalArrangement = Arrangement.spacedBy(layout.sidebarItemSpacing)) {
                    cover(Modifier.fillMaxWidth().aspectRatio(849f / 1200f)); playControls(); collectionActions(); statistics(); HorizontalDivider(); information(); tags()
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(layout.sectionSpacing)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(title, style = MaterialTheme.typography.headlineSmall)
                        Text(data.original, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        metadata()
                    }
                    if (!layout.showRail) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        rating()
                        if (layout.showInlineRatingHistogram) { Spacer(Modifier.weight(1f)); Box(Modifier.width(274.dp)) { WebHistogram(data) } }
                    }
                    body(false)
                }
                if (layout.showRail) Column(Modifier.width(layout.railWidth)) { RailCard { rating(); WebHistogram(data) } }
            }
        } else {
            Scaffold(topBar = { TopAppBar(title = { Text("作品详情") }, navigationIcon = navigation,
                actions = { IconButton(external) { Icon(Icons.AutoMirrored.Outlined.OpenInNew, "在 Bangumi 查看") } }) }) { padding ->
                LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(layout.contentHorizontalPadding), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    item { SubjectDetailsHeaderCompactContent(
                        title = { Text(title) }, subtitle = { Text(data.original) },
                        seasonTags = metadata,
                        collectionData = statistics,
                        collectionAction = { Box(Modifier.widthIn(max = 160.dp)) { collectionActions() } },
                        selectEpisodeButton = { playControls() }, rating = rating,
                        cover = { cover(Modifier.width(140.dp).height(140.dp / (849f / 1200f))) }) }
                    item { body(true) }; item { tags() }; item { information() }; item { WebHistogram(data) }
                }
            }
        }
    }
            }
            if (sidePanel && book && !mangaBtResources) WebMangaSources(data, { resources = false }, onSources,
                Modifier.width(panelWidth).fillMaxHeight(), embedded = true)
            else if (sidePanel) WebSubjectMedia(data, selectedEpisode, { resources = false }, onDownloads, onSources,
                Modifier.width(panelWidth).fillMaxHeight(), embedded = true)
        }
        if (resources && !sidePanel && book && !mangaBtResources) WebMangaSources(data, { resources = false }, onSources,
            useDialog = maxHeight < 480.dp)
        else if (resources && !sidePanel) WebSubjectMedia(data, selectedEpisode, { resources = false }, onDownloads, onSources,
            useDialog = maxHeight < 480.dp)
    }
    if (showEpisodeList) {
    val episodeTime = rememberWebEpisodeTime()
    EpisodeListDialogContent(
        subjectTitle=data.title, onDismissRequest={showEpisodeList=false},
        onCacheClick={showEpisodeList=false;onDownloads()}, episodes={
            if(episodeLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            episodeError?.let { WebError(it,{retry++}) }
            val groups=episodes.groupBy { it.jsonObject.int("type") == 0 }
            listOf(true,false).forEach { main ->
                if(!main && groups[false]?.isNotEmpty()==true) HorizontalDivider(Modifier.padding(vertical=16.dp))
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    groups[main].orEmpty().forEach { value -> val episode=value.jsonObject.playbackEpisode()
                        WebEpisodeStateButton(data,value.jsonObject,episodeTime,
                            {showEpisodeList=false;playback=episode},{},Modifier)
                    }
                }
            }
        })
    }
    if (showComments) ModalBottomSheet(onDismissRequest = { showComments = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        WebComments(id, subject?.type ?: 2) { showComments = false; if (canCollect) edit = true else onLogin() }
    }
    if (edit) WebLibraryEditor(data, library, onDismiss = { edit = false }, onSaved = { edit = false; libraryRevision++; onChanged() })

}

fun collectionLabel(type: Int, book: Boolean) = when (type) {
    1 -> if (book) "想读" else "想看"; 2 -> if (book) "读过" else "看过"; 3 -> if (book) "在读" else "在看"; 4 -> "搁置"; 5 -> "抛弃"; else -> "未收藏"
}

@Composable
private fun WebHistogram(subject: Subject) {
    RatingHistogramValues((1..10).map { subject.histogram["$it"] ?: 0 }, Modifier.fillMaxWidth().padding(vertical = 16.dp))
}

@Composable
private fun WebLibraryEditor(subject: Subject, current: JsonObject?, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var type by remember { mutableIntStateOf(current?.int("type") ?: 3) }
    var progress by remember { mutableStateOf((current?.int("ep_status") ?: 0).toString()) }
    var volumes by remember { mutableStateOf((current?.int("vol_status") ?: 0).toString()) }
    var score by remember { mutableStateOf((current?.int("rate") ?: 0).toString()) }
    var comment by remember { mutableStateOf(current?.get("comment")?.jsonPrimitive?.contentOrNull.orEmpty()) }
    var private by remember { mutableStateOf(current?.get("private")?.jsonPrimitive?.booleanOrNull ?: false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text(if (current == null) "加入收藏" else "编辑收藏与评价") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(1, 3, 2, 4, 5).forEach { value ->
                    FilterChip(type == value, { type = value }, enabled = !saving, label = { Text(collectionLabel(value, subject.type == 1)) })
                } }
                if (current != null) {
                    OutlinedTextField(progress, { progress = it }, label = { Text(if (subject.type == 1) "已读话数" else "已看话数") }, singleLine = true, enabled = !saving, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    if (subject.type == 1) OutlinedTextField(volumes, { volumes = it }, label = { Text("已读卷数") }, singleLine = true, enabled = !saving, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(score, { score = it }, label = { Text("评分（0–10，0 为未评分）") }, singleLine = true, enabled = !saving, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(comment, { comment = it }, label = { Text("短评") }, enabled = !saving, minLines = 3)
                    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(private, { private = it }, enabled = !saving); Text("私密收藏") }
                } else Text("加入收藏后可以编辑进度、评分和短评。")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(enabled = !saving, onClick = {
            val ep = progress.toIntOrNull(); val vol = volumes.toIntOrNull(); val rate = score.toIntOrNull()
            if (current != null && (ep == null || ep !in 0..1000000 || vol == null || vol !in 0..1000000 || rate == null || rate !in 0..10 || comment.length > 10000)) {
                error = "请检查进度、评分范围及短评长度。"
            } else scope.launch {
                saving = true; error = null
                try {
                    // PATCH includes only changed fields, preserving unrelated collection metadata.
                    val fields = buildJsonObject {
                        if (current == null || current.int("type") != type) put("type", type)
                        if (current != null) {
                            if (current.int("ep_status") != ep) put("ep_status", ep!!)
                            if (subject.type == 1 && current.int("vol_status") != vol) put("vol_status", vol!!)
                            if (current.int("rate") != rate) put("rate", rate!!)
                            if (current["comment"]?.jsonPrimitive?.contentOrNull.orEmpty() != comment) put("comment", comment)
                            if ((current["private"]?.jsonPrimitive?.booleanOrNull ?: false) != private) put("private", private)
                        }
                    }
                    if (fields.isNotEmpty()) request("libraryWrite", buildJsonObject { put("id", subject.id); put("type", subject.type); put("create", current == null); put("fields", fields) }.toString())
                    onSaved()
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { error = e.message }
                finally { saving = false }
            }
        }) { Text("保存") } },
        dismissButton = { TextButton(onDismiss, enabled = !saving) { Text("取消") } })
}
