/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.exploration.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.subject.SubjectCoverCardContent
import me.him188.ani.app.ui.subject.SubjectScoreLabel
import kotlin.test.Test
import kotlin.test.assertTrue

class SearchRatingPlacementTest {
    @Test fun compactPreviewKeepsScoreBesideLongTitle() = verifyPreview(340)
    @Test fun widePreviewKeepsScoreAtTopRight() = verifyPreview(620)

    private fun verifyPreview(width: Int) = runAniComposeUiTest {
        setContent {
            ProvideCompositionLocalsForPreview {
                SubjectItemLayout(false, {}, image = {},
                    title = { lines -> Text("关于我转生变成史莱姆这档事 第三季", Modifier.testTag("title"), maxLines = lines) },
                    tags = { Text("2024 年 10 月", Modifier.testTag("tags")) }, extraInfo = {},
                    rating = { SubjectScoreLabel("8.1") }, actions = {},
                    modifier = Modifier.width(width.dp).testTag("card"), ratingAtTop = true)
            }
        }
        val score = onNodeWithText("★ 8.1", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val title = onNodeWithTag("title", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val tags = onNodeWithTag("tags", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val card = onNodeWithTag("card").fetchSemanticsNode().boundsInRoot
        assertTrue(score.left >= title.right, "Score must not overlap the title")
        assertTrue(score.top < tags.top && score.right <= card.right, "Score must stay in the upper right")
    }

    @Test fun coverScoreIsAtTopRightAndCardStillOpens() = runAniComposeUiTest {
        var opened = false
        setContent {
            ProvideCompositionLocalsForPreview {
                Box(Modifier.width(160.dp)) {
                    SubjectCoverCardContent("作品", null, false, { opened = true },
                        Modifier.testTag("cover"), score = "7.6")
                }
            }
        }
        val score = onNodeWithText("★ 7.6", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val card = onNodeWithTag("cover", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(score.left > card.center.x && score.bottom < card.center.y)
        onNodeWithText("★ 7.6", useUnmergedTree = true).performTouchInput { click() }
        runOnIdle { assertTrue(opened) }
    }
}
