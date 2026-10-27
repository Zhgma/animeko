/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.layout

import me.him188.ani.app.platform.Context
import me.him188.ani.app.platform.MainViewControllerPropertyProvider
import me.him188.ani.app.platform.SwiftBridge
import platform.Foundation.NSThread
import platform.UIKit.UIApplication
import platform.UIKit.UINavigationController
import platform.UIKit.attemptRotationToDeviceOrientation
import platform.UIKit.setNeedsUpdateOfHomeIndicatorAutoHidden
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

actual suspend fun Context.setRequestFullScreen(
    window: PlatformWindowMP,
    fullscreen: Boolean,
    lockLandscape: Boolean,
) {
    ensureMainThread {
        // 阅读器跟随设备方向, 视频全屏才请求横屏.
        if (lockLandscape) {
            SwiftBridge.setDeviceOrientation(window.uiViewController, fullscreen)
            UINavigationController.attemptRotationToDeviceOrientation()
        }
        UIApplication.sharedApplication.idleTimerDisabled = fullscreen
    }
}

actual fun Context.setSystemBarVisible(window: PlatformWindowMP, visible: Boolean) {
    ensureMainThread {
        window.uiViewController.statusBarHidden = !visible

        MainViewControllerPropertyProvider.prefersHomeIndicatorAutoHidden = !visible
        window.uiViewController.setNeedsUpdateOfHomeIndicatorAutoHidden()
    }
}

/**
 * Utility to ensure the given block runs on the main thread.
 */
private inline fun ensureMainThread(crossinline block: () -> Unit) {
    if (NSThread.isMainThread()) {
        block()
    } else {
        dispatch_async(dispatch_get_main_queue()) {
            block()
        }
    }
}
