/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.manga

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.him188.ani.app.data.repository.manga.BangumiBookKind
import me.him188.ani.app.data.repository.manga.MangaRelation
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.navigation.NavRoutes

internal fun bookRelationRoute(related: MangaRelation): NavRoutes = when {
    BangumiBookKind.NOVEL.accepts(related.type, related.platform) ->
        NavRoutes.NovelDetail(related.id, related.title, related.originalTitle, related.cover)
    related.type == 1 -> NavRoutes.MangaDetail(related.id, related.title, related.originalTitle, related.cover)
    else -> NavRoutes.SubjectDetail(related.id)
}

/** Cards and graph nodes share the same in-app destination and book classification. */
@Composable
internal fun rememberOpenBookRelation(
    currentId: Int, relations: List<MangaRelation>, onCurrentSubject: () -> Unit,
    resolve: suspend (MangaRelation) -> MangaRelation,
): (Int) -> Unit {
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    var opening by remember(currentId) { mutableStateOf(false) }
    var error by remember(currentId) { mutableStateOf<String?>(null) }
    error?.let { message ->
        AlertDialog(onDismissRequest = { error = null }, title = { Text("关联作品") },
            text = { Text(message) }, confirmButton = { TextButton({ error = null }) { Text("确定") } })
    }
    return { id ->
        if (id == currentId) onCurrentSubject()
        else if (!opening) relations.firstOrNull { it.id == id }?.let { related ->
            opening = true
            scope.launch {
                try { navigator.navigate(bookRelationRoute(resolve(related))) }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { error = e.message ?: "关联作品加载失败，请重试" }
                finally { opening = false }
            }
        }
    }
}
