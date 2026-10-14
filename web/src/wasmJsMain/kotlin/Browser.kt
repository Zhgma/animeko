@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

import kotlinx.coroutines.suspendCancellableCoroutine
import me.him188.ani.app.data.repository.manga.BOOK_SEARCH_PAGE_SIZE
import me.him188.ani.app.data.repository.manga.MangaRequestRouting
import me.him188.ani.app.data.repository.manga.bookSearchBody
import me.him188.ani.app.domain.search.SearchSort
import me.him188.ani.client.models.AniSubjectCollection
import kotlinx.serialization.encodeToString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import me.him188.ani.app.data.repository.manga.BangumiMangaCollectionLoader
import me.him188.ani.app.data.repository.manga.MangaCollectionType
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull

val webJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

@Serializable
data class Subject(
    val id: Int, val type: Int = 2, val title: String = "", val original: String = "",
    val cover: String = "", val score: String = "", val rank: Int = 0,
    val date: String = "", val summary: String = "", val count: Int = 0,
    val tags: List<String> = emptyList(),
    val sample: Boolean = false,
    val platform: String = "", val votes: Int = 0, val volumes: Int = 0,
    val info: List<InfoRow> = emptyList(), val collection: Map<String, Int> = emptyMap(),
    val histogram: Map<String, Int> = emptyMap(), val relation: String = "",
    val animeCollection: AniSubjectCollection? = null,
    val trendingRank: Int = 0, val heat: Int = 0,
    val finished: Boolean? = null, val progress: Int = 0, val volumeProgress: Int = 0, val nsfw: Boolean = false,
)
@Serializable
data class InfoRow(val label: String, val value: String)
@Serializable
data class LibraryEntry(val subject: Subject, val status: Int = 3, val progress: Int = 0)
@Serializable
data class WebResult(
    val ok: Boolean = true, val error: String = "", val items: List<Subject> = emptyList(),
    val subject: Subject? = null, val username: String = "", val nickname: String = "",
    val cached: Boolean = false, val total: Int = 0,
    val raw: String = "",
    val pending: Boolean = false,
    val userId: String = "", val email: String = "", val bangumiError: String = "",
    val signedIn: Boolean = false, val hasBangumi: Boolean = false, val avatar: String = "",
    val providers: List<String> = emptyList(), val otpId: String = "", val hasExistingUser: Boolean? = null,
    val status: Int = 0,
    val externalAccounts: Map<String, String> = emptyMap(), val profileLoaded: Boolean = false,
    val hasMore: Boolean = false,
)

external fun aniRequest(action: String, payload: String, callback: (String) -> Unit)
external fun aniRead(key: String): String
external fun aniWrite(key: String, value: String): Boolean
external fun aniDark(): Boolean
external fun aniOpen(url: String)
external fun aniTheme(dark: Boolean)
external fun aniReady()
external fun aniLogout()
external fun aniCurrentRoute(): String
external fun aniNavigate(route: String)
external fun aniWatchRoute(callback: (String) -> Unit)
external fun aniUnwatchRoute()
external fun aniOAuthPrepare(provider: String, bind: Boolean): Boolean
external fun aniAccountCancel()
external fun aniOAuthCancel()
external fun aniOAuthReopen(): Boolean

suspend fun request(action: String, payload: String = "{}"): WebResult {
    if (action == "search") {
        val params = webJson.parseToJsonElement(payload).jsonObject
        if (params["type"]?.jsonPrimitive?.intOrNull == 2) return animekoSearch(params)
    }
    if (action in setOf("collection", "libraryRead", "libraryWrite")) {
        val params = webJson.parseToJsonElement(payload).jsonObject
        if (params["type"]?.jsonPrimitive?.intOrNull == 2) return animekoCollectionRequest(action, params)
    }
    return browserRequest(action, payload)
}

private suspend fun browserRequest(action: String, payload: String): WebResult = suspendCancellableCoroutine { continuation ->
    aniRequest(action, payload) { raw ->
        if (continuation.isActive) {
            try {
                val result = webJson.decodeFromString<WebResult>(raw)
                if (result.ok) continuation.resume(result)
                else continuation.resumeWithException(WebRequestException(result.error, result.status))
            } catch (e: Exception) { continuation.resumeWithException(e) }
        }
    }
}

class WebRequestException(message: String, val status: Int) : Exception(message)

fun loadLibrary(): List<LibraryEntry> = runCatching {
    webJson.decodeFromString<List<LibraryEntry>>(aniRead("library"))
}.getOrDefault(emptyList())

/** Uses the App's pagination, manga classification and progress mapping unchanged. */
suspend fun loadMangaCollection(status: Int): List<Subject> {
    var result = emptyList<Subject>()
    val loader = BangumiMangaCollectionLoader { path, _, type, offset ->
        request("mangaRaw", buildJsonObject { put("path", path); if (type != null) put("status", type); put("offset", offset) }.toString()).raw
    }
    loader.loadCollections("", MangaCollectionType.entries.first { it.bangumiId == status }) { _, list ->
        result = list.map { manga -> Subject(id = manga.subjectId, type = 1, title = manga.title, original = manga.originalTitle,
            cover = manga.coverUrl, count = manga.totalChapters, date = "${manga.chaptersRead} / ${manga.totalChapters.takeIf { it > 0 } ?: "—"} 话 · ${manga.volumesRead} 卷") }
    }
    return result
}

external fun aniInstallMangaApi(route: (String, String, String) -> String, search: (String, String) -> String, pageSize: Int)

fun installSharedMangaApi() {
    aniInstallMangaApi(
        { method, url, body -> webJson.encodeToString(MangaRequestRouting.resolve(method, url, body.takeIf { it.isNotEmpty() })) },
        { keyword, sort -> bookSearchBody(keyword, when (sort) {
            "rank" -> SearchSort.RANK
            "collect", "heat" -> SearchSort.COLLECTION
            "date" -> SearchSort.DATE
            else -> SearchSort.MATCH
        }) },
        BOOK_SEARCH_PAGE_SIZE,
    )
}
