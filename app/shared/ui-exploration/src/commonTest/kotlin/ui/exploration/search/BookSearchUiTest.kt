/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.exploration.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.paging.PagingData
import kotlinx.coroutines.flow.flowOf
import me.him188.ani.app.domain.search.SubjectSearchQuery
import me.him188.ani.app.domain.search.SubjectType
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(TestOnly::class)
class BookSearchUiTest {
    @Test
    fun compactSearchBarPreservesTypedKeywordWhenSwitchingToBooks() = runAniComposeUiTest {
        var state by mutableStateOf(createTestSearchPageState(query = SubjectSearchQuery(""), hasActiveSearch = false))
        setContent {
            ProvideCompositionLocalsForPreview {
                SearchPage(state, onIntent = { intent ->
                    if (intent is SearchPageIntent.UpdateQuery) state = state.withQuery(intent.query)
                }, suggestionsPager = { flowOf(PagingData.empty()) }, detailContent = {},
                    modifier = Modifier.requiredSize(380.dp, 650.dp))
            }
        }
        onNodeWithTag("subject-search-input").performTextReplacement("薰香花朵")
        onNodeWithTag("search-subject-type").performClick()
        onNodeWithTag("search-subject-type-BOOK").performClick()
        runOnIdle {
            assertEquals(SubjectType.BOOK, state.query.type)
            assertEquals("薰香花朵", state.query.keywords)
        }
    }

    @Test
    fun searchTypeDropdownSwitchesBothWays() = runAniComposeUiTest {
        var type by mutableStateOf(SubjectType.ANIME)
        setContent {
            ProvideCompositionLocalsForPreview {
                SearchSubjectTypeSelector(type) { type = it }
            }
        }
        onNodeWithTag("search-subject-type").assertExists().performClick()
        onNodeWithTag("search-subject-type-BOOK").performClick()
        runOnIdle { assertEquals(SubjectType.BOOK, type) }
        onNodeWithTag("search-subject-type").performClick()
        onNodeWithTag("search-subject-type-ANIME").performClick()
        runOnIdle { assertEquals(SubjectType.ANIME, type) }
    }
}
