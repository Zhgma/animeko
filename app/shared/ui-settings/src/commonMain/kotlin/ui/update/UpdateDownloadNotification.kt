/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.update

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import me.him188.ani.app.platform.ContextMP

val LocalAppUpdateViewModel = staticCompositionLocalOf<AppUpdateViewModel> {
    error("App updates must be provided by the application host")
}

/** Notification taps reopen the same download, including repeated foreground/background cycles. */
object UpdateDownloadUiRequests {
    const val ACTION_SHOW_DOWNLOAD = "me.him188.ani.action.SHOW_UPDATE_DOWNLOAD"
    private val requests = MutableStateFlow(0L)
    val foregroundRequests = requests.asStateFlow()
    fun showDownload() { requests.update { it + 1 } }
}

internal enum class UpdateDownloadPhase { DOWNLOADING, DOWNLOADED, FAILED, INSTALLING }

internal data class UpdateDownloadNotice(
    val version: String,
    val phase: UpdateDownloadPhase,
    val percent: Int,
)

internal fun AppUpdatePresentation.downloadNotice(): UpdateDownloadNotice? {
    val phase = when (state) {
        is AppUpdateState.Downloading -> UpdateDownloadPhase.DOWNLOADING
        is AppUpdateState.Downloaded -> UpdateDownloadPhase.DOWNLOADED
        is AppUpdateState.DownloadFailed -> UpdateDownloadPhase.FAILED
        is AppUpdateState.Installing -> UpdateDownloadPhase.INSTALLING
        else -> return null
    }
    val progress = fileDownloaderStats.progress
    return UpdateDownloadNotice(
        newVersion?.name ?: return null,
        phase,
        if (progress.isFinite()) (progress.coerceIn(0f, 1f) * 100).toInt() else 0,
    )
}

internal interface UpdateDownloadNotifier : AutoCloseable {
    fun update(notice: UpdateDownloadNotice?)
}

internal expect fun createUpdateDownloadNotifier(context: ContextMP, onTimeout: () -> Unit): UpdateDownloadNotifier
internal expect fun canShowUpdateDownloadNotification(context: ContextMP): Boolean
