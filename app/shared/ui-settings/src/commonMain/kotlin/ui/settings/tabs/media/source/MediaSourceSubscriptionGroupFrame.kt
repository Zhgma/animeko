// SPDX-License-Identifier: AGPL-3.0-only
package me.him188.ani.app.ui.settings.tabs.media.source
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.app.ui.lang.*
import org.jetbrains.compose.resources.stringResource

@Composable fun SettingsScope.MediaSourceSubscriptionGroupFrame(
    updating: Boolean, onAdd: () -> Unit, onRefresh: () -> Unit,
    enabled: Boolean = true, content: @Composable ColumnScope.() -> Unit,
) {
    Group(title = { Text(stringResource(Lang.settings_media_source_subscription)) },
        description = { Text(stringResource(Lang.settings_media_source_subscription_description)) },
        actions = {
            IconButton(onAdd, enabled = enabled) { Icon(Icons.Rounded.Add, stringResource(Lang.settings_media_source_subscription_add)) }
            if (updating) CircularProgressIndicator(Modifier.size(24.dp))
            else IconButton(onRefresh, enabled = enabled) { Icon(Icons.Rounded.Refresh, stringResource(Lang.settings_media_source_subscription_refresh_all)) }
        }, content = content)
}
