/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.reading

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.Structure
import com.sun.jna.win32.StdCallLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal actual fun rememberReaderBatteryLevel(): Int? {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val level by produceState<Int?>(null, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                value = withContext(Dispatchers.IO) { runCatching { desktopBatteryLevel() }.getOrNull() }
                delay(30_000)
            }
        }
    }
    return level
}

private fun desktopBatteryLevel(): Int? = when {
    Platform.isWindows() -> {
        val status = ReaderPowerStatus()
        if (readerPowerApi.GetSystemPowerStatus(status) == 0 || status.batteryFlag.toInt() and 128 != 0) null
        else (status.batteryLifePercent.toInt() and 255).takeIf { it in 0..100 }
    }
    Platform.isLinux() -> File("/sys/class/power_supply").listFiles()?.firstOrNull {
        File(it, "type").readText().trim() == "Battery"
    }?.let { File(it, "capacity").readText().trim().toIntOrNull()?.takeIf { level -> level in 0..100 } }
    else -> null
}

private val readerPowerApi by lazy { Native.load("kernel32", ReaderPowerApi::class.java) }
internal interface ReaderPowerApi : StdCallLibrary {
    fun GetSystemPowerStatus(status: ReaderPowerStatus): Int
}

@Structure.FieldOrder("acLineStatus", "batteryFlag", "batteryLifePercent", "systemStatusFlag", "batteryLifeTime", "batteryFullLifeTime")
internal class ReaderPowerStatus : Structure() {
    @JvmField var acLineStatus: Byte = 0
    @JvmField var batteryFlag: Byte = 0
    @JvmField var batteryLifePercent: Byte = 0
    @JvmField var systemStatusFlag: Byte = 0
    @JvmField var batteryLifeTime: Int = 0
    @JvmField var batteryFullLifeTime: Int = 0
}
