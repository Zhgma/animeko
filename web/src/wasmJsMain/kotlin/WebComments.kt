import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import me.him188.ani.app.ui.comment.CommentItemLayout
import me.him188.ani.app.ui.rating.FiveRatingStars
import me.him188.ani.app.ui.subject.details.sections.SubjectCommentsHeader
import me.him188.ani.app.tools.LocalTimeFormatter
import me.him188.ani.app.tools.TimeFormatter
import me.him188.ani.app.tools.formatDateTime
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.him188.ani.app.data.repository.manga.MangaComments
import me.him188.ani.app.data.repository.manga.parseMangaComments
import me.him188.ani.app.ui.foundation.avatar.AvatarImage

@Composable
fun WebComments(id: Int, type: Int, onWriteReview: () -> Unit) {
    var offset by rememberSaveable(id) { mutableIntStateOf(0) }
    var result by remember(id) { mutableStateOf(MangaComments(emptyList(), 0)) }
    var loading by remember(id) { mutableStateOf(true) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    var retry by remember(id) { mutableIntStateOf(0) }
    LaunchedEffect(id, type, offset, retry) {
        loading = true; error = null; result = MangaComments(emptyList(), 0)
        try { result = parseMangaComments(request("comments", buildJsonObject { put("id", id); put("type", type); put("offset", offset) }.toString()).raw) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message }
        finally { loading = false }
    }
    CompositionLocalProvider(LocalTimeFormatter provides remember { TimeFormatter() }) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f)) {
            SubjectCommentsHeader(onWriteReview)
            LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    error?.let { WebError(it, { retry++ }) }
                    if (!loading && error == null && result.items.isEmpty()) Text("暂无评论", Modifier.padding(16.dp))
                }
                items(result.items, key = { it.id }) { comment ->
                    CommentItemLayout(
                        avatar = { AvatarImage(comment.avatar.takeIf { it.isNotBlank() }, Modifier.size(36.dp)) },
                        title = { Text(comment.user, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) },
                        titleTrailing = { if (comment.rating > 0) FiveRatingStars(comment.rating, starSize = 12.dp) },
                        content = { SelectionContainer { Text(comment.text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium)) } },
                        timestamp = { Text(listOf(formatDateTime(comment.updatedAt), "Bangumi").filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
                item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    TextButton({ offset -= 20 }, enabled = offset > 0 && !loading) { Text("上一页评论") }
                    Text("${offset / 20 + 1}", Modifier.align(Alignment.CenterVertically))
                    TextButton({ offset += 20 }, enabled = offset + 20 < result.total && !loading) { Text("下一页评论") }
                } }
            }
        }
    }
}
