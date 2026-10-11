/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.settings.account
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.text.ProvideTextStyleContentColor
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_account_login_register
import org.jetbrains.compose.resources.stringResource

@Composable
fun SelfInfoBannerContent(
    signedIn: Boolean,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onLoginClick: () -> Unit,
    avatar: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    textModifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
) {
    Surface(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier,
        color = containerColor,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = CenterVertically) {
            if (!signedIn) {
                FilledTonalButton(onLoginClick, Modifier.fillMaxWidth()) {
                    Text(stringResource(Lang.settings_account_login_register))
                }
            } else {
                avatar(Modifier.clip(CircleShape).size(64.dp))

                Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ProvideTextStyleContentColor(
                        MaterialTheme.typography.titleMedium,
                        MaterialTheme.colorScheme.onSurface,
                    ) {
                        Text(
                            title,
                            modifier = textModifier,
                        )
                    }

                    if (subtitle.isNotBlank()) {
                        ProvideTextStyleContentColor(
                            MaterialTheme.typography.bodyMedium,
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        ) {
                            Text(
                                subtitle,
                                modifier = textModifier,
                            )
                        }
                    }
                }
            }
        }
    }
}
