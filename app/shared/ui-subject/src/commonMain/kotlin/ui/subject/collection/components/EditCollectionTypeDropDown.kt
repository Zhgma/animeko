/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.collection.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.foundation.widgets.showLoadError
import me.him188.ani.app.ui.lang.*
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import org.jetbrains.compose.resources.*


@Composable
fun EditCollectionTypeDropDown(
    state: EditableSubjectCollectionTypeState,
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val presentation by state.presentationFlow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current

    EditCollectionTypeDropDown(
        currentType = presentation.selfCollectionType,
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        onClick = {
            scope.launch {
                val error = state.setSelfCollectionType(it.type)
                if (error != null) {
                    toaster.showLoadError(error)
                }
            }
        },
        modifier = modifier,
    )
}
