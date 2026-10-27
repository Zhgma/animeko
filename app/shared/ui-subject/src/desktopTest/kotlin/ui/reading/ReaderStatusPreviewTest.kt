/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.reading

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.data.repository.manga.MangaPage
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.manga.MangaReaderPages
import me.him188.ani.app.ui.manga.mangaImageFileUri
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderStatusPreviewTest {
    @OptIn(TestOnly::class)
    @Test fun mangaImageContinuesBehindStatusWithoutBottomBand() = runAniComposeUiTest {
        val page = File.createTempFile("manga-status-overlay", ".png")
        val pageColor = Color(0xff537c91)
        val fixture = BufferedImage(400, 800, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until fixture.height) for (x in 0 until fixture.width) {
            fixture.setRGB(x, y, 0xff537c91.toInt())
        }
        ImageIO.write(fixture, "png", page)
        try {
            setContent { ProvideCompositionLocalsForPreview {
                MangaReaderPages(listOf(MangaPage("overlay", "", width = 400, height = 800)), 0, {},
                    { mangaImageFileUri(page.absolutePath) }, null, null,
                    Modifier.requiredSize(380.dp, 650.dp).testTag("manga-overlay-preview"), fullscreen = true)
            } }
            waitUntil(timeoutMillis = 10_000) {
                onAllNodesWithTag("manga-page-image-1").fetchSemanticsNodes().any {
                    it.config[SemanticsProperties.StateDescription] == "decoded"
                }
            }
            mainClock.advanceTimeBy(6000)
            onNodeWithTag("manga-reader-controls").assertDoesNotExist()
            val screenshot = onNodeWithTag("manga-overlay-preview").captureToImage()
            val pixels = screenshot.toPixelMap()
            // The image must remain visible behind the entire status row, including its bottom edge.
            for (y in pixels.height - 24 until pixels.height) {
                assertEquals(pageColor, pixels[8, y])
            }
            System.getenv("READER_STATUS_PREVIEW_DIR")?.let { directory ->
                Image.makeFromBitmap(screenshot.asSkiaBitmap()).use { image ->
                    image.encodeToData(EncodedImageFormat.PNG)!!.use { png ->
                        File(directory).mkdirs()
                        File(directory, "manga-status-overlay.png").writeBytes(png.bytes)
                    }
                }
            }
        } finally { page.delete() }
    }

    @Test fun lightPaperAndDarkFooters() = runAniComposeUiTest {
        setContent {
            MaterialTheme {
                Column(Modifier.width(380.dp).testTag("status-preview")) {
                    for ((name, colors) in listOf(
                        "浅色阅读" to (Color(0xfffafafa) to Color(0xff222222)),
                        "纸色阅读" to (Color(0xffeee2c6) to Color(0xff332d22)),
                        "夜间阅读" to (Color(0xff101014) to Color(0xffcecece)),
                    )) {
                        Column(Modifier.background(colors.first)) {
                            Text(name, Modifier.padding(16.dp), color = colors.second)
                            Spacer(Modifier.height(20.dp))
                            ReaderStatusContent("23:05", 67, colors.first, colors.second)
                        }
                    }
                }
            }
        }
        assertEquals(3, onAllNodesWithTag("reader-status-footer").fetchSemanticsNodes().size)
        System.getenv("READER_STATUS_PREVIEW_DIR")?.let { directory ->
            val bitmap = onNodeWithTag("status-preview").captureToImage().asSkiaBitmap()
            Image.makeFromBitmap(bitmap).use { image ->
                image.encodeToData(EncodedImageFormat.PNG)!!.use { png ->
                    File(directory).mkdirs()
                    File(directory, "reader-status-themes.png").writeBytes(png.bytes)
                }
            }
        }
    }
}
