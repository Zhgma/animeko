/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.manga

import androidx.compose.runtime.mutableStateOf
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.cachedIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import me.him188.ani.app.data.models.UserInfo
import me.him188.ani.app.data.repository.manga.MangaComment
import me.him188.ani.app.data.repository.manga.MangaDetailsRepository
import me.him188.ani.app.ui.comment.CommentMapperContext
import me.him188.ani.app.ui.comment.CommentState
import me.him188.ani.app.ui.comment.UIComment

internal fun MangaComment.toUiComment() = UIComment(id.toLong(), "manga:$id",
    UserInfo(userId.toString(), username, user, avatar), CommentMapperContext.parseBBCode(text), updatedAt,
    emptyList(), emptyList(), 0, rating.takeIf { it > 0 }, rawContent = text)

/** Uses the same paging contract as anime subject reviews. */
internal class MangaCommentPaging(id: Int, repository: MangaDetailsRepository, scope: CoroutineScope) {
    private var current: PagingSource<Int, UIComment>? = null
    private val count = mutableStateOf<Int?>(null)
    val state = CommentState(Pager(PagingConfig(pageSize = 20, enablePlaceholders = false)) {
        object : PagingSource<Int, UIComment>() {
            override fun getRefreshKey(state: PagingState<Int, UIComment>): Int? = null
            override suspend fun load(params: LoadParams<Int>): LoadResult<Int, UIComment> = try {
                val offset = params.key ?: 0
                val page = repository.comments(id, offset)
                count.value = page.total
                LoadResult.Page(page.items.map { it.toUiComment() }, null,
                    (offset + page.items.size).takeIf { page.items.isNotEmpty() && it < page.total })
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { LoadResult.Error(e) }
        }.also { current = it }
    }.flow.cachedIn(scope), count, { _, _, _ -> }, scope)
    fun refresh() { current?.invalidate() }
}
