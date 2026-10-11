/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.exploration.search

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import me.him188.ani.app.domain.search.SubjectType
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SharedSearchPresentationTest {
    @Test
    fun inputRetainsKeywordAcrossTypeChangeAndSubmitsImeSearch() = runAniComposeUiTest {
        var query by mutableStateOf("")
        var type by mutableStateOf(SubjectType.ANIME)
        var submitted = ""
        setContent {
            ProvideCompositionLocalsForPreview {
                SubjectSearchInput(query, { query = it }, { submitted = query }, type, { type = it },
                    { query = "" }, false, {})
            }
        }
        onNodeWithTag("subject-search-input").performTextInput("薰香花朵")
        onNodeWithTag("search-subject-type").performClick()
        onNodeWithTag("search-subject-type-BOOK").performClick()
        runOnIdle { assertEquals(SubjectType.BOOK, type); assertEquals("薰香花朵", query) }
        onNodeWithTag("subject-search-input").performImeAction()
        runOnIdle { assertEquals("薰香花朵", submitted) }
        onNodeWithContentDescription("清除搜索").performClick()
        runOnIdle { assertEquals("", query) }
    }
}
