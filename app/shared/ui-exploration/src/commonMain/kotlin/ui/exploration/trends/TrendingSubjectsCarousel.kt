/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(TestOnly::class)

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
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import me.him188.ani.app.data.models.trending.TrendingSubjectInfo
import me.him188.ani.app.ui.external.placeholder.placeholder
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.layout.CarouselAutoAdvanceEffect
import me.him188.ani.app.ui.foundation.layout.CarouselItem
import me.him188.ani.app.ui.foundation.layout.CarouselItemDefaults
import me.him188.ani.app.ui.foundation.layout.minimumHairlineSize
import me.him188.ani.app.ui.foundation.preview.PreviewSizeClasses
import me.him188.ani.app.ui.search.LoadErrorCard
import me.him188.ani.app.ui.search.isLoadingFirstPageOrRefreshing
import me.him188.ani.app.ui.search.rememberLoadErrorState
import me.him188.ani.app.ui.search.rememberTestLazyPagingItems
import me.him188.ani.utils.platform.annotations.TestOnly

@Composable
fun TrendingSubjectsCarousel(
    items: LazyPagingItems<TrendingSubjectInfo>,
    onClick: (TrendingSubjectInfo) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    itemSpacing: Dp = 8.dp,
    modifier: Modifier = Modifier,
    carouselState: CarouselState = rememberCarouselState(initialItem = 0) {
        items.itemCount
    }
) {
    TrendingSubjectsCarouselContent(
        itemCount = items.itemCount,
        itemAt = { index ->
            if (items.isLoadingFirstPageOrRefreshing) null else items[index]?.let {
                TrendingCarouselEntry(it.bangumiId, it.nameCn, it.imageLarge)
            }
        },
        onClick = { entry -> items.itemSnapshotList.items.firstOrNull { it.bangumiId == entry.id }?.let(onClick) },
        contentPadding = contentPadding, itemSpacing = itemSpacing, modifier = modifier,
        carouselState = carouselState,
        itemModifier = { loading -> Modifier.placeholder(loading, shape = rememberMaskShape(CarouselItemDefaults.shape)) },
        autoAdvance = { enabled -> CarouselAutoAdvanceEffect(enabled, carouselState) },
        overlay = {
            if (items.loadState.hasError) {
                Box(Modifier.height(CarouselItemDefaults.itemSize().imageHeight).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Box(Modifier.minimumHairlineSize()) {
                        val problem by items.rememberLoadErrorState()
                        LoadErrorCard(problem, onRetry = { items.refresh() })
                    }
                }
            }
        },
    )
}


@TestOnly
val TestTrendingSubjectInfos
    get() = listOf(
        TrendingSubjectInfo(
            bangumiId = 467461,
            nameCn = "胆大党",
            imageLarge = "https://lain.bgm.tv/pic/cover/l/44/7d/467461_HHw4K.jpg",
        ),
        TrendingSubjectInfo(
            bangumiId = 425998,
            nameCn = "Re：从零开始的异世界生活 第三季 袭击篇",
            imageLarge = "https://lain.bgm.tv/pic/cover/l/26/d6/425998_dnzr8.jpg",
        ),
        TrendingSubjectInfo(
            bangumiId = 389156,
            nameCn = "地。 ―关于地球的运动―",
            imageLarge = "https://lain.bgm.tv/pic/cover/l/5f/84/389156_J4gqQ.jpg",
        ),
        TrendingSubjectInfo(
            bangumiId = 464376,
            nameCn = "败犬女主太多了！",
            imageLarge = "https://lain.bgm.tv/pic/cover/l/e4/dc/464376_NsZRw.jpg",
        ),
    )

@Composable
@PreviewSizeClasses
@PreviewLightDark
private fun PreviewTrendingSubjectsCarousel() = ProvideCompositionLocalsForPreview {
    PreviewTrendingSubjectsCarouselImpl()
}

@Composable
private fun PreviewTrendingSubjectsCarouselImpl() {
    TrendingSubjectsCarousel(
        items = rememberTestLazyPagingItems(TestTrendingSubjectInfos),
        {},
    )
}
