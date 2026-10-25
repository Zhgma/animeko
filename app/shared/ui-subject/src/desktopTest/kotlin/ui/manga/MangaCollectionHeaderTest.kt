/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.manga

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import me.him188.ani.app.data.repository.manga.BangumiBookKind
import me.him188.ani.app.data.repository.manga.MangaCollectionType
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.user.TestSelfInfoUiState
import me.him188.ani.utils.platform.Arch
import me.him188.ani.utils.platform.Platform
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(TestOnly::class, ExperimentalTestApi::class)
class MangaCollectionHeaderTest {
    private fun check(width: Int, height: Int, kind: BangumiBookKind) =
        runSkikoComposeUiTest(Size(width.toFloat(), height.toFloat()), density = Density(1f)) {
            var usingCache by mutableStateOf(false)
            var loading by mutableStateOf(false)
            var failed by mutableStateOf(false)
            setContent {
                ProvideCompositionLocalsForPreview {
                    CompositionLocalProvider(
                        LocalDensity provides Density(1f),
                        LocalPlatform provides Platform.Android(Arch.AARCH64),
                    ) {
                        MangaCollectionContent(
                            state = MangaCollectionUiState(pages = MangaCollectionType.entries.associateWith {
                                MangaCollectionPageState(loading = loading, failed = failed, count = 3)
                            }),
                            selfInfo = TestSelfInfoUiState,
                            title = { TrackingCollectionTabs(if (kind == BangumiBookKind.NOVEL) 2 else 1) {} },
                            onSelect = {}, onRefresh = {}, onClickLogin = {}, onOpen = {},
                            kind = kind, usingBangumiCache = usingCache,
                        )
                    }
                }
            }
            val tags = listOf("tracking-anime", "tracking-manga", "tracking-novel") +
                MangaCollectionType.entries.map { "manga-status-${it.name}" }
            fun bounds() = tags.associateWith { onNodeWithTag(it).fetchSemanticsNode().boundsInRoot }
            waitForIdle()
            val baseline = bounds()
            // Network state changes may change the list, but neither navigation row may move.
            for ((cache, refreshing, error) in listOf(
                Triple(true, false, false), Triple(true, true, false),
                Triple(true, false, true), Triple(false, false, false),
            )) {
                runOnIdle { usingCache = cache; loading = refreshing; failed = error }
                waitForIdle()
                assertEquals(baseline, bounds(), "Header moved at $width x $height / $kind / $cache $refreshing $error")
                onNodeWithText("搜索").assertDoesNotExist()
                onNodeWithText("Search").assertDoesNotExist()
                val notice = onNodeWithTag("manga-cache-notice-DOING")
                if (cache) {
                    notice.assertIsDisplayed()
                    assertTrue(notice.fetchSemanticsNode().boundsInRoot.top >= baseline.getValue("manga-status-DOING").bottom)
                } else {
                    notice.assertDoesNotExist()
                }
            }
            runOnIdle { usingCache = true }
            waitForIdle()
            val out = File("build/screenshots/collection-header").also { it.mkdirs() }
            File(out, "$kind-$width.png").writeBytes(
                Image.makeFromBitmap(captureToImage().asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes,
            )
        }

    @Test fun mangaPhone() = check(380, 900, BangumiBookKind.MANGA)
    @Test fun booksPhone() = check(380, 900, BangumiBookKind.NOVEL)
    @Test fun mangaTablet() = check(1200, 800, BangumiBookKind.MANGA)
    @Test fun booksTablet() = check(1200, 800, BangumiBookKind.NOVEL)
    @Test fun mangaLandscape() = check(900, 420, BangumiBookKind.MANGA)
    @Test fun booksLandscape() = check(900, 420, BangumiBookKind.NOVEL)
}
