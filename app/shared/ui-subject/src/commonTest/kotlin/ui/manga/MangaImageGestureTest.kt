/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.manga

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.repository.manga.MangaPage
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(TestOnly::class)
class MangaImageGestureTest {
    @Test fun doubleTapUsesTapPositionAndDoesNotToggleControls() = runAniComposeUiTest {
        var taps = 0
        var active by mutableStateOf(true)
        setContent {
            ProvideCompositionLocalsForPreview {
                Box(Modifier.requiredSize(320.dp, 700.dp)) {
                    MangaReaderImage(MangaPage("one", ""), 1, active, { "fixture" }, onTap = { taps++ })
                }
            }
        }
        val node = onNodeWithTag("manga-page-viewport-1")
        val bounds = node.fetchSemanticsNode().boundsInRoot
        val center = Offset(bounds.width / 2, bounds.height / 2)
        val anchor = center * 0.5f
        node.performTouchInput { doubleClick(anchor) }
        var semantics = node.fetchSemanticsNode().config
        assertEquals(2.5f, semantics[MangaImageScale])
        val mapped = center + (anchor - center) * semantics[MangaImageScale] + semantics[MangaImageTranslation]
        assertEquals(anchor.x, mapped.x, 1f)
        assertEquals(anchor.y, mapped.y, 1f)
        assertEquals(0, taps)
        node.performTouchInput { doubleClick(anchor) }
        semantics = node.fetchSemanticsNode().config
        assertEquals(1f, semantics[MangaImageScale])
        assertEquals(Offset.Zero, semantics[MangaImageTranslation])
        node.performTouchInput { click(anchor) }
        mainClock.advanceTimeBy(400)
        assertEquals(1, taps)
        node.performTouchInput { doubleClick(anchor) }
        runOnIdle { active = false }
        assertEquals(1f, node.fetchSemanticsNode().config[MangaImageScale])
    }

    @Test fun offCenterPinchKeepsInitialMidpointAnchoredAcrossRepeatedGestures() = runAniComposeUiTest {
        setContent {
            ProvideCompositionLocalsForPreview {
                Box(Modifier.requiredSize(400.dp, 750.dp)) {
                    MangaReaderImage(MangaPage("one", ""), 1, true, { "fixture" })
                }
            }
        }
        val node = onNodeWithTag("manga-page-viewport-1")
        val bounds = node.fetchSemanticsNode().boundsInRoot
        val center = Offset(bounds.width / 2, bounds.height / 2)
        val anchor = Offset(bounds.width * 0.35f, bounds.height * 0.3f)
        repeat(2) {
            node.performTouchInput {
                pinch(start0 = anchor - Offset(30f, 0f), start1 = anchor + Offset(30f, 0f),
                    end0 = anchor - Offset(70f, 0f), end1 = anchor + Offset(70f, 0f), durationMillis = 500)
            }
            val semantics = node.fetchSemanticsNode().config
            assertTrue(semantics[MangaImageScale] > 1.5f)
            val mapped = center + (anchor - center) * semantics[MangaImageScale] + semantics[MangaImageTranslation]
            assertEquals(anchor.x, mapped.x, 1f)
            assertEquals(anchor.y, mapped.y, 1f)
        }
    }
}
