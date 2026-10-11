/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.subject
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.foundation.layout.BasicCarouselItem
import me.him188.ani.app.ui.foundation.layout.CarouselItemDefaults

@Composable
fun SubjectCoverCardContent(
    name: String?,
    image: String?,
    isPlaceholder: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = CarouselItemDefaults.shape,
    imageModifier: Modifier = Modifier,
    brushLayerModifier: Modifier = Modifier,
) {
    BasicCarouselItem(
        label = { CarouselItemDefaults.Text(name ?: "", maxLines = 2) },
        modifier,
        maskShape = shape,
        brushLayerModifier = brushLayerModifier,
    ) {
        if (!isPlaceholder) {
            val image = @Composable {
                AsyncImage(
                    image,
                    modifier = imageModifier.aspectRatio(9f / 16).fillMaxWidth(),
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                )
            }

            Surface({ onClick() }, content = image)
        } else {
            Box(Modifier.aspectRatio(9f / 16).fillMaxWidth())
        }
    }
}
