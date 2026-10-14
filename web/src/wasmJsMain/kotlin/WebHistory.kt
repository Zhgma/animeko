import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import me.him188.ani.app.tools.LocalTimeFormatter
import me.him188.ani.app.tools.TimeFormatter
import me.him188.ani.app.ui.foundation.widgets.BackNavigationIconButton
import me.him188.ani.app.ui.playback.PlaybackHistoryListItem
import me.him188.ani.app.ui.playback.PlaybackHistoryUiItem
import kotlin.time.Instant

@Composable
fun WebHistory(session: WebResult, onBack: () -> Unit, onLogin: () -> Unit, onOpen: (Int) -> Unit) {
    var items by remember(session.userId) { mutableStateOf<List<PlaybackHistoryUiItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    LaunchedEffect(session.userId, session.signedIn, revision) {
        if (!session.signedIn) return@LaunchedEffect
        loading = true; error = null
        try { items = animekoPlaybackHistory().map { PlaybackHistoryUiItem(it.episodeId.toInt(), it.subjectId.toInt(), it.episodeSort,
            it.subjectName, it.subjectImageUrl, it.episodeName, it.positionMillis, it.durationMillis,
            Instant.parse(it.updatedAt).toEpochMilliseconds()) } }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "无法读取 Animeko 云端播放历史，请重试。" }
        finally { loading = false }
    }
    CompositionLocalProvider(LocalTimeFormatter provides remember { TimeFormatter() }) {
        Scaffold(topBar = { TopAppBar(title = { Text("播放历史") }, navigationIcon = { BackNavigationIconButton(onBack) },
            actions = { TextButton({ revision++ }, enabled = !loading) { Text("刷新") } }) }) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    if (!session.signedIn) Button(onLogin) { Text("登录 Animeko") }
                    else if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    else if (error == null && items.isEmpty()) Text("暂无云端播放历史")
                    error?.let { WebError(it, { revision++ }) }
                }
                items(items, key = { it.episodeId }) { item -> PlaybackHistoryListItem(item, false, false,
                    MaterialTheme.colorScheme.surfaceContainerLow, { item.subjectId?.let(onOpen) }, {}, Modifier.fillMaxWidth()) }
            }
        }
    }
}
