/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.settings.account
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import me.him188.ani.app.ui.lang.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun AccountLogoutDialog(
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    confirmEnabled: Boolean = true,
) {
    AlertDialog(
        onCancel,
        icon = { Icon(Icons.AutoMirrored.Outlined.Logout, null) },
        text = { Text(stringResource(Lang.settings_account_popup_logout_confirm)) },
        confirmButton = {
            TextButton(onConfirm, enabled = confirmEnabled) {
                Text(stringResource(Lang.settings_account_popup_logout_button), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onCancel) {
                Text(stringResource(Lang.settings_account_popup_cancel))
            }
        },
    )
}
