/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.manga

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.semantics.SemanticsProperties
import me.him188.ani.app.data.repository.manga.MangaCollection
import me.him188.ani.app.data.repository.manga.MangaChapter
import me.him188.ani.app.data.repository.manga.MangaPage
import me.him188.ani.app.data.repository.manga.MangaDetails
import me.him188.ani.app.data.models.subject.RatingInfo
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.utils.platform.annotations.TestOnly
import me.him188.ani.utils.io.SystemPaths
import me.him188.ani.utils.io.createTempFile
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.writeBytes
import me.him188.ani.utils.io.delete
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(TestOnly::class)
class MangaReaderTest {
    @Test
    fun fullscreenSideTapAdvancesSpreadAndCenterTogglesControls() = runAniComposeUiTest {
        var page = -1
        var nextChapter = 0
        setContent {
            ProvideCompositionLocalsForPreview {
                MangaReaderPages(List(5) { MangaPage("page-$it", "", width = 400, height = 600) }, 0, { page = it },
                    { "fixture" }, null, { nextChapter++ }, Modifier.requiredSize(700.dp, 450.dp),
                    fullscreen = true, initialFirstPageSingle = true)
            }
        }
        onNodeWithTag("manga-reader-controls").assertDoesNotExist()
        onNodeWithTag("manga-page-viewport-1").performTouchInput { doubleClick() }
        mainClock.advanceTimeBy(400)
        runOnIdle { assertEquals(0, page) }
        onNodeWithTag("manga-page-viewport-1").performTouchInput { click(Offset(width * 0.1f, height * 0.25f)) }
        mainClock.advanceTimeBy(1000)
        runOnIdle { assertEquals(1, page) }
        onNodeWithTag("manga-reader-controls").assertDoesNotExist()
        onNodeWithTag("manga-page-viewport-2").performTouchInput { click(Offset(width * 0.1f, height * 0.25f)) }
        mainClock.advanceTimeBy(400)
        onNodeWithTag("manga-reader-page").assertTextEquals("2–3 / 5")
        onNodeWithTag("manga-page-viewport-2").performTouchInput { click(Offset(width * 0.1f, height * 0.25f)) }
        mainClock.advanceTimeBy(400)
        onNodeWithTag("manga-page-viewport-2").performTouchInput { click(Offset(width * 0.9f, height * 0.25f)) }
        mainClock.advanceTimeBy(1000)
        runOnIdle { assertEquals(3, page); assertEquals(0, nextChapter) }
        onNodeWithTag("manga-page-viewport-4").performTouchInput { click() }
        mainClock.advanceTimeBy(1000)
        runOnIdle { assertEquals(3, page); assertEquals(0, nextChapter) }
    }

    @Test
    fun fullscreenPortraitTapAdvancesOnePage() = runAniComposeUiTest {
        var page = -1
        setContent {
            ProvideCompositionLocalsForPreview {
                MangaReaderPages(List(3) { MangaPage("page-$it", "", width = 400, height = 800) }, 0, { page = it },
                    { "fixture" }, null, null, Modifier.requiredSize(380.dp, 650.dp), fullscreen = true)
            }
        }
        onNodeWithTag("manga-page-viewport-1").performTouchInput { click(Offset(width * 0.1f, height * 0.25f)) }
        mainClock.advanceTimeBy(1000)
        runOnIdle { assertEquals(1, page) }
        onNodeWithTag("manga-reader-controls").assertDoesNotExist()
    }

    @Test
    fun fullscreenLandscapeTallPagesStillHaveChapterEndGap() = runAniComposeUiTest {
        var nextChapter = 0
        setContent {
            ProvideCompositionLocalsForPreview {
                MangaReaderPages(List(2) { MangaPage("tall-$it", "", width = 500, height = 750) }, 0, {},
                    { error("Test image unavailable") }, null, { nextChapter++ },
                    Modifier.requiredSize(700.dp, 450.dp), fullscreen = true)
            }
        }
        onNodeWithTag("manga-reader-scroll").performTouchInput { swipeUp() }
        mainClock.advanceTimeBy(1000)
        val gap = onNodeWithTag("manga-reader-bottom-gap").fetchSemanticsNode().boundsInRoot
        assertTrue(gap.height > 0)
        val image = onNodeWithTag("manga-reader-page-slot-1").fetchSemanticsNode().boundsInRoot
        assertTrue(gap.top >= image.bottom)
        onNodeWithTag("manga-reader-bottom-gap").performTouchInput { click() }
        runOnIdle { assertEquals(1, nextChapter) }
        onNodeWithTag("manga-reader-controls").assertDoesNotExist()
    }

