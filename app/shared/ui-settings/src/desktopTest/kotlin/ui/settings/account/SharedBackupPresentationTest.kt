/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.settings.account

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_danmaku_cancel
import me.him188.ani.app.ui.lang.settings_danmaku_confirm
import me.him188.ani.app.ui.lang.settings_storage_backup_op_backup_title
import me.him188.ani.app.ui.lang.settings_storage_backup_op_restore
import me.him188.ani.app.ui.lang.settings_storage_backup_op_restore_warning
import me.him188.ani.app.ui.settings.SettingsTab
import me.him188.ani.app.ui.settings.tabs.media.BackupSettingsContent
import me.him188.ani.app.ui.settings.tabs.media.RestoreSettingsConfirmation
import org.jetbrains.compose.resources.stringResource
import kotlin.test.Test
import kotlin.test.assertEquals

class SharedBackupPresentationTest {
    @Test
    fun copyActsDirectlyAndRestoreRequiresConfirmation() = runAniComposeUiTest {
        var copies = 0
        var restores = 0
        var copyLabel = ""
        var restoreLabel = ""
        var warning = ""
        var cancelLabel = ""
        var confirmLabel = ""
        setContent {
            ProvideCompositionLocalsForPreview {
                var confirming by remember { mutableStateOf(false) }
                copyLabel = stringResource(Lang.settings_storage_backup_op_backup_title)
                restoreLabel = stringResource(Lang.settings_storage_backup_op_restore)
                warning = stringResource(Lang.settings_storage_backup_op_restore_warning)
                cancelLabel = stringResource(Lang.settings_danmaku_cancel)
                confirmLabel = stringResource(Lang.settings_danmaku_confirm)
                SettingsTab {
                    BackupSettingsContent({ copies++ }, { confirming = true })
                }
                if (confirming) RestoreSettingsConfirmation(
                    onDismissRequest = { confirming = false },
                    onConfirm = { restores++; confirming = false },
                )
            }
        }
        onNodeWithText(copyLabel).performClick()
        runOnIdle { assertEquals(1, copies) }
        onNodeWithText(restoreLabel).performClick()
        onNodeWithText(warning).assertIsDisplayed()
        runOnIdle { assertEquals(0, restores) }
        onNodeWithText(cancelLabel).performClick()
        runOnIdle { assertEquals(0, restores) }
        onNodeWithText(restoreLabel).performClick()
        onNodeWithText(confirmLabel).performClick()
        runOnIdle { assertEquals(1, restores) }
    }
}
