/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.novel

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.*
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
}
