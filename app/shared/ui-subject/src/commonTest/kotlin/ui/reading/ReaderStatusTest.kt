/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.reading

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
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
    @Test fun novelLandscapeStatusDoesNotOverlapMenu() = readerStatusToggle(novel = true, landscape = true)
    @Test fun mangaLandscapeStatusDoesNotOverlapMenu() = readerStatusToggle(novel = false, landscape = true)

    @Test fun statusKeepsRoundedCornerMarginBeyondSystemInsets() = runAniComposeUiTest {
        setContent { Box(Modifier.requiredSize(380.dp, 650.dp).testTag("viewport")) {
            ReaderStatusContent("23:59", 67, Color.Transparent, Color.White,
                Modifier.align(Alignment.BottomEnd),
                windowInsets = WindowInsets(left = 0.dp, top = 0.dp, right = 20.dp, bottom = 28.dp))
        } }
        val viewport = onNodeWithTag("viewport").getUnclippedBoundsInRoot()
        val time = onNodeWithTag("reader-status-time").getUnclippedBoundsInRoot()
        val battery = onNodeWithTag("reader-status-battery").getUnclippedBoundsInRoot()
        assertTrue(time.right <= viewport.right - 44.dp)
        assertTrue(time.bottom <= viewport.bottom - 44.dp)
        assertTrue(battery.bottom <= viewport.bottom - 44.dp)
        assertTrue(battery.left > viewport.left + 190.dp, "Indicators stay in the right half")
    }

    private fun readerStatusToggle(novel: Boolean, landscape: Boolean = false) = runAniComposeUiTest {
        var fullscreen by mutableStateOf(false)
        var enabled by mutableStateOf(true)
        val size = if (landscape) Modifier.requiredSize(650.dp, 380.dp) else Modifier.requiredSize(380.dp, 650.dp)
        setContent { ProvideCompositionLocalsForPreview {
            if (novel) {
                NovelReader(List(15) { NovelBlock("阅读正文。".repeat(30)) }, 0, 0, "测试章节", NovelReaderSettings(), {},
                    { _, _ -> }, {}, {}, {}, {}, null, null, fullscreen, { fullscreen = it },
                    size, enabled, { enabled = it })
            } else {
                MangaReaderPages(List(3) { MangaPage("$it", "", width = 400, height = 800) }, 0, {},
                    { error("Image unavailable in layout test") }, null, null, size,
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
        if (novel) {
            assertTrue(reading.bottom <= footer.top, "Novel status occupies space below the text")
        } else {
            assertEquals(reading.bottom, footer.bottom, "Manga status overlays the bottom of the reading surface")
            assertTrue(footer.top >= reading.top)
        }
        onNodeWithTag("$prefix-reader-scroll").performClick()
        onNodeWithTag("$prefix-reader-controls").assertIsDisplayed()
        onNodeWithTag("reader-status-time").assertIsNotDisplayed()
        onNodeWithTag("reader-status-battery").assertIsNotDisplayed()
        assertEquals(reading, onNodeWithTag("$prefix-reader-scroll").fetchSemanticsNode().boundsInRoot,
            "Opening the menu must not resize or move the reading surface")
        onNodeWithTag("$prefix-reader-scroll").performClick()
        onNodeWithTag("reader-status-time").assertIsDisplayed()
        assertEquals(footer, onNodeWithTag("reader-status-footer").fetchSemanticsNode().boundsInRoot,
            "Status returns to the same corner after closing the menu")
        onNodeWithTag("$prefix-reader-scroll").performClick()
        onNodeWithTag(if (novel) "novel-settings" else "manga-reader-settings").performClick()
        onNodeWithTag("reader-status-setting").performClick()
        onNodeWithText("完成").performClick()
        onNodeWithTag("reader-status-footer").assertDoesNotExist()
        if (!novel) {
            assertEquals(reading, onNodeWithTag("manga-reader-scroll").fetchSemanticsNode().boundsInRoot,
                "Disabling the overlay must not resize the manga")
        }
        runOnIdle { assertTrue(!enabled); fullscreen = false }
        runOnIdle { fullscreen = true }
        onNodeWithTag("reader-status-footer").assertDoesNotExist()
        runOnIdle { enabled = true }
        mainClock.advanceTimeBy(6000)
        onNodeWithTag("$prefix-reader-controls").assertDoesNotExist()
        onNodeWithTag("reader-status-footer").assertExists()
        onNodeWithTag("reader-status-time").assertIsDisplayed()
    }
}
