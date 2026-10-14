/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.subject.details

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.manga.MangaCollectionCard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MangaCardHeightTest {
    @Test
    fun progressShrinksToOneCompleteLineAndRecoversWhenWidened() = runAniComposeUiTest {
        val width = mutableStateOf(220.dp)
        val progress = "204 / — 话 · 24 / — 卷"
        setContent {
            ProvideCompositionLocalsForPreview {
                MangaCollectionCard("漫画", progress, {}, Modifier.width(width.value), image = {})
            }
        }
        fun layout(): TextLayoutResult {
            val results = mutableListOf<TextLayoutResult>()
            onNodeWithText(progress, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            return results.single()
        }
        val originalWidth = layout().getLineRight(0)
        runOnIdle { width.value = 140.dp }
        val narrow = layout()
        assertEquals(1, narrow.lineCount)
        assertFalse(narrow.isLineEllipsized(0))
        assertFalse(narrow.didOverflowWidth)
        assertTrue(narrow.getLineRight(0) < originalWidth)
        runOnIdle { width.value = 220.dp }
        assertEquals(originalWidth, layout().getLineRight(0))
    }

    @Test
    fun longTitleDoesNotIncreaseCardHeight() = runAniComposeUiTest {
        setContent {
            ProvideCompositionLocalsForPreview {
                Row {
                    listOf("短标题", "GRANDBLUE 碧蓝之海 这是会超出宽度的很长的标题").forEachIndexed { index, title ->
                        MangaCollectionCard(title, "112 / — 话 · 0 / — 卷", {},
                            Modifier.width(168.dp).testTag("card-$index"), image = { Box(Modifier.size(168.dp, 240.dp)) })
                    }
                }
            }
        }
        assertEquals(onNodeWithTag("card-0").fetchSemanticsNode().boundsInRoot.height,
            onNodeWithTag("card-1").fetchSemanticsNode().boundsInRoot.height)
    }
}
