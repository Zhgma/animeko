/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.settings.tabs.media.source
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.lang.*
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import org.jetbrains.compose.resources.stringResource

private const val DISABLED_ALPHA = 0.38f
@Composable
fun SettingsScope.MediaSourceItemContent(title: String, description: String, isEnabled: Boolean,
    modifier: Modifier = Modifier, sourceIcon: @Composable () -> Unit, kindIcon: @Composable () -> Unit,
    tier: (@Composable () -> Unit)? = null, selectionMode: Boolean = false, selected: Boolean = false,
    onToggleSelected: () -> Unit = {}, actions: @Composable RowScope.() -> Unit) {
    Item(
        modifier = modifier,
        supportingContent = {
            SelectionContainer {
                Text(
                    description,
                    Modifier.ifThen(!isEnabled) { alpha(DISABLED_ALPHA) },
                )
            }
        },
        leadingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectionMode) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { onToggleSelected() },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .ifThen(!isEnabled) { alpha(DISABLED_ALPHA) }
                            .clip(MaterialTheme.shapes.extraSmall)
                            .size(48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        sourceIcon()
                    }
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                actions()
            }
        },
        headlineContent = {
            val name = title + if (!isEnabled) stringResource(Lang.settings_media_source_disabled) else ""
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                kindIcon()
                Text(
                    name,
                    Modifier.ifThen(!isEnabled) { alpha(DISABLED_ALPHA) }.basicMarquee(),
                    textAlign = TextAlign.Center,
                )
                tier?.invoke()

            }
        },
    )
}
