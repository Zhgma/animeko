/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.manga

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Shared collection tabs; labels are supplied by each platform's localization layer. */
@Composable
fun TrackingCollectionTabsContent(
    mangaSelected: Boolean,
    onSelectManga: (Boolean) -> Unit,
    animeLabel: String,
    mangaLabel: String,
) {
    PrimaryTabRow(
        selectedTabIndex = if (mangaSelected) 1 else 0,
        modifier = Modifier.width(184.dp),
        containerColor = Color.Transparent,
        divider = {},
    ) {
        listOf(animeLabel, mangaLabel).forEachIndexed { index, title ->
            Tab(
                selected = mangaSelected == (index == 1),
                onClick = { onSelectManga(index == 1) },
                modifier = Modifier.testTag(if (index == 0) "tracking-anime" else "tracking-manga"),
                text = { Text(title, style = MaterialTheme.typography.titleLarge) },
            )
        }
    }
}

/** Platform image loading is provided as a slot; typography and card layout are shared. */
@Composable
fun MangaCollectionCard(
    title: String,
    progress: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    image: @Composable () -> Unit,
    annotations: @Composable () -> Unit = {},
) {
    Card(onClick = onClick, modifier = modifier) {
        image()
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(progress, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            annotations()
        }
    }
}
