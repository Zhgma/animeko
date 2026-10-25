/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.novel

import androidx.compose.ui.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import com.github.panpf.sketch.PlatformContext
import com.github.panpf.sketch.Sketch
import me.him188.ani.app.data.repository.novel.NovelBlock
import me.him188.ani.app.data.repository.novel.NovelReaderSettings
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.LocalSketch
import me.him188.ani.app.ui.manga.mangaImageFileUri
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import kotlin.test.Test

@OptIn(TestOnly::class, ExperimentalTestApi::class)
class NovelIllustrationRenderingTest {
    @Test fun phoneRendersDownloadedIllustration() = render(380, 900)
    @Test fun tabletRendersDownloadedIllustration() = render(1200, 800)

    private fun render(width: Int, height: Int) {
        val path = System.getenv("NOVEL_ILLUSTRATION_FIXTURE")
        assumeTrue(path != null, "Set NOVEL_ILLUSTRATION_FIXTURE to a downloaded illustration")
        val sketch = Sketch(PlatformContext.INSTANCE)
        try {
        runSkikoComposeUiTest(Size(width.toFloat(), height.toFloat()), density = Density(1f)) {
            setContent { ProvideCompositionLocalsForPreview {
                CompositionLocalProvider(LocalSketch provides sketch) {
                NovelReader(listOf(NovelBlock(image = mangaImageFileUri(File(path!!).absolutePath)), NovelBlock("插图之后")),
                    0, 0, "第十四卷 · 插图", NovelReaderSettings(), {}, { _, _ -> }, {}, {}, {}, {},
                    null, null, true, {}, Modifier)
                }
            } }
            // Decode and paint must succeed; an empty placeholder has no image color variation.
            waitUntil(timeoutMillis = 15_000) {
                val pixels = onNodeWithTag("novel-illustration-0").captureToImage().toPixelMap()
                val colors = mutableSetOf<Int>()
                for (y in 0 until pixels.height step 5) for (x in 0 until pixels.width step 5) colors += pixels[x, y].hashCode()
                colors.size > 64
            }
            onNodeWithContentDescription("插图").assertIsDisplayed()
            val file = File("build/screenshots/novel-illustrations/$width.png")
            file.parentFile.mkdirs()
            val bitmap = onRoot().captureToImage().asSkiaBitmap()
            file.writeBytes(Image.makeFromBitmap(bitmap).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
        } finally { sketch.shutdown() }
    }
}
