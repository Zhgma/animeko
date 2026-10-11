/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.subject.details.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.lang.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun SubjectSummarySection(
    summary: String,
    modifier: Modifier = Modifier,
    collapsedMaxLines: Int = 5,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var hasOverflow by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        SelectionContainer {
            Text(
                summary,
                Modifier.fillMaxWidth().clickable { expanded = !expanded },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else collapsedMaxLines,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { if (!expanded) hasOverflow = it.hasVisualOverflow },
            )
        }
        if (hasOverflow || expanded) {
            TextButton(
                { expanded = !expanded },
                Modifier.align(Alignment.End),
            ) {
                Text(stringResource(if (expanded) Lang.subject_details_show_less else Lang.subject_details_show_more))
            }
        }
    }
}

@Composable
fun SubjectInfoTable(
    rows: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    labelWidth: Dp = 78.dp,
    rowSpacing: Dp = 12.dp,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(rowSpacing)) {
        rows.forEach { (label, value) ->
            Row(Modifier.fillMaxWidth()) {
                Text(
                    label,
                    Modifier.width(labelWidth),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    value,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalContentColor.current,
                )
            }
        }
    }
}
