/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.settings.account
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.widgets.HeroIcon
import me.him188.ani.app.ui.foundation.text.ProvideContentColor
import me.him188.ani.app.ui.settings.SettingsTab
import me.him188.ani.app.ui.settings.framework.components.TextItem
import me.him188.ani.app.ui.lang.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun ProfilePopupContent(
    isLogin: Boolean, title: String,
    avatar: @Composable () -> Unit,
    onClickLogin: () -> Unit, onClickEditProfile: () -> Unit,
    onClickPlaybackHistory: () -> Unit, onClickSettings: () -> Unit, onClickLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val notLoggedInText = stringResource(Lang.settings_account_popup_not_logged_in)
    val editProfileText = stringResource(Lang.settings_account_popup_edit_profile)
    val loginRegisterText = stringResource(Lang.settings_account_popup_login_register)
    val playbackHistoryText = stringResource(Lang.playback_history_title)
    val settingsText = stringResource(Lang.settings)
    val logoutText = stringResource(Lang.settings_account_popup_logout)
    Column(modifier) {
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            HeroIcon {
                avatar()
            }
        }
        val showEmail = false

        Text(
            if (isLogin) title else notLoggedInText,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(
                    bottom = if (showEmail) 4.dp else 0.dp,
                )
                .fillMaxWidth(),
            maxLines = 1,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.MiddleEllipsis,
        )
        SettingsTab(
            modifier = Modifier
                .padding(top = 24.dp)
                .fillMaxWidth(),
        ) {
            Column {
                if (isLogin) {
                    TextItem(
                        icon = { Icon(Icons.Outlined.Edit, contentDescription = editProfileText) },
                        onClick = onClickEditProfile,
                    ) {
                        Text(editProfileText)
                    }
                } else {
                    TextItem(
                        icon = { Icon(Icons.AutoMirrored.Outlined.Login, contentDescription = loginRegisterText) },
                        onClick = onClickLogin,
                    ) {
                        Text(loginRegisterText)
                    }
                }

                TextItem(
                    icon = { Icon(Icons.Outlined.History, contentDescription = playbackHistoryText) },
                    onClick = onClickPlaybackHistory,
                ) {
                    Text(playbackHistoryText)
                }

                TextItem(
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = settingsText) },
                    onClick = onClickSettings,
                ) {
                    Text(settingsText)
                }

                if (isLogin) {
                    TextItem(
                        icon = {
                            ProvideContentColor(MaterialTheme.colorScheme.error) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.Logout,
                                    contentDescription = logoutText,
                                )
                            }
                        },
                        onClick = onClickLogout,
                    ) {
                        ProvideContentColor(MaterialTheme.colorScheme.error) {
                            Text(logoutText)
                        }
                    }
                }
            }
        }
    }
}