    @Test
    fun bottomGapOpensNextChapterOnlyInFullscreenWithoutTogglingControls() = runAniComposeUiTest {
        var fullscreen by mutableStateOf(false)
        var hasNext by mutableStateOf(true)
        var nextChapter = 0
        setContent {
            ProvideCompositionLocalsForPreview {
                MangaReaderPages(listOf(MangaPage("wide-page", "", width = 1000, height = 200)), 0, {},
                    { error("Test image unavailable") }, null, if (hasNext) ({ nextChapter++ }) else null,
                    Modifier.requiredSize(700.dp, 450.dp), fullscreen = fullscreen)
            }
        }
        val originalGap = onNodeWithTag("manga-reader-bottom-gap").fetchSemanticsNode().boundsInRoot
        assertTrue(originalGap.height > 0)
        onNodeWithTag("manga-reader-bottom-gap").performTouchInput { click() }
        runOnIdle { assertEquals(0, nextChapter); fullscreen = true }
        assertEquals(originalGap, onNodeWithTag("manga-reader-bottom-gap").fetchSemanticsNode().boundsInRoot)
        onNodeWithTag("manga-reader-bottom-gap").performTouchInput { click() }
        runOnIdle { assertEquals(1, nextChapter) }
        onNodeWithTag("manga-reader-controls").assertDoesNotExist()
        onNodeWithTag("manga-reader-bottom-gap").performTouchInput { swipeUp() }
        runOnIdle { assertEquals(1, nextChapter); hasNext = false }
        onNodeWithTag("manga-reader-bottom-gap").performTouchInput { click() }
        runOnIdle { assertEquals(1, nextChapter) }
        onNodeWithTag("manga-reader-controls").assertExists()
    }

    @Test
    fun portraitDetailsCollapseReaderAndExpandOnlyAfterReturningToTop() = runAniComposeUiTest {
        val chapter = MangaChapter("one", "第 1 话", 1.0, "章节")
        var fullscreen by mutableStateOf(false)
        setContent {
            ProvideCompositionLocalsForPreview {
                MangaReaderScreen(MangaDetailsUiState(loading = false, chapter = chapter,
                    chapters = listOf(chapter), pages = List(20) { MangaPage("page-$it", "") },
                    details = MangaDetails("Test manga", "Original", "", "Summary", RatingInfo.Empty,
                        List(40) { "Field $it" to "Value $it" }, emptyList(), emptyMap(), emptyList())),
                    {}, {}, { _, _ -> }, { error("Test image unavailable") }, { _, _ -> },
                    Modifier.requiredSize(380.dp, 800.dp), fullscreen = fullscreen, onFullscreen = { fullscreen = it })
            }
        }
        fun readerHeight() = onNodeWithTag("manga-reading-pane").fetchSemanticsNode().boundsInRoot.height
        val initialHeight = readerHeight()
        val screenHeight = onNodeWithTag("manga-reader").fetchSemanticsNode().boundsInRoot.height
        assertEquals(screenHeight * 0.7f, initialHeight, 1f)
        onNodeWithTag("manga-reader-scroll").performTouchInput { swipeUp(durationMillis = 1000) }
        assertEquals(initialHeight, readerHeight())
        onNodeWithTag("manga-reader-details-scroll").performTouchInput { swipeUp(durationMillis = 1000) }
        val partialHeight = readerHeight()
        assertTrue(partialHeight in 1f..<initialHeight)
        repeat(4) { onNodeWithTag("manga-reader-details-scroll").performTouchInput { swipeUp(durationMillis = 1000) } }
        assertEquals(0f, readerHeight(), 1f)
        // A short reverse drag within the long information list must scroll details first.
        onNodeWithTag("manga-reader-details-scroll").performTouchInput {
            swipeDown(startY = centerY, endY = centerY + 40, durationMillis = 1000)
        }
        assertEquals(0f, readerHeight(), 1f)
        repeat(12) { onNodeWithTag("manga-reader-details-scroll").performTouchInput { swipeDown(durationMillis = 1000) } }
        assertEquals(initialHeight, readerHeight(), 1f)
        onNodeWithTag("manga-reader-details-scroll").performTouchInput { swipeUp(durationMillis = 1000) }
        val beforeFullscreen = readerHeight()
        runOnIdle { fullscreen = true }
        assertEquals(screenHeight, readerHeight(), 1f)
        runOnIdle { fullscreen = false }
        assertEquals(beforeFullscreen, readerHeight(), 1f)
        onNodeWithTag("manga-reader-comments-tab").performClick()
        repeat(4) { onNodeWithTag("manga-reader-details-scroll").performTouchInput { swipeDown(durationMillis = 1000) } }
        assertEquals(initialHeight, readerHeight(), 1f)
    }

