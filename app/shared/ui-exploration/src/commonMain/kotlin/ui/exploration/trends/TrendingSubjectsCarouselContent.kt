/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.exploration.trends

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.carousel.CarouselDefaults
import androidx.compose.material3.carousel.CarouselItemScope
import androidx.compose.material3.carousel.CarouselState
import androidx.compose.material3.carousel.HorizontalCenteredHeroCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.foundation.layout.CarouselItem
import me.him188.ani.app.ui.foundation.layout.CarouselItemDefaults


data class TrendingCarouselEntry(val id: Int, val title: String, val image: String)

/** Shared rendering; platform paging, errors and lifecycle stay with the caller. */
@Composable
fun TrendingSubjectsCarouselContent(
    itemCount: Int,
    itemAt: (Int) -> TrendingCarouselEntry?,
    onClick: (TrendingCarouselEntry) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    itemSpacing: Dp = 8.dp,
    modifier: Modifier = Modifier,
    carouselState: CarouselState = rememberCarouselState(initialItem = 0) { itemCount },
    itemModifier: @Composable CarouselItemScope.(Boolean) -> Modifier = { Modifier },
    autoAdvance: @Composable (Boolean) -> Unit = {},
    overlay: @Composable () -> Unit = {},
) {
    val size = CarouselItemDefaults.itemSize()
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    Box(modifier.padding(contentPadding).hoverable(interactionSource)) {
        val content: @Composable CarouselItemScope.(Int) -> Unit = { index ->
            val item = itemAt(index)
            CarouselItem(
                label = { CarouselItemDefaults.Text(item?.title ?: "") },
                itemModifier(item == null),
            ) {
                if (item != null) {
                    Surface({ onClick(item) }) {
                        AsyncImage(
                            item.image,
                            // 必须撑满 item. 只给高度时宽度取决于解码后的 bitmap, 窗口变宽后会留空白且空白处无法点击
                            modifier = Modifier.height(size.imageHeight).fillMaxWidth(),
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                        )
                    }
                } else {
                    Box(Modifier.height(size.imageHeight).fillMaxWidth())
                }
            }
        }

        HorizontalCenteredHeroCarousel(
            carouselState,
            //            preferredItemWidth = size.preferredWidth,
            Modifier.fillMaxWidth(),
            maxItemWidth = 300.dp,
            itemSpacing = itemSpacing,
            flingBehavior = CarouselDefaults.multiBrowseFlingBehavior(
                carouselState,
                snapAnimationSpec = spring(stiffness = Spring.StiffnessMedium),
            ),
            content = content,
        )

        autoAdvance(!isHovered)

        overlay()
    }
}
