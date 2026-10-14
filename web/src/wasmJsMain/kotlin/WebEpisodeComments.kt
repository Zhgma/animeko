// SPDX-License-Identifier: AGPL-3.0-only
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import me.him188.ani.app.ui.comment.CommentItemLayout
import me.him188.ani.app.ui.foundation.avatar.AvatarImage
import me.him188.ani.client.models.AniEpisodeCommentsResponse

@Composable
fun WebEpisodeComments(id: Int) {
    var result by remember(id) { mutableStateOf<AniEpisodeCommentsResponse?>(null) }
    var offset by remember(id) { mutableIntStateOf(0) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    var loading by remember(id) { mutableStateOf(true) }
    var retry by remember(id) { mutableIntStateOf(0) }
    LaunchedEffect(id,offset,retry) {
        loading=true;error=null;result=null
        try { result=webEpisodeComments(id,offset) }
        catch(e: CancellationException) {throw e}
        catch(e: Exception) {error=e.message}
        finally {loading=false}
    }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {
            if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let {WebError(it,{retry++})}
            if(result?.items?.isEmpty()==true) Text("暂无评论")
            if(result?.bangumiUnavailable==true) Text("Bangumi 评论暂时无法加载",color=MaterialTheme.colorScheme.outline)
        }
        items(result?.items.orEmpty(),key={it.id}) {comment ->
            CommentItemLayout(
                avatar={AvatarImage(comment.author?.avatarUrl,Modifier.size(36.dp))},
                title={Text(comment.author?.nickname ?: "用户",style=MaterialTheme.typography.bodySmall)},
                titleTrailing={},content={SelectionContainer {Text(comment.contentBbcode,style=MaterialTheme.typography.bodyMedium)}},
                timestamp={Text(comment.source.toString(),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.outline)})
        }
        item {Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center) {
            TextButton({offset-=20},enabled=offset>0 && !loading) {Text("上一页")}
            TextButton({offset+=20},enabled=!loading && offset+20<(result?.total ?: 0)) {Text("下一页")}
        }}
    }
}
