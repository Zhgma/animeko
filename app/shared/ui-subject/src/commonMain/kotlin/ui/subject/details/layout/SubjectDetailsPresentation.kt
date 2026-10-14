/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.subject.details.layout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kmpalette.color
import com.kmpalette.palette.graphics.Palette
import kotlinx.collections.immutable.toImmutableList
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.models.subject.preferredDisplayName
import me.him188.ani.app.tools.ColorUtils
import me.him188.ani.app.ui.foundation.AniImageLoadSuccess
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.foundation.LocalSubjectAppearanceSettings
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.subject.details.components.AnimatedGradientBackground
import me.him188.ani.app.ui.subject.details.components.COVER_WIDTH_TO_HEIGHT_RATIO
import me.him188.ani.app.ui.subject.details.components.SUBJECT_COVER_IMAGE_TEST_TAG

@Composable
internal fun SubjectDetailsCover(
    info: SubjectInfo,
    modifier: Modifier = Modifier,
    onCoverImageSuccess: (AniImageLoadSuccess) -> Unit = {},
    onClickCover: (() -> Unit)? = null,
) {
    AsyncImage(info.imageLarge, null,
        modifier.fillMaxWidth().aspectRatio(COVER_WIDTH_TO_HEIGHT_RATIO).clip(RoundedCornerShape(16.dp))
            .ifThen(onClickCover != null) { clickable(onClick = checkNotNull(onClickCover)) }
            .testTag(SUBJECT_COVER_IMAGE_TEST_TAG),
        contentScale = ContentScale.Crop, onSuccess = onCoverImageSuccess)
}

@Composable
internal fun SubjectDetailsTitleBlock(info: SubjectInfo, metadata: @Composable () -> Unit) {
    val useOriginalTitle = LocalSubjectAppearanceSettings.current.useOriginalTitle
    val primaryTitle = info.preferredDisplayName(useOriginalTitle)
    val secondaryTitle = if (useOriginalTitle) info.displayName else info.name
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(primaryTitle, style = MaterialTheme.typography.headlineSmall, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
                if (secondaryTitle.isNotBlank() && secondaryTitle != primaryTitle) {
                    Text(secondaryTitle, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
            }
        }
        metadata()
    }
}

@Composable
internal fun SubjectDetailsGradientBackground(palette: Palette?) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val colors = remember(palette, surfaceColor) {
        palette?.swatches?.map { ColorUtils.blendColor(it.color, surfaceColor, 0.85f) }?.toImmutableList()
    }
    if (colors != null) AnimatedGradientBackground(colors, speed = 0.05, modifier = Modifier.fillMaxSize())
}
