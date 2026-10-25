/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.novel

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.repository.novel.NovelBlock
import me.him188.ani.app.data.repository.novel.NovelReaderSettings
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.manga.TrackingCollectionTabsContent
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(TestOnly::class)
class NovelReaderTest {
    @Test fun pageButtonScrollsThroughIntermediatePositions() = runAniComposeUiTest {
        setContent { ProvideCompositionLocalsForPreview {
            NovelReader(List(30) { NovelBlock("正文段落。".repeat(80)) }, 0, 0, "测试章节",
                    NovelReaderSettings(), {}, { _, _ -> }, {}, {}, {}, {}, null, null, false, {},
                    Modifier.requiredSize(380.dp, 650.dp))
        } }
        onNodeWithTag("novel-reader-scroll").performClick()
        onNodeWithText("下页").assertExists()
        fun scrollPosition() = onNodeWithTag("novel-reader-scroll").fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange].value()
        val before = scrollPosition()
        mainClock.autoAdvance = false
        onNodeWithText("下页").performClick()
        mainClock.advanceTimeBy(96)
        val middle = scrollPosition()
        assertTrue(middle > before, "The page should move during the animation")
        onNodeWithText("下页").performClick()
        mainClock.advanceTimeBy(400)
        val after = scrollPosition()
        assertTrue(after > middle, "The page must not jump to its destination immediately")
        mainClock.advanceTimeBy(400)
        assertEquals(after, scrollPosition(), "Repeated taps during animation must not queue another page")
    }

    @Test fun threeTrackingTabsHaveIndependentSelection() = runAniComposeUiTest {
        var selected by mutableIntStateOf(0)
        setContent { ProvideCompositionLocalsForPreview { TrackingCollectionTabsContent(selected, { selected = it }, listOf("追番", "追漫", "追文")) } }
        onNodeWithTag("tracking-novel").performClick()
        runOnIdle { assertEquals(2, selected) }
        onNodeWithTag("tracking-manga").performClick()
        runOnIdle { assertEquals(1, selected) }
    }

    @Test fun restoresParagraphAndPreservesItAcrossFullscreenAndFontChange() = runAniComposeUiTest {
        val blocks = List(30) { NovelBlock("第 $it 段：" + "中文阅读正文。".repeat(20)) }
        var position = -1
        var fullscreen by mutableStateOf(false)
        var settings by mutableStateOf(NovelReaderSettings())
        setContent { ProvideCompositionLocalsForPreview {
            NovelReader(blocks, 10, 0, "测试章节", settings, { settings = it }, { p, _ -> position = p }, {}, {}, {}, {},
                null, {}, fullscreen, { fullscreen = it }, Modifier.requiredSize(700.dp, 500.dp))
        } }
        runOnIdle { assertEquals(10, position) }
        onNodeWithTag("novel-reader-controls").assertDoesNotExist()
        onNodeWithTag("novel-reader-scroll").performTouchInput { click() }
        onNodeWithTag("novel-settings").assertExists()
        onNodeWithTag("novel-fullscreen").performClick()
        runOnIdle { assertTrue(fullscreen); assertEquals(10, position); settings = settings.copy(fontSize = 26) }
        runOnIdle { assertEquals(10, position) }
        onNodeWithTag("novel-reader-controls").assertDoesNotExist()
    }
    @Test fun fullscreenBothOuterThirdsAdvanceAndCenterOnlyShowsControls() = runAniComposeUiTest {
        val blocks = List(60) { NovelBlock("第 $it 段：" + "这是阅读测试正文。".repeat(8)) }
        var position = 0
        var offset = 0
        setContent { ProvideCompositionLocalsForPreview {
            NovelReader(blocks, 0, 0, "测试章节", NovelReaderSettings(), {}, { p, o -> position = p; offset = o }, {}, {}, {}, {},
                null, {}, true, {}, Modifier.requiredSize(700.dp, 500.dp))
        } }
        onNodeWithTag("novel-reader-scroll").performTouchInput { click(Offset(width * 0.1f, height * 0.5f)) }
        var afterLeft = 0 to 0
        runOnIdle { assertTrue(position > 0 || offset > 0); afterLeft = position to offset }
        onNodeWithTag("novel-reader-scroll").performTouchInput { click(Offset(width * 0.9f, height * 0.5f)) }
        var afterRight = 0 to 0
        runOnIdle { assertTrue(position > afterLeft.first || position == afterLeft.first && offset > afterLeft.second); afterRight = position to offset }
        onNodeWithTag("novel-reader-scroll").performTouchInput { click(center) }
        onNodeWithTag("novel-reader-controls").assertExists()
        runOnIdle { assertEquals(afterRight, position to offset) }
    }

    @Test fun chapterEndAndNextPageAtEndAdvanceAndLastChapterDoesNot() = runAniComposeUiTest {
        val blocks = listOf(NovelBlock("短章节正文"))
        var next = 0
        var hasNext by mutableStateOf(true)
        setContent { ProvideCompositionLocalsForPreview {
            NovelReader(blocks, 0, 0, "测试章节", NovelReaderSettings(), {}, { _, _ -> }, {}, {}, {}, {},
                null, if (hasNext) ({ next++ }) else null, true, {}, Modifier.requiredSize(700.dp, 500.dp))
        } }
        onNodeWithTag("novel-chapter-end").performClick()
        runOnIdle { assertEquals(1, next) }
        onNodeWithTag("novel-reader-scroll").performTouchInput { click(Offset(width * 0.9f, height * 0.8f)) }
        runOnIdle { assertEquals(2, next); hasNext = false }
        onNodeWithText("已到最后一章").assertExists()
        onNodeWithTag("novel-reader-scroll").performTouchInput { click(Offset(width * 0.1f, height * 0.8f)) }
        runOnIdle { assertEquals(2, next) }
    }

}
