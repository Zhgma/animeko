/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.manga

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import me.him188.ani.app.data.models.subject.RatingInfo
import me.him188.ani.app.data.repository.manga.MangaCollection
import me.him188.ani.app.data.repository.manga.MangaCollectionType
import me.him188.ani.app.data.repository.manga.MangaDetails
import me.him188.ani.app.data.repository.manga.MangaLibraryEntry
import me.him188.ani.app.ui.foundation.IMAGE_VIEWER_TEST_TAG
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.subject.details.components.SUBJECT_COVER_IMAGE_TEST_TAG
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/** Offline renders and cover interaction at the shared detail breakpoints. */
@OptIn(TestOnly::class, ExperimentalTestApi::class)
class MangaDetailsPresentationTest {
    private fun check(width: Int, height: Int) {
        val out = File("build/screenshots/manga-details").also { it.mkdirs() }
        val cover = File(out, "fixture-cover.png")
        val bitmap = BufferedImage(283, 400, BufferedImage.TYPE_INT_RGB)
        bitmap.createGraphics().apply {
            color = Color(30, 85, 150); fillRect(0, 0, 283, 400)
            color = Color.WHITE; drawString("MANGA", 105, 185); dispose()
        }
        ImageIO.write(bitmap, "png", cover)
        val url = cover.toURI().toString()
        runSkikoComposeUiTest(Size(width.toFloat(), height.toFloat()), density = Density(1f)) {
            setContent {
                ProvideCompositionLocalsForPreview {
                    CompositionLocalProvider(LocalDensity provides Density(1f)) {
                        MangaDetailsContent(
                            MangaCollection(1, "碧蓝之海", "ぐらんぶる", url, 100, 24, 61, 0, 8, false),
                            MangaDetailsUiState(loading = false, libraryLoaded = true,
                                library = MangaLibraryEntry(MangaCollectionType.DOING, 61, 12),
                                details = MangaDetails("碧蓝之海", "ぐらんぶる", url, "大学生活与潜水社团的青春喜剧。",
                                    RatingInfo.Empty, listOf("作者" to "井上坚二 / 吉冈公威", "出版社" to "讲谈社"),
                                    listOf("搞笑", "青春"), mapOf("collect" to 1200, "doing" to 600), emptyList())),
                            "碧蓝之海", {}, {}, {}, { _, _ -> }, {}, {},
                        )
                    }
                }
            }
            waitForIdle()
            onNodeWithTag("manga-collection-edit").assertIsDisplayed().onChildren()[0].assertHasClickAction()
            onNodeWithTag("manga-start-reading").assertIsDisplayed().assertHasClickAction()
            if (width < 600) {
                val collection = onNodeWithTag("manga-collection-edit").fetchSemanticsNode().boundsInRoot
                assertTrue(collection.width < width / 2f, "Compact collection button must not fill the header")
            } else {
                val sidebar = onNodeWithTag("manga-detail-sidebar").fetchSemanticsNode().boundsInRoot
                val button = onNodeWithTag("manga-collection-edit").fetchSemanticsNode().boundsInRoot
                assertTrue(abs(sidebar.width - button.width) < 1f)
            }
            val png = Image.makeFromBitmap(captureToImage().asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes
            File(out, "details-$width.png").writeBytes(png)
            onNodeWithTag(SUBJECT_COVER_IMAGE_TEST_TAG).performClick()
            onNodeWithTag(IMAGE_VIEWER_TEST_TAG).assertIsDisplayed()
        }
    }

    @Test fun compactCoverAndActions() = check(380, 900)
    @Test fun mediumCoverAndActions() = check(1000, 900)
    @Test fun expandedCoverAndActions() = check(1700, 1100)
}
