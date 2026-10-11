/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.subject.details.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.lang.*
import me.him188.ani.app.ui.rating.FiveRatingStars
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt


@Composable
fun SubjectRatingSummaryValues(
    score: String, rank: Int, total: Int,
    modifier: Modifier = Modifier,
    scoreStyle: TextStyle = MaterialTheme.typography.displaySmall,
    starSize: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier
            .clip(MaterialTheme.shapes.small)
            .ifThen(onClick != null) { clickable(onClick = checkNotNull(onClick)) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            score,
            style = scoreStyle,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FiveRatingStars(
                remember(score) { (score.toFloatOrNull() ?: 0f).roundToInt() },
                starSize = starSize,
            )
            Text(
                stringResource(
                    Lang.subject_details_rating_summary,
                    rank.toString(),
                    remember(total) { groupThousands(total) },
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun SubjectCollectionStatsValues(
    collect: Int, doing: Int, wish: Int,
    modifier: Modifier = Modifier,
    doingLabel: String = stringResource(Lang.subject_details_stat_watching),
    wishLabel: String = stringResource(Lang.subject_details_stat_wish),
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatCell(collect, stringResource(Lang.subject_details_stat_collected), Modifier.weight(1f))
        StatCell(doing, doingLabel, Modifier.weight(1f))
        StatCell(wish, wishLabel, Modifier.weight(1f))
    }
}

@Composable
private fun StatCell(count: Int, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            remember(count) { groupThousands(count) },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

internal fun groupThousands(n: Int): String {
    val s = n.toString()
    val neg = s.startsWith("-")
    val digits = if (neg) s.substring(1) else s
    val grouped = digits.reversed().chunked(3).joinToString(",").reversed()
    return if (neg) "-$grouped" else grouped
}
