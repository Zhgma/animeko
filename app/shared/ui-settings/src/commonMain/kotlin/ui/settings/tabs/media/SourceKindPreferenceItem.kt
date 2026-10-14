// SPDX-License-Identifier: AGPL-3.0-only
package me.him188.ani.app.ui.settings.tabs.media

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import me.him188.ani.app.ui.lang.*
import me.him188.ani.app.ui.settings.framework.components.*
import me.him188.ani.app.ui.settings.rendering.P2p
import me.him188.ani.datasources.api.source.MediaSourceKind
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingsScope.SourceKindPreferenceItem(selected: MediaSourceKind?, onSelect: (MediaSourceKind?) -> Unit) {
    val values = remember { MediaSourceKind.selectableEntries + null }
    DropdownItem(
        selected = { selected }, values = { values }, onSelect = onSelect,
        itemText = { Text(when (it) {
            MediaSourceKind.WEB -> stringResource(Lang.settings_media_source_web)
            MediaSourceKind.BitTorrent -> stringResource(Lang.settings_media_source_bt)
            null -> stringResource(Lang.settings_media_source_no_preference)
            MediaSourceKind.LocalCache -> ""
        }) },
        itemIcon = { kind -> when (kind) {
            MediaSourceKind.WEB -> Icon(Icons.Rounded.Public, null)
            MediaSourceKind.BitTorrent -> Icon(Icons.Filled.P2p, null)
            else -> Unit
        } },
        title = { Text(stringResource(Lang.settings_media_prefer_source_type)) },
        description = { Text(stringResource(Lang.settings_media_prefer_source_type_description)) },
    )
}
