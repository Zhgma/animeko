/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.danmaku

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow.Companion.Ellipsis
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.models.danmaku.DanmakuRegexFilter
import me.him188.ani.app.ui.foundation.getClipEntryText
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.rememberAsyncHandler
import me.him188.ani.app.ui.foundation.setClipEntryText
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_danmaku_add_regex
import me.him188.ani.app.ui.lang.settings_danmaku_add_regex_filter
import me.him188.ani.app.ui.lang.settings_danmaku_cancel
import me.him188.ani.app.ui.lang.settings_danmaku_confirm
import me.him188.ani.app.ui.lang.settings_danmaku_export_to_clipboard
import me.him188.ani.app.ui.lang.settings_danmaku_import_description
import me.him188.ani.app.ui.lang.settings_danmaku_import_failed
import me.him188.ani.app.ui.lang.settings_danmaku_import_from_clipboard
import me.him188.ani.app.ui.lang.settings_danmaku_import_success
import me.him188.ani.app.ui.lang.settings_danmaku_import_title
import me.him188.ani.app.ui.lang.settings_danmaku_regex_description
import me.him188.ani.app.ui.lang.settings_danmaku_regex_expression
import me.him188.ani.app.ui.lang.settings_danmaku_regex_filter_group
import me.him188.ani.app.ui.lang.settings_danmaku_regex_invalid
import me.him188.ani.app.ui.lang.settings_mediasource_rss_copied_to_clipboard
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.utils.platform.Uuid
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SettingsScope.DanmakuRegexFilterGroup(
    state: DanmakuRegexFilterState,
) {
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }

    if (showAdd) {
        AddRegexFilterDialog(
            onDismissRequest = {
                showAdd = false
            },
            onAdd = state.add,
            title = { Text(stringResource(Lang.settings_danmaku_add_regex_filter)) },
        )
    }

    val scope = rememberAsyncHandler()
    val clipboard = LocalClipboard.current
    val toaster = LocalToaster.current
    val copiedToast = stringResource(Lang.settings_mediasource_rss_copied_to_clipboard)

    if (showImportDialog) {
        val importSuccess = stringResource(Lang.settings_danmaku_import_success)
        val importFailed = stringResource(Lang.settings_danmaku_import_failed)

        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            icon = { Icon(Icons.Rounded.ContentPaste, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(Lang.settings_danmaku_import_title)) },
            text = { Text(stringResource(Lang.settings_danmaku_import_description)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val text = clipboard.getClipEntryText()
                                ?.takeIf { it.isNotBlank() }
                            val result = text?.let { state.onImport(it) } == true
                            toaster.toast(if (result) importSuccess else importFailed)
                            showImportDialog = false
                        }
                    },
                ) {
                    Text(stringResource(Lang.settings_danmaku_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text(stringResource(Lang.settings_danmaku_cancel))
                }
            },
        )
    }

    DanmakuRegexFilterContent(
        filters = state.list,
        onAdd = { showAdd = true },
        onRemove = state.remove,
        onSwitch = state.switch,
        onExport = { scope.launch { clipboard.setClipEntryText(state.onExport()); toaster.toast(copiedToast) } },
        onImport = { showImportDialog = true },
    )

}
