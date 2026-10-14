/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.manga

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.unit.IntSize

internal val MangaImageScale = SemanticsPropertyKey<Float>("MangaImageScale")
internal val MangaImageTranslation = SemanticsPropertyKey<Offset>("MangaImageTranslation")

/** Translation is measured in viewport pixels; the graphics layer scales around its center. */
@Immutable
internal data class MangaImageTransform(val scale: Float = 1f, val translation: Offset = Offset.Zero) {
    fun transform(viewport: IntSize, centroid: Offset, zoom: Float, pan: Offset = Offset.Zero): MangaImageTransform {
        val newScale = (scale * zoom).coerceIn(1f, 5f)
        if (newScale == 1f) return MangaImageTransform()
        val center = Offset(viewport.width / 2f, viewport.height / 2f)
        val anchor = if (centroid.isSpecified) centroid else center
        val ratio = newScale / scale
        // Keep the image point under the previous gesture centroid under centroid + pan.
        val moved = (translation - (anchor - center)) * ratio + (anchor - center) + pan
        val limitX = viewport.width * (newScale - 1f) / 2f
        val limitY = viewport.height * (newScale - 1f) / 2f
        return MangaImageTransform(newScale, Offset(
            moved.x.coerceIn(-limitX, limitX), moved.y.coerceIn(-limitY, limitY),
        ))
    }
}
