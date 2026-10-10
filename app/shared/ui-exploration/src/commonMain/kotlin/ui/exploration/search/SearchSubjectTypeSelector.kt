/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.exploration.search

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import me.him188.ani.app.domain.search.SubjectType
import me.him188.ani.app.ui.foundation.widgets.SelectableDropdownMenuItem
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.search_type_anime
import me.him188.ani.app.ui.lang.search_type_book
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SearchSubjectTypeSelector(type: SubjectType, onSelect: (SubjectType) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val labels = mapOf(SubjectType.ANIME to stringResource(Lang.search_type_anime),
        SubjectType.BOOK to stringResource(Lang.search_type_book))
    Box {
        TextButton({ expanded = true }, Modifier.testTag("search-subject-type")) {
            Text(labels.getValue(type), maxLines = 1)
            Icon(Icons.Rounded.ArrowDropDown, null)
        }
        DropdownMenu(expanded, { expanded = false }) {
            SubjectType.entries.forEach { option ->
                SelectableDropdownMenuItem(selected = type == option,
                    text = { Text(labels.getValue(option)) },
                    onClick = { expanded = false; onSelect(option) },
                    modifier = Modifier.testTag("search-subject-type-${option.name}"))
            }
        }
    }
}
