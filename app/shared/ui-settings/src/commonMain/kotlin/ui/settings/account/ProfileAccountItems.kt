/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.settings.account

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.him188.ani.app.domain.session.auth.OAuthPlatform
import me.him188.ani.app.ui.foundation.icons.BangumiNext
import me.him188.ani.app.ui.foundation.icons.OAuthPlatformIcon
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_account_profile_not_bound
import me.him188.ani.app.ui.lang.settings_account_profile_user_id
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.app.ui.settings.framework.components.TextItem
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingsScope.ProfileUserIdItem(userId: String, modifier: Modifier = Modifier) {
    TextItem(
        title = { SelectionContainer { Text(userId) } },
        description = { Text(stringResource(Lang.settings_account_profile_user_id)) },
        modifier = modifier,
    )
}

@Composable
fun SettingsScope.ProfileLinkedAccountItem(
    platform: OAuthPlatform,
    username: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    TextItem(
        title = { Text(platform.displayName) },
        description = { Text(username ?: stringResource(Lang.settings_account_profile_not_bound)) },
        icon = {
            if (platform == OAuthPlatform.BANGUMI) Image(Icons.Default.BangumiNext, "Bangumi Icon")
            else OAuthPlatformIcon(platform, Modifier.size(24.dp))
        },
        onClick = onClick,
        action = action,
        modifier = modifier,
    )
}
