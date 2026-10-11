/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.subject.details

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.manga.MangaCollectionCard
import kotlin.test.Test
import kotlin.test.assertEquals

class MangaCardHeightTest {
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
