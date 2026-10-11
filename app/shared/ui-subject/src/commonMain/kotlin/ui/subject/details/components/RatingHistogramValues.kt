/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.subject.details.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun RatingHistogramValues(
    counts: List<Int>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    barHeight: Dp = 64.dp,
    barCornerRadius: Dp = 3.dp,
    barSpacing: Dp = 6.dp,
    labelStyle: TextStyle = MaterialTheme.typography.labelSmall,
) {
    val max = remember(counts) { counts.maxOrNull() ?: 0 }
    val shape = remember(barCornerRadius) { RoundedCornerShape(barCornerRadius) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth().height(barHeight),
            horizontalArrangement = Arrangement.spacedBy(barSpacing),
            verticalAlignment = Alignment.Bottom,
        ) {
            for (score in 1..10) {
                val count = counts.getOrElse(score - 1) { 0 }
                // 归一化到 [MIN_VISIBLE_FRACTION, 1]; 有人评分时至少留一点可见高度.
                val hasVotes = max > 0 && count > 0
                val fraction = if (hasVotes) {
                    (count.toFloat() / max).coerceIn(MIN_VISIBLE_FRACTION, 1f)
                } else {
                    MIN_VISIBLE_FRACTION
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(fraction)
                        .clip(shape)
                        .background(if (hasVotes) barColor else trackColor),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(barSpacing),
        ) {
            for (score in 1..10) {
                Text(
                    score.toString(),
                    Modifier.weight(1f),
                    color = labelColor,
                    style = labelStyle,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}
private const val MIN_VISIBLE_FRACTION = 0.06f
