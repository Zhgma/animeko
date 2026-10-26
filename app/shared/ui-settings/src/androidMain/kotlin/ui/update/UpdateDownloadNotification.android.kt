/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.update

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import me.him188.ani.app.platform.ContextMP
import me.him188.ani.app.ui.settings.R
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn

private const val CHANNEL_ID = "app_update_download"
private const val NOTIFICATION_ID = 116

internal actual fun canShowUpdateDownloadNotification(context: ContextMP): Boolean {
    val manager = context.getSystemService(NotificationManager::class.java)
    return manager.areNotificationsEnabled() &&
            manager.getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
}

internal actual fun createUpdateDownloadNotifier(context: ContextMP, onTimeout: () -> Unit): UpdateDownloadNotifier =
    AndroidUpdateDownloadNotifier(context.applicationContext, onTimeout)

private class AndroidUpdateDownloadNotifier(
    private val context: Context,
    private val onTimeout: () -> Unit,
) : UpdateDownloadNotifier {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val log = logger<AndroidUpdateDownloadNotifier>()
    private var startRequested = false

    init {
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL_ID, context.getString(R.string.update_download_channel), NotificationManager.IMPORTANCE_LOW,
        ))
        UpdateDownloadService.timeoutAction = onTimeout
    }

    override fun update(notice: UpdateDownloadNotice?) {
        UpdateDownloadService.notice.value = notice
        if (notice?.phase == UpdateDownloadPhase.DOWNLOADING) {
            if (!startRequested) {
                try {
                    ContextCompat.startForegroundService(context, Intent(context, UpdateDownloadService::class.java))
                    startRequested = true
                } catch (e: IllegalStateException) {
                    log.warn { "Update foreground service could not start: $e" }
                    onTimeout()
                }
            }
        } else {
            startRequested = false
            // The service also observes this terminal state and detaches its foreground notification.
            if (notice == null || notice.phase == UpdateDownloadPhase.INSTALLING) manager.cancel(NOTIFICATION_ID)
            else post(notice)
        }
    }

    private fun post(notice: UpdateDownloadNotice) {
        if (canShowUpdateDownloadNotification(context)) {
            manager.notify(NOTIFICATION_ID, buildDownloadNotification(context, notice))
        }
    }

    override fun close() {
        UpdateDownloadService.notice.value = null
        UpdateDownloadService.timeoutAction = null
        context.stopService(Intent(context, UpdateDownloadService::class.java))
        manager.cancel(NOTIFICATION_ID)
    }
}

/** Keeps the download process active and refreshes progress even with the Activity stopped. */
class UpdateDownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var observation: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val initial = notice.value
        try {
            // Must enter foreground even if the download finished before the start request arrived.
            startForeground(NOTIFICATION_ID, buildDownloadNotification(this, initial))
        } catch (e: IllegalStateException) {
            logger<UpdateDownloadService>().warn { "Update foreground start refused: $e" }
            timeoutAction?.invoke()
            stopSelf(startId)
            return START_NOT_STICKY
        }
        observation?.cancel()
        observation = scope.launch {
            notice.collect { current ->
                val manager = getSystemService(NotificationManager::class.java)
                if (current?.phase == UpdateDownloadPhase.DOWNLOADING) {
                    manager.notify(NOTIFICATION_ID, buildDownloadNotification(this@UpdateDownloadService, current))
                } else {
                    stopForeground(STOP_FOREGROUND_DETACH)
                    if (current == null || current.phase == UpdateDownloadPhase.INSTALLING) {
                        manager.cancel(NOTIFICATION_ID)
                    } else if (canShowUpdateDownloadNotification(this@UpdateDownloadService)) {
                        manager.notify(NOTIFICATION_ID, buildDownloadNotification(this@UpdateDownloadService, current))
                    }
                    stopSelf(startId)
                }
            }
        }
        // Process death must not resurrect a notification for an in-memory download that no longer exists.
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        timeoutAction?.invoke()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        internal val notice = MutableStateFlow<UpdateDownloadNotice?>(null)
        internal var timeoutAction: (() -> Unit)? = null
    }
}

private fun buildDownloadNotification(context: Context, notice: UpdateDownloadNotice?): Notification {
    val downloading = notice == null || notice.phase == UpdateDownloadPhase.DOWNLOADING
    val title = when (notice?.phase) {
        UpdateDownloadPhase.DOWNLOADED -> R.string.update_download_complete
        UpdateDownloadPhase.FAILED -> R.string.update_download_failed
        else -> R.string.update_download_title
    }
    val detail = when (notice?.phase) {
        UpdateDownloadPhase.DOWNLOADED -> context.getString(R.string.update_download_install)
        UpdateDownloadPhase.FAILED -> context.getString(R.string.update_download_retry)
        else -> if (notice?.percent == 100) context.getString(R.string.update_download_verifying)
                else context.getString(R.string.update_download_progress, notice?.percent ?: 0)
    }
    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
        action = UpdateDownloadUiRequests.ACTION_SHOW_DOWNLOAD
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    return Notification.Builder(context, CHANNEL_ID)
        .setSmallIcon(if (downloading) android.R.drawable.stat_sys_download else android.R.drawable.stat_sys_download_done)
        .setContentTitle(context.getString(title, notice?.version.orEmpty()))
        .setContentText(detail)
        .setContentIntent(launch?.let {
            PendingIntent.getActivity(context, NOTIFICATION_ID, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        })
        .setCategory(Notification.CATEGORY_PROGRESS)
        .setOnlyAlertOnce(true)
        .setShowWhen(false)
        .setOngoing(downloading)
        .setAutoCancel(!downloading)
        .setProgress(if (downloading) 100 else 0, if (downloading) notice?.percent ?: 0 else 0, downloading && notice?.percent == 100)
        .build()
}
