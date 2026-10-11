/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.exploration.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.him188.ani.app.domain.search.SearchSort
import me.him188.ani.app.ui.foundation.widgets.SelectableDropdownMenuItem
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.exploration_search_results_shown
import me.him188.ani.app.ui.lang.exploration_search_sort_collection
import me.him188.ani.app.ui.lang.exploration_search_sort_date
import me.him188.ani.app.ui.lang.exploration_search_sort_match
import me.him188.ani.app.ui.lang.exploration_search_sort_rank
import org.jetbrains.compose.resources.stringResource
import me.him188.ani.app.ui.foundation.IconButton
import me.him188.ani.app.ui.foundation.icons.BackgroundDotLarge
import me.him188.ani.app.ui.foundation.icons.GalleryThumbnail
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton

enum class SearchResultLayoutKind {
    COVER,
    PREVIEW, ;

    companion object {
        fun next(kind: SearchResultLayoutKind): SearchResultLayoutKind {
            return entries[(kind.ordinal + 1) % entries.size]
        }
    }
}

@Composable
fun SearchResultsToolbar(
    count: Int,
    layoutKind: SearchResultLayoutKind,
    currentSort: SearchSort,
    onLayoutKindChange: (SearchResultLayoutKind) -> Unit,
    onSortChange: (SearchSort) -> Unit,
    modifier: Modifier = Modifier,
    sortOptions: List<SearchSort> = SearchSort.entries,
) {
    Surface(modifier, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.aligned(Alignment.CenterVertically),
            itemVerticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Lang.exploration_search_results_shown, count), style = MaterialTheme.typography.bodyLarge)
            Row(Modifier.weight(1f).align(Alignment.Bottom), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.aligned(Alignment.End)) {
                LayoutKindButton(layoutKind, onLayoutKindChange)
                SortButton(currentSort, onSortChange, sortOptions = sortOptions)
            }
        }
    }
}

@Composable
private fun SearchSortTextButton(onClick: () -> Unit, leadingIcon: @Composable () -> Unit, content: @Composable () -> Unit) {
    TextButton(onClick, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
        leadingIcon()
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        content()
    }
}

@Composable
private fun LayoutKindButton(
    layoutKind: SearchResultLayoutKind,
    onLayoutKindChange: (SearchResultLayoutKind) -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = {
            onLayoutKindChange(SearchResultLayoutKind.next(layoutKind))
        },
        modifier,
    ) {
        Icon(
            when (layoutKind) {
                SearchResultLayoutKind.COVER -> Icons.Outlined.BackgroundDotLarge
                SearchResultLayoutKind.PREVIEW -> Icons.Outlined.GalleryThumbnail
            },
            layoutKind.name, // not good
        )
    }
}

/**
 * 切换排序方式的按钮
 *
 * @see [SearchSort]
 */
@Composable
private fun SortButton(
    currentSort: SearchSort,
    onSortChange: (SearchSort) -> Unit,
    modifier: Modifier = Modifier,
    sortOptions: List<SearchSort> = SearchSort.entries,
) {
    val sortLabels = rememberSearchSortLabels()
    Box(
        modifier, contentAlignment = Alignment.BottomEnd,
    ) {
        var showDropdown by rememberSaveable {
            mutableStateOf(false)
        }
        SearchSortTextButton(
            onClick = { showDropdown = true },
            leadingIcon = {
                Icon(Icons.AutoMirrored.Rounded.Sort, null)
            },
        ) {
            Text(getSortText(currentSort, sortLabels), softWrap = false)
        }
        DropdownMenu(showDropdown, { showDropdown = false }) {
            for (sort in sortOptions) {
                SelectableDropdownMenuItem(
                    selected = sort == currentSort,
                    text = {
                        Text(
                            getSortText(sort, sortLabels),
                            softWrap = false,
                        )
                    },
                    onClick = {
                        showDropdown = false
                        onSortChange(sort)
                    },
                )
            }
        }
    }
}

@Immutable
private data class SearchSortLabels(
    val match: String,
    val collection: String,
    val rank: String,
    val date: String,
)

@Composable
private fun rememberSearchSortLabels(): SearchSortLabels = SearchSortLabels(
    match = stringResource(Lang.exploration_search_sort_match),
    collection = stringResource(Lang.exploration_search_sort_collection),
    rank = stringResource(Lang.exploration_search_sort_rank),
    date = stringResource(Lang.exploration_search_sort_date),
)

private fun getSortText(currentSort: SearchSort, labels: SearchSortLabels): String = when (currentSort) {
    SearchSort.MATCH -> labels.match
    SearchSort.COLLECTION -> labels.collection
    SearchSort.RANK -> labels.rank
    SearchSort.DATE -> labels.date
}
