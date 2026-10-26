/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.android

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.view.MotionEvent
import android.view.InputDevice
import android.os.SystemClock
import android.os.ParcelFileDescriptor
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.io.files.Path
import me.him188.ani.android.activity.MainActivity
import me.him188.ani.app.tools.MonoTasker
import me.him188.ani.app.tools.update.DefaultFileDownloader
import me.him188.ani.app.tools.update.FileDownloaderState
import me.him188.ani.app.ui.update.AppUpdateViewModel
import me.him188.ani.app.ui.update.NewVersion
import me.him188.ani.app.ui.update.UpdateDownloadUiRequests
import me.him188.ani.utils.io.inSystem
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

/** Real Android notifications and Activity intents, driven by transfer states without network or installation. */
class UpdateDownloadNotificationDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val automation = instrumentation.uiAutomation
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val version = "notification-ui-test"

    @Test
    fun notificationProgressAndRepeatedForegroundRoundTrips() {
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        shell("input keyevent KEYCODE_WAKEUP")
        shell("wm dismiss-keyguard")
        val activity = instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as MainActivity
        lateinit var vm: AppUpdateViewModel
        instrumentation.runOnMainSync {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            vm = ViewModelProvider(activity)[AppUpdateViewModel::class.java]
        }
        await("global notification collector") { field<Boolean>(vm, "notificationsBound") }
        val downloader = vm.javaClass.getDeclaredMethod("getFileDownloader").apply { isAccessible = true }
            .invoke(vm) as DefaultFileDownloader
        val progress = field<MutableStateFlow<Float>>(downloader, "_progress")
        // Isolated state injection avoids introducing a production test entry point or making a download request.
        fun state(value: FileDownloaderState, percent: Float = 0.25f) {
            instrumentation.runOnMainSync {
                field<MonoTasker>(vm, "autoCheckTasker").cancel()
                field<MutableStateFlow<Long>>(vm, "lastCheckTime").value = System.currentTimeMillis()
                field<MutableStateFlow<NewVersion?>>(vm, "latestVersionFlow").value =
                    NewVersion(version, emptyList(), emptyList(), "")
                progress.value = percent
                downloader.state.value = value
            }
        }
        try {
            state(FileDownloaderState.Downloading)
            await("notification at 25 percent") { notification()?.extras?.getInt(Notification.EXTRA_PROGRESS) == 25 }
            shell("cmd statusbar expand-notifications")
            await("notification drawer") { contains(version) }
            screenshot("notification-25")
            openNotification()
            await("download window") { contains("后台下载") || contains("Download in Background") }
            screenshot("foreground-download")
            clickBackground()
            await("hidden download window") { !contains("后台下载") && !contains("Download in Background") }
            shell("input keyevent KEYCODE_HOME")

            state(FileDownloaderState.Downloading, 0.65f)
            await("background progress at 65 percent") { notification()?.extras?.getInt(Notification.EXTRA_PROGRESS) == 65 }
            shell("cmd statusbar expand-notifications")
            await("notification drawer again") { contains(version) }
            screenshot("notification-65-background")
            openNotification()
            await("reopened window") { contains("后台下载") || contains("Download in Background") }
            screenshot("foreground-again")
            clickBackground()
            await("hidden again") { !contains("后台下载") && !contains("Download in Background") }

            state(FileDownloaderState.Succeed("", Path(context.cacheDir.resolve("not-installed.apk").path).inSystem, true), 1f)
            await("completed notification") {
                notification()?.let { it.flags and Notification.FLAG_ONGOING_EVENT == 0 &&
                        (it.extras.getCharSequence(Notification.EXTRA_TITLE).toString().contains("下载完成") ||
                         it.extras.getCharSequence(Notification.EXTRA_TITLE).toString().contains("ready")) } == true
            }
            shell("cmd statusbar expand-notifications")
            await("completion in drawer") { contains(version) }
            screenshot("notification-completed")
            openNotification()
            await("completed window") { contains("下载完成") || contains("Download complete") }
            screenshot("foreground-completed")

            state(FileDownloaderState.Failed(IllegalStateException("Controlled notification UI failure")))
            await("failure notification") {
                notification()?.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.let {
                    it.contains("下载失败") || it.contains("failed")
                } == true
            }
            shell("cmd statusbar expand-notifications")
            await("failure in drawer") { contains(version) }
            screenshot("notification-failed")
            openNotification()
            await("failed window") { contains("Controlled notification UI failure") }
            screenshot("foreground-failed")
        } catch (failure: Throwable) {
            screenshot("test-failure")
            throw failure
        } finally {
            instrumentation.runOnMainSync {
                downloader.state.value = FileDownloaderState.Cancelled(CancellationException("UI test finished"))
            }
            await("notification removed after cancellation") { notification() == null }
            shell("cmd statusbar collapse")
        }
    }

    private fun notification(): Notification? = manager.activeNotifications.firstOrNull { it.id == 116 }?.notification

    private fun openNotification() {
        val before = UpdateDownloadUiRequests.foregroundRequests.value
        clickText(version)
        await("notification Activity intent") { UpdateDownloadUiRequests.foregroundRequests.value > before }
        await("application in foreground") { automation.rootInActiveWindow?.packageName == context.packageName }
        SystemClock.sleep(500)
    }

    private fun clickBackground() {
        clickText(if (contains("后台下载")) "后台下载" else "Download in Background")
    }

    private fun find(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.text?.toString()?.contains(text) == true || node.contentDescription?.toString()?.contains(text) == true) return node
        for (i in 0 until node.childCount) find(node.getChild(i), text)?.let { return it }
        return null
    }

    private fun findText(text: String): AccessibilityNodeInfo? =
        find(automation.rootInActiveWindow, text)

    private fun contains(text: String) = findText(text) != null

    private fun clickText(text: String) {
        var node = findText(text)
        while (node != null && !node.isClickable) node = node.parent
        val bounds = Rect()
        requireNotNull(node) { "Clickable node: $text" }.getBoundsInScreen(bounds)
        val time = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(time, SystemClock.uptimeMillis(), action, bounds.exactCenterX(), bounds.exactCenterY(), 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            assertTrue("Touch $text", automation.injectInputEvent(event, true))
            event.recycle()
            SystemClock.sleep(100)
        }
    }

    private fun screenshot(name: String) {
        SystemClock.sleep(500)
        val image = automation.takeScreenshot() ?: error("Screenshot unavailable")
        val directory = File(context.getExternalFilesDir(null), "update-notification-verification").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }

    private fun shell(command: String) {
        ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
        SystemClock.sleep(500)
    }

    private fun await(description: String, predicate: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 15000
        while (SystemClock.uptimeMillis() < end) {
            if (predicate()) return
            SystemClock.sleep(100)
        }
        error("Timed out waiting for $description")
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> field(instance: Any, name: String): T =
        instance.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(instance) as T
}
