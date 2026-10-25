/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.exploration.search

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import me.him188.ani.app.domain.search.SubjectType
import me.him188.ani.app.ui.foundation.interaction.onEnterKeyEvent

/** Search field shared by the native page and browser query adapter. */
@Composable
fun SubjectSearchInput(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    type: SubjectType,
    onTypeChange: (SubjectType) -> Unit,
    onClear: () -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: @Composable (() -> Unit)? = null,
    availableTypes: List<SubjectType> = SubjectType.entries,
) {
    SearchBarDefaults.InputField(
        query = query,
        onQueryChange = { onQueryChange(it.trim('\n')) },
        onSearch = { onSearch() },
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        modifier = modifier.testTag("subject-search-input").fillMaxWidth()
            .onFocusChanged { if (!it.isFocused && expanded) onExpandedChange(false) }
            .onEnterKeyEvent { onSearch(); true },
        placeholder = placeholder,
        leadingIcon = {
            Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, contentDescription = null)
                SearchSubjectTypeSelector(type, availableTypes, onTypeChange)
            }
        },
        trailingIcon = if (query.isNotEmpty() || expanded) {
            { IconButton(onClear) { Icon(Icons.Default.Close, contentDescription = "清除搜索") } }
        } else null,
    )
}
