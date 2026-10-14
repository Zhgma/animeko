@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*
import me.him188.ani.client.apis.EpisodesAniApi
import me.him188.ani.client.apis.SubjectsAniApi
import me.him188.ani.client.apis.UserAniApi
import me.him188.ani.client.apis.UserProfileAniApi
import me.him188.ani.client.apis.HomeAniApi
import me.him188.ani.client.apis.ScheduleAniApi
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import me.him188.ani.client.apis.TrendsAniApi
import me.him188.ani.client.apis.PlaybackHistoryAniApi
import me.him188.ani.app.ui.settings.account.isValidProfileNickname
import me.him188.ani.client.models.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Instant

external fun aniAnimekoBaseUrl(): String
external fun aniAnimekoToken(callback: (String, String) -> Unit)

// Wasm datetime uses a fixed UTC offset; Animeko schedule requires an IANA zone ID.
@JsFun("() => Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'")
external fun browserTimeZoneId(): String

@JsFun("() => document.visibilityState === 'visible'")
external fun browserDocumentVisible(): Boolean

private val animekoHttp by lazy {
    HttpClient(Js) {
        expectSuccess = true
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpTimeout) { requestTimeoutMillis = 25000 }
        HttpResponseValidator {
            handleResponseExceptionWithRequest { error, _ ->
                if (error is ResponseException) {
                    val status = error.response.status.value
                    throw WebRequestException(if (status == 401) "Animeko 会话已过期，请重新登录。" else "Animeko 接口返回 HTTP $status，请重试。", status)
                } else if (error !is CancellationException) {
                    throw WebRequestException("无法连接远程 Animeko API，请检查网络或稍后重试。", 0)
                }
            }
        }
    }
}

private suspend fun animekoToken(required: Boolean = true): String = suspendCancellableCoroutine { continuation ->
        aniAnimekoToken { token, error ->
            if (continuation.isActive) {
                if (error.isNotBlank()) continuation.resumeWithException(Exception(error))
                else if (required && token.isBlank()) continuation.resumeWithException(Exception("请登录 Animeko"))
                else continuation.resume(token)
            }
        }
}

private suspend fun subjectsApi(required: Boolean = true) = SubjectsAniApi(aniAnimekoBaseUrl(), animekoHttp).apply {
    animekoToken(required).takeIf { it.isNotBlank() }?.let(::setBearerToken)
}

private fun AniSubjectSearch.webSubject() = Subject(id = id.toInt(), title = nameCn.ifBlank { name }, original = name,
    cover = imageLarge, summary = summary, date = airDate, score = score.orEmpty(), rank = rank ?: 0, votes = ratingTotal,
    count = mainEpisodeCount, nsfw = nsfw, tags = tags.map { it.name })

suspend fun animekoTrending(offset: Int): WebResult {
    val result = TrendsAniApi(aniAnimekoBaseUrl(), animekoHttp).apply { animekoToken(false).takeIf { it.isNotBlank() }?.let(::setBearerToken) }
        .getTrendingSubjects(offset, 24).body()
    return WebResult(items = result.items.map { it.subject.webSubject().copy(trendingRank = it.trendingRank, heat = it.heat) }, total = result.total.toInt())
}

suspend fun animekoPlaybackHistory(): List<AniPlaybackHistoryUpsertRecord> {
    val result = PlaybackHistoryAniApi(aniAnimekoBaseUrl(), animekoHttp).apply { setBearerToken(animekoToken()) }
        .sync(AniSyncRequest(ops = emptyList())).body()
    val deleted = result.deletes.groupBy { it.episodeId }.mapValues { (_, records) -> records.maxOf { Instant.parse(it.deletedAt) } }
    return result.upserts.filter { record -> deleted[record.episodeId]?.let { it < Instant.parse(record.updatedAt) } ?: true }
        .sortedByDescending { Instant.parse(it.updatedAt) }
}

suspend fun animekoSearch(p: JsonObject, apiOverride: SubjectsAniApi? = null): WebResult {
    val sort = when (p["sort"]?.jsonPrimitive?.content) {
        "rank" -> AniSubjectSearchSortBy.RATING_DESC; "collect" -> AniSubjectSearchSortBy.COLLECTION_DESC
        "date" -> AniSubjectSearchSortBy.AIR_DATE_DESC; else -> AniSubjectSearchSortBy.RELEVANCE
    }
    val result = (apiOverride ?: subjectsApi(false)).searchSubjects(p["keyword"]?.jsonPrimitive?.content.orEmpty(),
        offset = p["offset"]?.jsonPrimitive?.int ?: 0, limit = 24, sortBy = sort,
        includeNsfw = if (p["hideNsfw"]?.jsonPrimitive?.boolean == true) AniNsfwFilter.EXCLUDE else AniNsfwFilter.INCLUDE,
        excludeCollectionTypes = if (p["ignoreDone"]?.jsonPrimitive?.boolean == true) listOf(AniCollectionType.DONE, AniCollectionType.DROPPED) else null,
    ).body()
    return WebResult(items = result.items.map { it.webSubject() }, total = -1, hasMore = result.items.size == 24)
}

