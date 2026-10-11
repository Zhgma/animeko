/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.subject.details

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.subject.collection.SubjectCollectionCard
import me.him188.ani.app.ui.subject.details.components.SubjectDetailsHeaderCompactContent
import kotlin.test.Test
import kotlin.test.assertEquals

class SharedWebPresentationTest {
    @Test
    fun compactHeaderSwitchesTitleWithoutNavigatingAway() = runAniComposeUiTest {
        setContent {
            ProvideCompositionLocalsForPreview {
                SubjectDetailsHeaderCompactContent(
                    title = { Text("中文标题") }, subtitle = { Text("Original title") },
                    seasonTags = { Text("漫画") }, collectionData = {}, collectionAction = {},
                    selectEpisodeButton = {}, rating = {}, cover = { Box(Modifier.size(140.dp, 198.dp)) },
                )
            }
        }
        onNodeWithText("中文标题").performClick()
        onNodeWithText("Original title").assertExists().performClick()
        onNodeWithText("中文标题").assertExists()
    }

    @Test
    fun sharedCollectionCardDispatchesOneInternalNavigation() = runAniComposeUiTest {
        var clicks = 0
        setContent {
            ProvideCompositionLocalsForPreview {
                SubjectCollectionCard({ clicks++ }, cover = { Box(Modifier.size(100.dp, 148.dp)) },
                    content = { Text("我的追番") }, modifier = Modifier.testTag("collection-card"))
            }
        }
        onNodeWithTag("collection-card").performClick()
        runOnIdle { assertEquals(1, clicks) }
    }
}
