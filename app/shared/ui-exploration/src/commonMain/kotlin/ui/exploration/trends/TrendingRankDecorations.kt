/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.exploration.trends

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.exploration_trending_ranking_heat
import org.jetbrains.compose.resources.stringResource

/**
 * 封面左上角的名次. 前三名用主题色突出.
 */
@Composable
fun TrendingRankBadge(
    rank: Int,
    modifier: Modifier = Modifier,
) {
    val highlighted = rank <= 3
    Surface(
        modifier,
        // 左上角由封面的圆角裁剪, 只需要右下角是圆的
        shape = RoundedCornerShape(bottomEnd = 12.dp),
        color = if (highlighted) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f)
        },
        contentColor = if (highlighted) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    ) {
        Text(
            rank.toString(),
            Modifier
                .defaultMinSize(minWidth = 32.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun TrendingHeatText(
    heat: Int,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Rounded.LocalFireDepartment,
            contentDescription = null,
            Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.size(4.dp))
        Text(
            stringResource(Lang.exploration_trending_ranking_heat, heat.toString()),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

