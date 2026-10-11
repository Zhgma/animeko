/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.rating

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.StarHalf
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun FiveRatingStars(
    score: Int, // range 0..10
    starSize: Dp = 22.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy((-1).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides color) {
            Icon(
                when {
                    score >= 2 -> Icons.Rounded.Star
                    score == 1 -> Icons.AutoMirrored.Rounded.StarHalf
                    else -> Icons.Rounded.StarOutline
                },
                contentDescription = null,
                Modifier.size(starSize),
            )
            Icon(
                when {
                    score >= 4 -> Icons.Rounded.Star
                    score == 3 -> Icons.AutoMirrored.Rounded.StarHalf
                    else -> Icons.Rounded.StarOutline
                },
                contentDescription = null,
                Modifier.size(starSize),
            )
            Icon(
                when {
                    score >= 6 -> Icons.Rounded.Star
                    score == 5 -> Icons.AutoMirrored.Rounded.StarHalf
                    else -> Icons.Rounded.StarOutline
                },
                contentDescription = null,
                Modifier.size(starSize),
            )
            Icon(
                when {
                    score >= 8 -> Icons.Rounded.Star
                    score == 7 -> Icons.AutoMirrored.Rounded.StarHalf
                    else -> Icons.Rounded.StarOutline
                },
                contentDescription = null,
                Modifier.size(starSize),
            )
            Icon(
                when {
                    score >= 10 -> Icons.Rounded.Star
                    score == 9 -> Icons.AutoMirrored.Rounded.StarHalf
                    else -> Icons.Rounded.StarOutline
                },
                contentDescription = null,
                Modifier.size(starSize),
            )
        }
    }
}
