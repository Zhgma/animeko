/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.subject.collection

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Collection card geometry shared by native state holders and browser state holders. */
@Composable
fun SubjectCollectionCard(
    onClick: () -> Unit,
    cover: @Composable () -> Unit,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 148.dp,
    shape: Shape = MaterialTheme.shapes.small,
    colors: CardColors = CardDefaults.cardColors(),
) {
    Card(onClick, modifier.clip(shape).fillMaxWidth().height(height), shape = shape, colors = colors) {
        Row(Modifier.weight(1f, fill = false)) {
            cover()
            Box(Modifier.weight(1f)) { content() }
        }
    }
}
