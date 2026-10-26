/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.reading

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderStatusPreviewTest {
    @Test fun lightPaperAndDarkFooters() = runAniComposeUiTest {
        setContent {
            MaterialTheme {
                Column(Modifier.width(380.dp).testTag("status-preview")) {
                    for ((name, colors) in listOf(
                        "浅色阅读" to (Color(0xfffafafa) to Color(0xff222222)),
                        "纸色阅读" to (Color(0xffeee2c6) to Color(0xff332d22)),
                        "夜间阅读 / 漫画" to (Color(0xff101014) to Color(0xffcecece)),
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