suspend fun loadAnimekoProfile(): AniAniSelfUser = UserAniApi(aniAnimekoBaseUrl(), animekoHttp)
    .apply { setBearerToken(animekoToken()) }.getUser().body()

suspend fun refreshAnimekoProfile(session: WebResult): WebResult {
    val profile = loadAnimekoProfile()
    return session.copy(userId = profile.id, nickname = profile.nickname, email = profile.email.orEmpty(),
        username = profile.bangumiUsername.orEmpty(), avatar = profile.mediumAvatar ?: profile.smallAvatar.orEmpty(),
        externalAccounts = profile.externalAccounts.associate { it.provider to it.username.orEmpty() }, profileLoaded = true)
}

suspend fun saveAnimekoNickname(nickname: String) {
    require(isValidProfileNickname(nickname)) { "昵称格式不正确" }
    UserProfileAniApi(aniAnimekoBaseUrl(), animekoHttp).apply { setBearerToken(animekoToken()) }
        .updateProfile(AniUpdateProfileRequest(newNickname = nickname))
}

private fun collectionType(value: Int): AniCollectionType = when (value) {
    1 -> AniCollectionType.WISH
    2 -> AniCollectionType.DONE
    3 -> AniCollectionType.DOING
    4 -> AniCollectionType.ON_HOLD
    5 -> AniCollectionType.DROPPED
    else -> error("收藏状态无效")
}

private fun AniCollectionType?.number(): Int = when (this) {
    AniCollectionType.WISH -> 1; AniCollectionType.DONE -> 2; AniCollectionType.DOING -> 3
    AniCollectionType.ON_HOLD -> 4; AniCollectionType.DROPPED -> 5; null -> 0
}

private fun AniSubjectCollection.mainEpisodes() = episodes.filter { it.type == AniEpisodeType.MAIN }.sortedBy { it.sort.toDoubleOrNull() ?: 0.0 }
private fun AniSubjectCollection.webSubject() = Subject(
    animeCollection = this,
    id = id.toInt(), title = nameCn.ifBlank { name }, original = name, cover = imageLarge.ifBlank { imageThumb },
    score = score.orEmpty(), rank = rank ?: 0, date = airDate, summary = summary, nsfw = nsfw,
    count = mainEpisodes().size, progress = mainEpisodes().count { it.collectionType == AniEpisodeCollectionType.DONE },
    votes = scoreDetails.values.sum(), histogram = scoreDetails,
    collection = mapOf("wish" to favorite.wish, "collect" to favorite.done, "doing" to favorite.doing, "on_hold" to favorite.onHold, "dropped" to favorite.dropped),
)

