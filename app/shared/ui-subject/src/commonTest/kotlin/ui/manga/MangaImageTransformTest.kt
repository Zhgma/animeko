/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.manga

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals

class MangaImageTransformTest {
    private val size = IntSize(400, 600)
    private val center = Offset(200f, 300f)
    private fun assertPoint(expected: Offset, actual: Offset) {
        assertEquals(expected.x, actual.x, 0.001f)
        assertEquals(expected.y, actual.y, 0.001f)
    }
    private fun MangaImageTransform.screen(point: Offset) = center + (point - center) * scale + translation

    @Test fun offCenterDoubleTapKeepsTappedContentInPlace() {
        for (anchor in listOf(Offset(40f, 60f), Offset(320f, 450f), Offset.Zero, Offset(400f, 600f))) {
            assertPoint(anchor, MangaImageTransform().transform(size, anchor, 2.5f).screen(anchor))
        }
    }

    @Test fun movingPinchKeepsInitialImagePointUnderMovingMidpoint() {
        val initial = Offset(130f, 210f)
        var midpoint = initial
        var transform = MangaImageTransform()
        repeat(10) {
            val pan = Offset(2f, 3f)
            transform = transform.transform(size, midpoint, 1.07f, pan)
            midpoint += pan
            assertPoint(midpoint, transform.screen(initial))
        }
    }

    @Test fun subsequentPinchUsesExistingPanAndActualClampedScale() {
        val initial = MangaImageTransform(4f, Offset(90f, -80f))
        val anchor = Offset(260f, 240f)
        val contentPoint = center + (anchor - center - initial.translation) / initial.scale
        val result = initial.transform(size, anchor, 2f)
        assertEquals(5f, result.scale)
        assertPoint(anchor, result.screen(contentPoint))
        assertPoint(anchor, result.transform(size, anchor, 0.6f).screen(contentPoint))
    }

    @Test fun scaleOneResetsAndPanRemainsWithinPageBounds() {
        assertEquals(MangaImageTransform(), MangaImageTransform(2f, Offset(10f, 10f)).transform(size, center, 0.1f))
        val result = MangaImageTransform(2f).transform(size, Offset.Unspecified, 1f, Offset(1000f, -1000f))
        assertPoint(Offset(200f, -300f), result.translation)
    }
}
