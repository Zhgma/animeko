/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.manga

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Density
import me.him188.ani.app.data.repository.manga.MangaSource
import me.him188.ani.app.data.repository.manga.MangaSourceBook
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.mediaselect.MediaSelectorMode
import me.him188.ani.app.ui.mediaselect.common.MediaSelectorChromeTestTags
import me.him188.ani.app.ui.mediaselect.common.MediaSelectorDialogTestTags
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(TestOnly::class, ExperimentalTestApi::class)
class MangaSourcePresentationTest {
    private fun check(width: Int, height: Int) = runSkikoComposeUiTest(Size(width.toFloat(), height.toFloat()), density = Density(1f)) {
        val book = MangaSourceBook(MangaSource.KOMIIC, "one", "碧蓝之海")
        val other = MangaSourceBook(MangaSource.GODA, "other", "另一个来源结果")
        var selected: MangaSourceBook? = null
        var closed = 0
        var searches = 0
        var state by mutableStateOf(MangaDetailsUiState(source = MangaSource.KOMIIC, book = book,
            sourceSearches = mapOf(
                MangaSource.KOMIIC to MangaSourceSearchState("碧蓝之海", listOf(book)),
                MangaSource.GODA to MangaSourceSearchState("碧蓝之海", listOf(other)))))
        setContent {
            ProvideCompositionLocalsForPreview {
                MangaSourcesDialog(state, "碧蓝之海", { closed++ }, { source, keyword ->
                    searches++
                    state = state.copy(sourceSearches = state.sourceSearches +
                        (source to MangaSourceSearchState(keyword, listOf(book))))
                }, { selected = it }, {})
            }
        }
        val out = File("build/screenshots/manga-sources").also { it.mkdirs() }
        fun capture(mode: String) {
            waitForIdle()
            File(out, "$width-$height-$mode.png").writeBytes(
                Image.makeFromBitmap(captureToImage().asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
        onNodeWithTag(MediaSelectorChromeTestTags.MODE_CHIP).assertIsDisplayed()
        onNodeWithTag(MediaSelectorDialogTestTags.CLOSE).assertIsDisplayed()
        onNodeWithTag("manga-source-query").assertDoesNotExist()
        capture("auto")
        onNodeWithTag(MediaSelectorChromeTestTags.MODE_CHIP).performClick()
        onNodeWithTag(MediaSelectorChromeTestTags.modeItem(MediaSelectorMode.BT)).assertDoesNotExist()
        onNodeWithTag(MediaSelectorChromeTestTags.modeItem(MediaSelectorMode.MANUAL)).performClick()
        onNodeWithTag("manga-source-query").assertIsDisplayed()
        assertEquals(0, searches)
        onNodeWithText(other.title).assertDoesNotExist()
        capture("manual")
        onNodeWithTag("manga-source-query").performTextReplacement("新检索词")
        assertEquals(0, searches)
        onNodeWithTag("manga-source-query").performImeAction()
        waitForIdle()
        assertEquals(1, searches)
        onNodeWithText(book.title).performClick()
        assertEquals(book, selected)
        onNodeWithTag(MediaSelectorDialogTestTags.CLOSE).performClick()
        assertEquals(1, closed)
    }

    @Test fun phone() = check(380, 820)
    @Test fun tablet() = check(1200, 800)
    @Test fun shortLandscape() = check(900, 360)
}
