/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.subject

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.him188.ani.utils.platform.format1f

/** Compact aggregate score, also used over cover images. */
@Composable
fun SubjectScoreLabel(score: String?, modifier: Modifier = Modifier, badge: Boolean = false) {
    val value = score?.toFloatOrNull()?.takeIf { it.isFinite() && it > 0f && it <= 10f }
    val text = if (value == null) "暂无评分" else "★ ${String.format1f(value)}"
    val label: @Composable () -> Unit = {
        Text(text, maxLines = 1, softWrap = false,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = if (badge) Modifier.padding(horizontal = 8.dp, vertical = 4.dp) else Modifier)
    }
    if (badge) Surface(modifier, shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f), content = label)
    else Box(modifier) { label() }
}
