/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.settings.tabs.media

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_danmaku_cancel
import me.him188.ani.app.ui.lang.settings_danmaku_confirm
import me.him188.ani.app.ui.lang.settings_storage_backup_op_backup_description
import me.him188.ani.app.ui.lang.settings_storage_backup_op_backup_title
import me.him188.ani.app.ui.lang.settings_storage_backup_op_restore
import me.him188.ani.app.ui.lang.settings_storage_backup_op_restore_description
import me.him188.ani.app.ui.lang.settings_storage_backup_op_restore_warning
import me.him188.ani.app.ui.lang.settings_storage_backup_title
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.app.ui.settings.framework.components.TextItem
import org.jetbrains.compose.resources.stringResource

/** Shared presentation; each platform supplies its clipboard and persistence operations. */
@Composable
fun SettingsScope.BackupSettingsContent(
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    enabled: Boolean = true,
) {
    Group({ Text(stringResource(Lang.settings_storage_backup_title)) }) {
        TextItem(
            onClick = onBackup,
            onClickEnabled = enabled,
            title = { Text(stringResource(Lang.settings_storage_backup_op_backup_title)) },
            description = { Text(stringResource(Lang.settings_storage_backup_op_backup_description)) },
        )
        TextItem(
            onClick = onRestore,
            onClickEnabled = enabled,
            title = { Text(stringResource(Lang.settings_storage_backup_op_restore)) },
            description = { Text(stringResource(Lang.settings_storage_backup_op_restore_description)) },
        )
    }
}

@Composable
fun RestoreSettingsConfirmation(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    enabled: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = { Icon(Icons.Rounded.ContentPaste, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(Lang.settings_storage_backup_op_restore)) },
        text = { Text(stringResource(Lang.settings_storage_backup_op_restore_warning)) },
        confirmButton = {
            TextButton(onConfirm, enabled = enabled) {
                Text(stringResource(Lang.settings_danmaku_confirm), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onDismissRequest, enabled = enabled) {
                Text(stringResource(Lang.settings_danmaku_cancel))
            }
        },
    )
}
