/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.manga

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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


@Composable
fun TrackingCollectionTabsContent(selected: Int, onSelect: (Int) -> Unit, labels: List<String>) {
    PrimaryTabRow(selectedTabIndex = selected, modifier = Modifier.width(264.dp),
        containerColor = Color.Transparent, divider = {}) {
        labels.forEachIndexed { index, title ->
            Tab(selected == index, { onSelect(index) },
                modifier = Modifier.testTag(listOf("tracking-anime", "tracking-manga", "tracking-novel")[index]),
                text = { Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1) })
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
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val style = MaterialTheme.typography.bodySmall
                val measurer = rememberTextMeasurer()
                val availableWidth = with(LocalDensity.current) { maxWidth.roundToPx() }
                var fontSize = style.fontSize.value
                // Measure the complete line; ellipsized layout does not expose its natural width.
                while (fontSize > 8f && measurer.measure(
                        progress, style.copy(fontSize = fontSize.sp), softWrap = false, maxLines = 1,
                    ).size.width >= availableWidth) {
                    fontSize = (fontSize - 0.25f).coerceAtLeast(8f)
                }
                Text(progress, modifier = Modifier.fillMaxWidth(), style = style.copy(fontSize = fontSize.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
            annotations()
        }
    }
}