/** Uses the same generated client and request models as RemoteSubjectService. */
suspend fun animekoCollectionRequest(action: String, p: JsonObject, apiOverride: SubjectsAniApi? = null): WebResult {
    try {
        val api = apiOverride ?: subjectsApi()
        if (action == "collection") {
            val page = api.getSubjectCollections(p["offset"]?.jsonPrimitive?.int ?: 0, 30,
                collectionType(p["status"]?.jsonPrimitive?.int ?: 3)).body()
            return WebResult(items = page.items.map { it.webSubject() }, total = page.total.toInt())
        }
        val id = p.getValue("id").jsonPrimitive.long
        require(id > 0) { "作品编号无效" }
        if (action == "libraryRead") {
            val value = api.getSubject(id).body()
            val raw = if (value.collectionType == null) "null" else buildJsonObject {
                put("type", value.collectionType.number())
                put("ep_status", value.mainEpisodes().count { it.collectionType == AniEpisodeCollectionType.DONE })
                put("rate", value.selfRating.score); put("comment", value.selfRating.comment.orEmpty())
                put("private", value.selfRating.isPrivate)
            }.toString()
            return WebResult(raw = raw, subject = value.webSubject())
        }
        val fields = p.getValue("fields").jsonObject
        require(fields.keys.all { it in setOf("type", "rate", "comment", "private", "ep_status") }) { "收藏参数无效" }
        if (fields["type"]?.jsonPrimitive?.int == 0) {
            api.deleteSubjectCollection(id)
            return WebResult()
        }
        // Fetch current rating before merging, preserving tags and fields not edited in this dialog.
        val ratingChanged = fields.keys.any { it in setOf("rate", "comment", "private") }
        val progressChanged = "ep_status" in fields
        val current = if (ratingChanged || progressChanged) api.getSubject(id).body() else null
        val rating = if (ratingChanged) current!!.selfRating.copy(
            score = fields["rate"]?.jsonPrimitive?.int ?: current.selfRating.score,
            comment = fields["comment"]?.jsonPrimitive?.content ?: current.selfRating.comment,
            isPrivate = fields["private"]?.jsonPrimitive?.boolean ?: current.selfRating.isPrivate,
        ) else null
        val episodes = current?.mainEpisodes().orEmpty()
        val progress = fields["ep_status"]?.jsonPrimitive?.int
        require(progress == null || progress in 0..episodes.size) { "已看话数必须在 0 到 ${episodes.size} 之间" }
        if ("type" in fields || rating != null) api.updateSubjectCollection(id, AniUpdateSubjectCollectionRequest(
            collectionType = fields["type"]?.jsonPrimitive?.int?.let(::collectionType), selfRating = rating,
        ))
        if (progress != null) {
            val done = episodes.take(progress).filter { it.collectionType != AniEpisodeCollectionType.DONE }.map { it.episodeId }
            val undone = episodes.drop(progress).filter { it.collectionType == AniEpisodeCollectionType.DONE }.map { it.episodeId }
            if (done.isNotEmpty()) api.batchUpdateEpisodeCollections(id, AniBatchUpdateEpisodeCollectionsRequest(done, AniEpisodeCollectionTypeUpdate.DONE))
            if (undone.isNotEmpty()) api.batchUpdateEpisodeCollections(id, AniBatchUpdateEpisodeCollectionsRequest(undone, AniEpisodeCollectionTypeUpdate.NOT_COLLECTED))
        }
        return WebResult()
    } catch (e: ResponseException) {
        val status = e.response.status.value
        throw WebRequestException(if (status == 401) "Animeko 会话已过期，请重新登录。" else "Animeko 接口返回 HTTP $status，请刷新确认收藏状态后重试。", status)
    }
}


suspend fun animekoRecommendations(offset: Int, apiOverride: HomeAniApi? = null): WebResult {
    val api = apiOverride ?: HomeAniApi(aniAnimekoBaseUrl(), animekoHttp).apply { setBearerToken(animekoToken()) }
    val result = api.getHomeRecommendations(offset, 30).body()
    return WebResult(items = result.items.mapNotNull { item ->
        item.subjectId?.takeIf { it > 0 }?.let { id ->
            Subject(id = id.toInt(), title = item.subjectNameCn.ifBlank { item.subjectName }, original = item.subjectName, cover = item.imageUrl)
        }
    }, total = result.total.toInt())
}

suspend fun animekoSchedule(): AniLatestAiringSchedule {
    val zone = TimeZone.currentSystemDefault()
    return ScheduleAniApi(aniAnimekoBaseUrl(), animekoHttp)
        .getLatestAiringSchedule(Clock.System.now().toLocalDateTime(zone).date.toString(), browserTimeZoneId()).body()
}

/** Episode lists use the same subject endpoint as the App's repository. */
suspend fun animekoEpisodes(id: Int, apiOverride: SubjectsAniApi? = null): List<AniEpisodeCollection> =
    animekoEpisodeSubject(id, apiOverride).episodes


suspend fun updateWebEpisode(subjectId: Int, episodeId: Long, watched: Boolean, apiOverride: SubjectsAniApi? = null) {
    (apiOverride ?: subjectsApi()).batchUpdateEpisodeCollections(subjectId.toLong(),
        AniBatchUpdateEpisodeCollectionsRequest(listOf(episodeId),
            if(watched) AniEpisodeCollectionTypeUpdate.DONE else AniEpisodeCollectionTypeUpdate.NOT_COLLECTED))
}


suspend fun webCollectionCounts(apiOverride: SubjectsAniApi? = null): Map<Int,Int> {
    val stats=(apiOverride ?: subjectsApi()).getSubjectCollectionStats().body()
    return mapOf(1 to stats.wish,2 to stats.done,3 to stats.doing,4 to stats.onHold,5 to stats.dropped)
}


suspend fun animekoEpisodeSubject(id: Int, apiOverride: SubjectsAniApi? = null): AniSubjectCollection =
    (apiOverride ?: subjectsApi(false)).getSubject(id.toLong()).body()

suspend fun webEpisodeComments(id: Int, offset: Int, apiOverride: EpisodesAniApi? = null): AniEpisodeCommentsResponse =
    (apiOverride ?: EpisodesAniApi(aniAnimekoBaseUrl(), animekoHttp).apply {
        animekoToken(false).takeIf { it.isNotBlank() }?.let(::setBearerToken)
    }).listEpisodeComments(id.toLong(),offset,20,includeBangumi=true).body()
