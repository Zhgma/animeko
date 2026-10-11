/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import me.him188.ani.app.ui.external.placeholder.placeholder
import me.him188.ani.app.ui.foundation.layout.CarouselItemDefaults

@Composable
fun SubjectCoverCard(
    name: String?,
    image: String?,
    isPlaceholder: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = CarouselItemDefaults.shape,
    imageModifier: Modifier = Modifier,
    brushLayerModifier: Modifier = Modifier,
) {
    SubjectCoverCardContent(name, image, isPlaceholder, onClick,
        modifier.placeholder(isPlaceholder, shape = shape), shape, imageModifier, brushLayerModifier)
}
