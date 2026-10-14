/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.settings.tabs.media.source

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import me.him188.ani.app.ui.lang.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun AddMediaSubscriptionDialog(
    url: String, onUrlChange: (String) -> Unit, busy: Boolean,
    onPaste: () -> Unit, onConfirm: () -> Unit, onDismiss: () -> Unit,
    error: String? = null,
) {
    val focus = remember { FocusRequester() }
    val canConfirm = url.isNotBlank() && !busy
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(Lang.settings_media_source_subscription_add_dialog)) },
        text = { Column {
            OutlinedTextField(url, onUrlChange, Modifier.fillMaxWidth().focusRequester(focus),
                singleLine = true, enabled = !busy, isError = error != null,
                label = { Text(stringResource(Lang.settings_media_source_subscription_url)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (canConfirm) onConfirm() }),
                trailingIcon = { IconButton(onPaste, enabled = !busy) {
                    Icon(Icons.Rounded.ContentPaste, stringResource(Lang.settings_media_source_subscription_paste))
                } })
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(onConfirm, enabled = canConfirm) {
            Text(stringResource(Lang.settings_media_source_subscription_add_confirm))
        } },
        dismissButton = { TextButton(onDismiss, enabled = !busy) {
            Text(stringResource(Lang.settings_media_source_subscription_cancel))
        } })
    LaunchedEffect(Unit) { focus.requestFocus() }
}
