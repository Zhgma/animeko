/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.settings.tabs.media.source
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import me.him188.ani.app.ui.settings.framework.components.SettingsScope

@Composable
fun SettingsScope.SubscriptionItemContent(url: String, updateDescription: String, actions: @Composable () -> Unit) {
    Item(headlineContent = { SelectionContainer { Text(url) } },
        supportingContent = { Text(updateDescription) }, trailingContent = actions)
}
