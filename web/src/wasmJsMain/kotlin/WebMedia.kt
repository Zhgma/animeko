import me.him188.ani.app.data.repository.media.DefaultMediaSubscriptions
import androidx.compose.runtime.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable data class BrowserMediaSettings(val maxConns: Int = 20, val downloadLimit: Long = -1, val uploadLimit: Long = -1, val webSeeds: Boolean = true, val trackers: List<String> = emptyList(), val refreshMinutes: Int = 60, val autoRefresh: Boolean = true, val playbackRate: Double = 1.0)
@Serializable data class TorrentFile(val index: Int, val name: String, val length: Long = 0, val progress: Double = 0.0, val selected: Boolean = true)
@Serializable data class TorrentTask(val id: String, val name: String = "", val state: String = "", val progress: Double = 0.0, val length: Long = 0, val downloaded: Long = 0, val speed: Double = 0.0, val uploadSpeed: Double = 0.0, val peers: Int = 0, val error: String = "", val warning: String = "", val files: List<TorrentFile> = emptyList(), val subjectTitle: String = "", val episode: String = "", val subjectId: Int = 0, val cover: String = "", val createdAt: Long? = null, val uploaded: Long? = null)
@Serializable data class FeedItem(val id: String, val title: String, val url: String = "", val page: String = "", val date: String = "", val supported: Boolean = false, val size: Long = 0, val resolution: String = "", val subtitles: List<String> = emptyList(), val subtitleKind: String = "", val alliance: String = "", val episodeStart: Double = -1.0, val episodeEnd: Double = -1.0, val published: String = "")
@Serializable data class FeedSource(val id: String, val title: String, val url: String = "", val enabled: Boolean = true, val supported: Boolean = false, val reason: String = "", val kind: String = "bt", val format: JsonObject = JsonObject(emptyMap()))
@Serializable data class BrowserSubscription(val id: String, val title: String, val url: String = "", val kind: String = "", val enabled: Boolean = true, val error: String = "", val updated: Long = 0, val sources: List<FeedSource> = emptyList(), val items: List<FeedItem> = emptyList())
@Serializable data class SubjectMediaSource(val id: String, val title: String)
@Serializable data class SubjectMediaSearch(val items: List<FeedItem> = emptyList(), val note: String = "", val hasNext: Boolean = false)
@Serializable data class BrowserMediaState(val settings: BrowserMediaSettings = BrowserMediaSettings(), val tasks: List<TorrentTask> = emptyList(), val subscriptions: List<BrowserSubscription> = emptyList(), val refreshing: Boolean = false, val secure: Boolean = true, val webrtc: Boolean = true, val feedItems: List<FeedItem> = emptyList(), val usage: Long = 0, val quota: Long = 0, val subjectSources: List<SubjectMediaSource> = emptyList(), val subjectSearch: SubjectMediaSearch = SubjectMediaSearch())
@Stable class BrowserMediaController(private val scope: CoroutineScope) {
    var state by mutableStateOf(BrowserMediaState()); private set
    var error by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false); private set
    var feedItems by mutableStateOf<List<FeedItem>>(emptyList()); private set
    suspend fun initialize() {
        try { state = webJson.decodeFromString(request("mediaInitialize", defaultMediaCatalog().toString()).raw) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message }
    }
    suspend fun refresh() { try { state = webJson.decodeFromString(request("mediaState").raw) } catch(e: CancellationException) { throw e } catch(e: Exception) { error = e.message } }
    fun action(name: String, payload: JsonObject = buildJsonObject {}, done: () -> Unit = {}) {
        if (busy) return
        busy = true; error = null
        scope.launch { try {
            state = webJson.decodeFromString(request(name, payload.toString()).raw)
            if (name == "subscriptionSearch") feedItems = state.feedItems
            done()
        } catch(e: CancellationException) { throw e } catch(e: Exception) { error = e.message } finally { busy = false } }
    }
    fun settings(value: BrowserMediaSettings) = action("mediaSettings", buildJsonObject { put("settings", webJson.encodeToJsonElement(value)) })
    fun actionBatch(name: String, ids: List<String>, done: () -> Unit = {}) {
        if (busy || ids.isEmpty()) return
        busy = true; error = null
        scope.launch {
            try {
                val failures = runBrowserDownloadBatch(ids) { id ->
                    state = webJson.decodeFromString(request(name, mediaId(id).toString()).raw)
                }
                if (failures > 0) error = "$failures 个任务操作失败，请检查任务状态后重试。"
                else done()
            } finally { busy = false }
        }
    }
}
internal suspend fun runBrowserDownloadBatch(ids: List<String>, action: suspend (String) -> Unit): Int {
    var failures = 0
    for (id in ids.distinct()) try { action(id) }
    catch (e: CancellationException) { throw e }
    catch (_: Exception) { failures++ }
    return failures
}
@Composable fun rememberBrowserMedia(): BrowserMediaController {
    val scope = rememberCoroutineScope()
    val media = remember { BrowserMediaController(scope) }
    LaunchedEffect(media) { media.initialize(); while (true) { if (!media.busy) media.refresh(); delay(2000) } }
    return media
}
fun mediaBytes(bytes: Number): String {
    var value = bytes.toDouble(); var i = 0
    val units = listOf("B", "KiB", "MiB", "GiB")
    while(value >= 1024 && i < 3) { value /= 1024; i++ }
    return "${(value * 10).toLong() / 10.0} ${units[i]}"
}
fun mediaId(id: String) = buildJsonObject { put("id", id) }

fun defaultMediaCatalog() = buildJsonObject {
    putJsonArray("catalogs") {
        DefaultMediaSubscriptions.anime.forEach { url -> add(buildJsonObject {
            put("url", url); put("title", if (url == DefaultMediaSubscriptions.WEB) "Animeko 网站资源" else "Animeko BT 资源"); put("kind", "animeko")
        }) }
        add(buildJsonObject { put("url", DefaultMediaSubscriptions.MANGA); put("title", "Animecomic 漫画源"); put("kind", "manga") })
    }
}
