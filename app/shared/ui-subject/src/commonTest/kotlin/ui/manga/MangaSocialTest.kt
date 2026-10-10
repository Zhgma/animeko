/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.manga

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.performClick
import me.him188.ani.app.data.models.subject.SelfRatingInfo
import me.him188.ani.app.data.repository.manga.MangaCollection
import me.him188.ani.app.data.repository.manga.MangaRelation
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.rating.EditableRatingActions
import me.him188.ani.app.ui.rating.EditableRatingDialogsHost
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(TestOnly::class)
class MangaSocialTest {
    @Test fun entryOpensSharedEditorWithExistingReview() = runAniComposeUiTest {
        var state by mutableStateOf(MangaDetailsUiState(ratingLoaded = true))
        state = state.copy(rating = state.rating.copy(enableEdit = true,
            selfRatingInfo = SelfRatingInfo(8, "Existing review", listOf("tag"), true)))
        val actions = object : EditableRatingActions by EditableRatingActions.Noop {
            override fun requestEditRating() { state = state.copy(rating = state.rating.copy(showRatingDialog = true)) }
        }
        setContent {
            ProvideCompositionLocalsForPreview {
                MangaRatingEntry(state, actions)
                EditableRatingDialogsHost(state.rating, actions)
            }
        }
        onNodeWithTag("manga-edit-rating").performClick()
        onNode(hasSetTextAction()).assertTextContains("Existing review")
    }

    @Test fun directRelationsKeepLabelsWithoutInventingSequelOrder() {
        val manga = MangaCollection(355555, "Manga", "Original", "cover", 0, 0, 0, 0, 0, false)
        val state = MangaDetailsUiState(relations = listOf(
            MangaRelation(513345, 2, "Anime", "Anime", "", "动画"),
            MangaRelation(372122, 1, "Volume 1", "Volume 1", "", "单行本")))
        val graph = mangaRelationGraph(manga, state)
        assertEquals(355555, graph.subjectId)
        assertEquals(1, graph.mainline.size)
        assertEquals(listOf("动画", "单行本"), graph.mainline.single().branches.map { it.relationLabel })
        assertEquals(listOf(513345, 372122), graph.mainline.single().branches.map { it.subject.subjectId })
    }
}