    @Test
    fun localPageFileDecodesThroughSharedImageLoader() = runAniComposeUiTest {
        val file = SystemPaths.createTempFile("manga 中文 test", ".png")
        file.writeBytes(Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+ip1sAAAAASUVORK5CYII="))
        try {
            setContent {
                ProvideCompositionLocalsForPreview {
                    MangaReaderPages(listOf(MangaPage("test", "")), 0, {}, { mangaImageFileUri(file.absolutePath) }, null, null, Modifier.requiredSize(380.dp, 650.dp))
                }
            }
            waitUntil(timeoutMillis = 10_000) {
                onAllNodesWithTag("manga-page-image-1").fetchSemanticsNodes().any {
                    it.config[SemanticsProperties.StateDescription] == "decoded"
                }
            }
            onNodeWithTag("manga-page-image-1").performTouchInput { click() }
            mainClock.advanceTimeBy(400)
            onNodeWithTag("manga-next-page").assertIsNotEnabled()
        } finally { file.delete() }
    }

    @Test
    fun readerRestoresPageAndKeepsChapterActionsSeparate() = runAniComposeUiTest {
        var remembered = -1
        var nextChapter = 0
        setContent {
            ProvideCompositionLocalsForPreview {
                MangaReaderPages(List(3) { MangaPage("page-$it", "") }, initialPage = 1,
                    onPage = { remembered = it }, loadImage = { error("Test image unavailable") },
                    onPreviousChapter = null, onNextChapter = { nextChapter++ }, modifier = Modifier.requiredSize(380.dp, 650.dp))
            }
        }
        onNodeWithTag("manga-reader-scroll").performClick()
        onNodeWithTag("manga-reader-page").assertTextEquals("2 / 3")
        onNodeWithTag("manga-previous-chapter").assertIsNotEnabled()
        onNodeWithTag("manga-next-page").performClick()
        waitForIdle()
        onNodeWithTag("manga-reader-page").assertTextEquals("3 / 3")
        onNodeWithTag("manga-next-page").assertIsNotEnabled()
        onNodeWithTag("manga-reader-scroll").performTouchInput { swipeDown(startY = centerY, endY = bottom - 5, durationMillis = 600) }
        waitForIdle()
        onNodeWithTag("manga-reader-page").assertTextEquals("2 / 3")
        onNodeWithTag("manga-next-chapter").performClick()
        runOnIdle { assertEquals(1, remembered); assertEquals(1, nextChapter) }
    }

    @Test
    fun spreadsKeepEveryPageAndSupportCoverOffset() {
        assertEquals(listOf(listOf(0, 1), listOf(2, 3), listOf(4)), mangaReaderRows(5, true, false))
        assertEquals(listOf(listOf(0), listOf(1, 2), listOf(3, 4)), mangaReaderRows(5, true, true))
        assertEquals((0..4).toList(), mangaReaderRows(5, true, true).flatten())
        assertEquals(List(5) { listOf(it) }, mangaReaderRows(5, false, true))
        assertEquals(emptyList(), mangaReaderRows(0, true, true))
    }

    @Test
    fun landscapeReadingOrderOffsetAndResizePreservePosition() = runAniComposeUiTest {
        var landscape by mutableStateOf(true)
        var savedLayout = true to false
        setContent {
            ProvideCompositionLocalsForPreview {
                MangaReaderPages(List(5) { MangaPage("page-$it", "") }, 0, {}, { error("Test image unavailable") }, null, null,
                    Modifier.requiredSize(if (landscape) 700.dp else 380.dp, if (landscape) 450.dp else 650.dp),
                    onReaderLayout = { right, offset -> savedLayout = right to offset })
            }
        }
        onNodeWithTag("manga-reader-scroll").performClick()
        onNodeWithTag("manga-reader-page").assertTextEquals("1–2 / 5")
        assertTrue(onNodeWithTag("manga-reader-page-slot-2").fetchSemanticsNode().boundsInRoot.right <=
            onNodeWithTag("manga-reader-page-slot-1").fetchSemanticsNode().boundsInRoot.left)
        onNodeWithTag("manga-reader-settings").performClick()
        onNodeWithTag("manga-spread-offset").performClick()
        onNodeWithTag("manga-reader-settings-close").performClick()
        onNodeWithTag("manga-reader-page").assertTextEquals("1 / 5")
        fun assertFirstPageFillsRow() {
            val row = onNodeWithTag("manga-reader-row-0").fetchSemanticsNode().boundsInRoot
            val page = onNodeWithTag("manga-reader-page-slot-1").fetchSemanticsNode().boundsInRoot
            assertEquals(row.left, page.left, 1f)
            assertEquals(row.right, page.right, 1f)
        }
        assertFirstPageFillsRow()
        onNodeWithTag("manga-next-page").performClick()
        onNodeWithTag("manga-reader-page").assertTextEquals("2–3 / 5")
        assertEquals(onNodeWithTag("manga-reader-row-1").fetchSemanticsNode().boundsInRoot.width / 2,
            onNodeWithTag("manga-reader-page-slot-2").fetchSemanticsNode().boundsInRoot.width, 1f)
        onNodeWithTag("manga-reader-settings").performClick()
        onNodeWithTag("manga-reading-order").performClick()
        onNodeWithTag("manga-reading-order").assertTextEquals("先左后右")
        onNodeWithTag("manga-reader-settings-close").performClick()
        assertTrue(onNodeWithTag("manga-reader-page-slot-2").fetchSemanticsNode().boundsInRoot.right <=
            onNodeWithTag("manga-reader-page-slot-3").fetchSemanticsNode().boundsInRoot.left)
        onNodeWithTag("manga-previous-page").performClick()
        assertFirstPageFillsRow()
        onNodeWithTag("manga-next-page").performClick()
        runOnIdle { assertEquals(false to true, savedLayout); landscape = false }
        onNodeWithTag("manga-reader-page").assertTextEquals("2 / 5")
        onNodeWithTag("manga-spread-offset").assertDoesNotExist()
    }

    @Test
    fun controlsAreTapToRevealAndAutoHideWithoutChangingReaderBounds() = runAniComposeUiTest {
        setContent {
            ProvideCompositionLocalsForPreview {
                MangaReaderPages(List(3) { MangaPage("page-$it", "") }, 0, {},
                    { error("Test image unavailable") }, null, null, Modifier.requiredSize(380.dp, 650.dp))
            }
        }
        onNodeWithTag("manga-reader-controls").assertDoesNotExist()
        val bounds = onNodeWithTag("manga-reader-scroll").fetchSemanticsNode().boundsInRoot
        onNodeWithTag("manga-reader-scroll").performClick()
        onNodeWithTag("manga-reader-slider").assertExists()
        assertEquals(bounds, onNodeWithTag("manga-reader-scroll").fetchSemanticsNode().boundsInRoot)
        mainClock.advanceTimeBy(5500)
        onNodeWithTag("manga-reader-controls").assertDoesNotExist()
        onNodeWithTag("manga-reader-scroll").performClick()
        onNodeWithTag("manga-reader-controls").assertExists()
        onNodeWithTag("manga-reader-scroll").performClick()
        onNodeWithTag("manga-reader-controls").assertDoesNotExist()
    }

    @Test
    fun videoLayoutRetainsDetailsCommentsAndReadingPositionAcrossFullscreen() = runAniComposeUiTest {
        var fullscreen by mutableStateOf(false)
        var wide by mutableStateOf(true)
        val chapter = MangaChapter("one", "第 1 话", 1.0, "章节")
        setContent {
            ProvideCompositionLocalsForPreview {
                MangaReaderScreen(MangaDetailsUiState(loading = false, chapter = chapter,
                    chapters = listOf(chapter), pages = List(6) { MangaPage("page-$it", "") }, initialPage = 2),
                    {}, {}, { _, _ -> }, { error("Test image unavailable") }, { _, _ -> },
                    Modifier.requiredSize(if (wide) 1000.dp else 380.dp, 650.dp),
                    fullscreen = fullscreen, onFullscreen = { fullscreen = it })
            }
        }
        onNodeWithTag("manga-player-layout-WIDE").assertExists()
        onNodeWithTag("manga-reader-comments-tab").performClick()
        assertTrue(onAllNodesWithText("暂无评论").fetchSemanticsNodes().isNotEmpty())
        onNodeWithTag("manga-reader-scroll").performClick()
        onNodeWithTag("manga-reader-fullscreen").performClick()
        onNodeWithTag("manga-reader-details").assertDoesNotExist()
        onNodeWithTag("manga-player-layout-VIDEO_ONLY").assertExists()
        onNodeWithTag("manga-reader-controls").assertDoesNotExist()
        onNodeWithTag("manga-reader-scroll").performClick()
        onNodeWithTag("manga-reader-page").assertTextEquals("3–4 / 6")
        onNodeWithTag("manga-reader-fullscreen").performClick()
        onNodeWithTag("manga-reader-details").assertExists()
        runOnIdle { wide = false }
        onNodeWithTag("manga-player-layout-COMPACT").assertExists()
        val reader = onNodeWithTag("manga-reading-pane").fetchSemanticsNode().boundsInRoot
        val details = onNodeWithTag("manga-reader-details").fetchSemanticsNode().boundsInRoot
        assertTrue(reader.bottom <= details.top)
        onNodeWithTag("manga-reader-controls").assertDoesNotExist()
    }

    @Test
    fun detailsHaveInternalChaptersCommentsAndBack() = runAniComposeUiTest {
        var backCount = 0
        var sourceCount = 0
        var ratingCount = 0
        var chaptersCount = 0
        setContent {
            ProvideCompositionLocalsForPreview {
                CompositionLocalProvider(LocalMangaActions provides MangaPageActions(
                    showSources = { sourceCount++ }, writeReview = { ratingCount++ }, showChapters = { chaptersCount++ })) {
                    MangaDetailsContent(MangaCollection(1, "Test manga", "Original", "", 12, 2, 0, 0, 0, false),
                        MangaDetailsUiState(loading = false), "Test manga", onBack = { backCount++ }, onRetry = {},
                        onComments = {}, onSearch = { _, _ -> }, onBook = {}, onChapter = {},
                        modifier = Modifier.requiredSize(380.dp, 800.dp))
                }
            }
        }
        onNodeWithTag("manga-details-back").performClick()
        assertEquals(1, backCount)
        onNodeWithTag("manga-start-reading").performClick()
        assertEquals(1, sourceCount)
        onNodeWithContentDescription("漫画目录").performClick()
        assertEquals(1, chaptersCount)
        onAllNodesWithText("评价")[0].performClick()
        onNodeWithTag("manga-write-review").performClick()
        assertEquals(1, ratingCount)
    }

    @Test
    fun detailsUseSharedCompactMediumAndExpandedBreakpoints() = runAniComposeUiTest {
        var width by mutableStateOf(380.dp)
        setContent {
            ProvideCompositionLocalsForPreview {
                MangaDetailsContent(MangaCollection(1, "Test manga", "Original", "", 12, 2, 0, 0, 0, false),
                    MangaDetailsUiState(loading = false), "Test manga", {}, {}, {}, { _, _ -> }, {}, {},
                    Modifier.requiredSize(width, 700.dp))
            }
        }
        onNodeWithTag("manga-layout-COMPACT").assertExists()
        onNodeWithTag("manga-detail-sidebar").assertDoesNotExist()
        runOnIdle { width = 800.dp }
        onNodeWithTag("manga-layout-MEDIUM").assertExists()
        val sidebar = onNodeWithTag("manga-detail-sidebar").fetchSemanticsNode().boundsInRoot
        val main = onNodeWithTag("manga-detail-main").fetchSemanticsNode().boundsInRoot
        assertTrue(sidebar.right < main.left)
        onNodeWithTag("manga-detail-rail").assertDoesNotExist()
        runOnIdle { width = 1700.dp }
        onNodeWithTag("manga-layout-EXPANDED").assertExists()
        val expandedMain = onNodeWithTag("manga-detail-main").fetchSemanticsNode().boundsInRoot
        val rail = onNodeWithTag("manga-detail-rail").fetchSemanticsNode().boundsInRoot
        assertTrue(expandedMain.right < rail.left)
    }
}
