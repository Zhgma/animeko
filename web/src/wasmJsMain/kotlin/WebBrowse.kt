import me.him188.ani.app.ui.exploration.trends.TrendingRankBadge
import me.him188.ani.app.ui.exploration.trends.TrendingHeatText
import me.him188.ani.app.data.models.preference.NsfwMode
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.him188.ani.app.ui.exploration.search.SubjectItemLayout
import me.him188.ani.app.ui.exploration.search.SubjectItemDefaults

val LocalWebOriginalTitle = staticCompositionLocalOf { false }
fun Subject.displayTitle(original: Boolean) = if (original && this.original.isNotBlank()) this.original else title

@Composable
fun WebSubjectItem(subject: Subject, onOpen: (Int) -> Unit, modifier: Modifier = Modifier) {
    val original = LocalWebOriginalTitle.current
    SubjectItemLayout(false, { onOpen(subject.id) },
        image = { Box(Modifier.fillMaxSize()) { SubjectItemDefaults.Image(subject.cover); if (subject.trendingRank > 0) TrendingRankBadge(subject.trendingRank, Modifier.align(Alignment.TopStart)) } },
        title = { lines -> Text(subject.displayTitle(original), maxLines = lines, overflow = TextOverflow.Ellipsis) },
        tags = { Text(listOf(subject.date, subject.platform, subject.relation).filter { it.isNotBlank() }.joinToString(" · "), maxLines = 2) },
        extraInfo = { if (subject.trendingRank > 0) TrendingHeatText(subject.heat) else Text(subject.tags.take(3).joinToString(" / "), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        rating = { Text(if (subject.score.isBlank()) "暂无评分" else "★ ${subject.score}  ${if (subject.rank > 0) "#${subject.rank}" else ""}", color = MaterialTheme.colorScheme.primary) },
        actions = {}, modifier = modifier.fillMaxWidth())
}


@Composable
fun WebError(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(message, color = MaterialTheme.colorScheme.error)
        TextButton(onRetry) { Text("重试") }
    }
}
