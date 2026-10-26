/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.reading

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.repository.manga.MangaPage
import me.him188.ani.app.data.repository.novel.NovelBlock
import me.him188.ani.app.data.repository.novel.NovelReaderSettings
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.manga.MangaReaderPages
import me.him188.ani.app.ui.novel.NovelReader
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(TestOnly::class)
class ReaderStatusTest {
    @Test fun clockUses24HourFormatAndBatteryHasNoPercentageText() = runAniComposeUiTest {
        assertEquals("00:00", readerClockText(0, 0))
        assertEquals("09:05", readerClockText(9, 5))
        assertEquals("23:59", readerClockText(23, 59))
        setContent { ReaderStatusContent(readerClockText(23, 59), 67, Color.Black, Color.White) }
        onNodeWithTag("reader-status-time").assertTextEquals("23:59")
        onNodeWithTag("reader-status-battery").assertExists()
        assertEquals(0, onAllNodesWithText("67%").fetchSemanticsNodes().size)
    }

    @Test fun novelFullscreenStatusStaysVisibleAndCanBeDisabled() = readerStatusToggle(novel = true)
    @Test fun mangaFullscreenStatusStaysVisibleAndCanBeDisabled() = readerStatusToggle(novel = false)

    private fun readerStatusToggle(novel: Boolean) = runAniComposeUiTest {
        var fullscreen by mutableStateOf(false)
        var enabled by mutableStateOf(true)
        setContent { ProvideCompositionLocalsForPreview {
            if (novel) {
                NovelReader(List(15) { NovelBlock("阅读正文。".repeat(30)) }, 0, 0, "测试章节", NovelReaderSettings(), {},
                    { _, _ -> }, {}, {}, {}, {}, null, null, fullscreen, { fullscreen = it },
                    Modifier.requiredSize(380.dp, 650.dp), enabled, { enabled = it })
            } else {
                MangaReaderPages(List(3) { MangaPage("$it", "", width = 400, height = 800) }, 0, {},
                    { error("Image unavailable in layout test") }, null, null, Modifier.requiredSize(380.dp, 650.dp),
                    fullscreen = fullscreen, onFullscreen = { fullscreen = it },
                    showReaderStatus = enabled, onShowReaderStatusChange = { enabled = it })
            }
        } }
        val prefix = if (novel) "novel" else "manga"
        onNodeWithTag("reader-status-footer").assertDoesNotExist()
        runOnIdle { fullscreen = true }
        onNodeWithTag("reader-status-footer").assertExists()
        val footer = onNodeWithTag("reader-status-footer").fetchSemanticsNode().boundsInRoot
        val reading = onNodeWithTag("$prefix-reader-scroll").fetchSemanticsNode().boundsInRoot
        assertTrue(reading.bottom <= footer.top, "Status must occupy space below the reading surface")
        onNodeWithTag("$prefix-reader-scroll").performClick()
        onNodeWithTag(if (novel) "novel-settings" else "manga-reader-settings").performClick()
        onNodeWithTag("reader-status-setting").performClick()
        onNodeWithText("完成").performClick()
        onNodeWithTag("reader-status-footer").assertDoesNotExist()
        runOnIdle { assertTrue(!enabled); fullscreen = false }
        runOnIdle { fullscreen = true }
        onNodeWithTag("reader-status-footer").assertDoesNotExist()
        runOnIdle { enabled = true }
        mainClock.advanceTimeBy(6000)
        onNodeWithTag("$prefix-reader-controls").assertDoesNotExist()
        onNodeWithTag("reader-status-footer").assertExists()
    }
}
