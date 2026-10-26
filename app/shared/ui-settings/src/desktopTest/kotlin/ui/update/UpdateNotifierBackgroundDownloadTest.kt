/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.update

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlin.test.assertEquals
import me.him188.ani.app.tools.update.FileDownloaderState
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_mediasource_retry
import me.him188.ani.app.ui.lang.settings_update_popup_background_download
import me.him188.ani.app.ui.lang.settings_update_popup_cancel
import me.him188.ani.app.ui.lang.settings_update_popup_close
import me.him188.ani.app.ui.lang.settings_update_popup_download_complete
import me.him188.ani.app.ui.lang.settings_update_popup_restart_update
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.stringResource

@OptIn(TestOnly::class)
class UpdateNotifierBackgroundDownloadTest {
    @Test
    fun popupReappearsWhenDownloadCompletes() = verifyBackgroundDownload(UpdateNotifierLayoutKind.POPUP, false)

    @Test
    fun mobileReappearsWhenDownloadCompletes() = verifyBackgroundDownload(UpdateNotifierLayoutKind.SNACKBAR, false)

    @Test
    fun popupReappearsOnFailureAndRetryCanRunInBackground() = verifyBackgroundDownload(UpdateNotifierLayoutKind.POPUP, true)

    @Test
    fun mobileReappearsOnFailureAndRetryCanRunInBackground() = verifyBackgroundDownload(UpdateNotifierLayoutKind.SNACKBAR, true)

    @Test
    fun notificationCanReopenAndBackgroundRepeatedly() = runAniComposeUiTest {
        var request by mutableStateOf(0L)
        var backgroundLabel = ""
        var cancels = 0
        setContent {
            ProvideCompositionLocalsForPreview {
                backgroundLabel = stringResource(Lang.settings_update_popup_background_download)
                Box(Modifier.fillMaxSize()) {
                    UpdateNotifier(
                        presentation = downloading(),
                        onStartUpdateClick = {}, onInstallClick = {},
                        onCancelClick = { cancels++ }, onRetryClick = {},
                        foregroundRequest = request,
                    )
                }
            }
        }
        repeat(3) {
            onNodeWithText(backgroundLabel).assertIsDisplayed().performClick()
            onNodeWithText(TestNewVersion.name).assertDoesNotExist()
            runOnIdle { request++ }
            onNodeWithText(backgroundLabel).assertIsDisplayed()
        }
        runOnIdle { assertEquals(0, cancels) }
    }

    @Test
    fun deniedNotificationsKeepProgressVisible() = runAniComposeUiTest {
        var backgroundLabel = ""
        setContent {
            ProvideCompositionLocalsForPreview {
                backgroundLabel = stringResource(Lang.settings_update_popup_background_download)
                Box(Modifier.fillMaxSize()) {
                    UpdateNotifier(
                        presentation = downloading(),
                        onStartUpdateClick = {}, onInstallClick = {}, onCancelClick = {}, onRetryClick = {},
                        onBackgroundDownloadRequest = { false },
                    )
                }
            }
        }
        onNodeWithText(backgroundLabel).performClick()
        onNodeWithText(backgroundLabel).assertIsDisplayed()
    }

    private fun verifyBackgroundDownload(kind: UpdateNotifierLayoutKind, failFirst: Boolean) = runAniComposeUiTest {
        var presentation by mutableStateOf(downloading())
        var cancelCount = 0
        var installCount = 0
        var retryCount = 0
        var backgroundLabel = ""
        var completeLabel = ""
        var installLabel = ""
        var retryLabel = ""
        var closeLabel = ""
        var cancelLabel = ""
        setContent {
            ProvideCompositionLocalsForPreview {
                backgroundLabel = stringResource(Lang.settings_update_popup_background_download)
                completeLabel = stringResource(Lang.settings_update_popup_download_complete)
                installLabel = stringResource(Lang.settings_update_popup_restart_update)
                retryLabel = stringResource(Lang.settings_mediasource_retry)
                closeLabel = stringResource(Lang.settings_update_popup_close)
                cancelLabel = stringResource(Lang.settings_update_popup_cancel)
                Box(Modifier.fillMaxSize()) {
                    UpdateNotifier(
                        presentation = presentation,
                        onStartUpdateClick = { presentation = downloading() },
                        onInstallClick = { installCount++ },
                        onCancelClick = { cancelCount++ },
                        onRetryClick = { retryCount++; presentation = downloading() },
                        layoutKind = kind,
                    )
                }
            }
        }

        onNodeWithText(backgroundLabel).assertIsDisplayed().performClick()
        onNodeWithText(TestNewVersion.name).assertDoesNotExist()
        runOnIdle { presentation = downloading(0.8f) }
        onNodeWithText(TestNewVersion.name).assertDoesNotExist()
        runOnIdle { assertEquals(0, cancelCount) }

        if (failFirst) {
            runOnIdle {
                presentation = TestAppUpdatePresentations.Failed.copy(fileDownloaderStats = TestFileDownloaderStats.Failed)
            }
            onNodeWithText(retryLabel).assertIsDisplayed().performClick()
            runOnIdle { assertEquals(1, retryCount) }
            onNodeWithText(backgroundLabel).assertIsDisplayed().performClick()
            onNodeWithText(TestNewVersion.name).assertDoesNotExist()
        }

        runOnIdle {
            presentation = TestAppUpdatePresentations.Succeed.copy(fileDownloaderStats = TestFileDownloaderStats.Succeed)
        }
        onNodeWithText(completeLabel).assertIsDisplayed()
        onNodeWithText(backgroundLabel).assertDoesNotExist()
        runOnIdle { assertEquals(0, installCount) }
        onNodeWithText(installLabel).performClick()
        runOnIdle { assertEquals(1, installCount) }

        // Explicit cancellation still requires confirmation while a download is active.
        runOnIdle { presentation = downloading() }
        onNodeWithContentDescription(closeLabel).performClick()
        runOnIdle { assertEquals(0, cancelCount) }
        onNodeWithText(cancelLabel).performClick()
        runOnIdle { assertEquals(1, cancelCount) }
        onNodeWithText(TestNewVersion.name).assertDoesNotExist()
    }

    private fun downloading(progress: Float = 0.5f): AppUpdatePresentation {
        val stats = FileDownloaderStats(progress, FileDownloaderState.Downloading)
        return TestAppUpdatePresentations.Downloading.copy(
            state = AppUpdateState.Downloading(TestNewVersion, stats),
            fileDownloaderStats = stats,
        )
    }
}
