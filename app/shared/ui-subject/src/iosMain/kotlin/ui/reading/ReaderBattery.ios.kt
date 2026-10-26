/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.reading

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import platform.UIKit.UIDevice
import kotlin.math.roundToInt

@Composable
internal actual fun rememberReaderBatteryLevel(): Int? {
    val device = UIDevice.currentDevice
    DisposableEffect(device) {
        val previous = device.batteryMonitoringEnabled
        device.batteryMonitoringEnabled = true
        onDispose { device.batteryMonitoringEnabled = previous }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val level by produceState<Int?>(null, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                value = device.batteryLevel.takeIf { it >= 0f }?.let { (it * 100).roundToInt().coerceIn(0, 100) }
                delay(30_000)
            }
        }
    }
    return level
}
