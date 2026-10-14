/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.exploration.followed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.foundation.layout.BasicCarouselItem
import me.him188.ani.app.ui.foundation.layout.CarouselItemDefaults
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.isHeightAtLeastMedium
import me.him188.ani.app.ui.foundation.layout.isWidthAtLeastExpanded
import me.him188.ani.app.ui.foundation.layout.isWidthAtLeastMedium
import me.him188.ani.app.ui.foundation.widgets.NsfwMask


@Composable
fun FollowedSubjectItemContent(
    title: String?, imageUrl: String?, hasNewEpisode: Boolean,
    onClick: () -> Unit, onPlay: () -> Unit,
    imageSize: DpSize, shape: Shape, modifier: Modifier = Modifier,
    supportingText: @Composable () -> Unit = {},
) {
    BasicCarouselItem(
        label = { CarouselItemDefaults.Text(title.orEmpty()) },
        modifier,
        supportingText = supportingText,
        maskShape = shape,
        overlay = {
            if (hasNewEpisode) {
                FilledTonalIconButton(
                    onClick = { onPlay() },
                    modifier = Modifier.align(Alignment.BottomEnd),
                ) {
                    Icon(Icons.Rounded.PlayArrow, null, Modifier.size(24.dp))
                }
            }
        },
    ) {
        if (title != null) {
            val image = @Composable {
                AsyncImage(
                    imageUrl,
                    modifier = Modifier.size(imageSize),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                )
            }
            Surface({ onClick() }, content = image)
        } else {
            Box(Modifier.size(imageSize))
        }
    }
}

@Immutable
data class FollowedSubjectsLayoutParameters(
    val imageSize: DpSize,
    val horizontalArrangement: Arrangement.Horizontal,
    val shape: Shape,
)

@Stable
object FollowedSubjectsDefaults {
    @Composable
    fun layoutParameters(windowAdaptiveInfo: WindowAdaptiveInfo = currentWindowAdaptiveInfo1()): FollowedSubjectsLayoutParameters {
        val windowSizeClass = windowAdaptiveInfo.windowSizeClass
        return FollowedSubjectsLayoutParameters(
            imageSize = imageSize(windowAdaptiveInfo),
            horizontalArrangement = when {
                windowSizeClass.isWidthAtLeastExpanded -> Arrangement.spacedBy(16.dp)
                windowSizeClass.isWidthAtLeastMedium -> Arrangement.spacedBy(12.dp)
                else -> Arrangement.spacedBy(8.dp)
            },
            shape = MaterialTheme.shapes.large,
        )
    }

    private fun imageSize(windowAdaptiveInfo: WindowAdaptiveInfo): DpSize {
        val windowSizeClass = windowAdaptiveInfo.windowSizeClass
        val baseSize = when {
            windowSizeClass.isHeightAtLeastMedium && windowSizeClass.isWidthAtLeastExpanded -> 160.dp
            windowSizeClass.isHeightAtLeastMedium && windowSizeClass.isWidthAtLeastMedium -> 140.dp
            else -> 120.dp
        }
        return DpSize(baseSize, (baseSize) / 9 * 16)
    }
}
