/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.reading

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import me.him188.ani.app.data.repository.reading.ReaderStatusSettingsStore
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.data.persistent.dataStores
import kotlin.time.Clock

@Composable
internal fun rememberReaderStatusPreference(): Pair<Boolean, (Boolean) -> Unit> {
    val context = LocalContext.current
    val store = remember(context) { ReaderStatusSettingsStore(context.dataStores.preferencesStore) }
    val enabled by store.enabled.collectAsStateWithLifecycle(initialValue = false)
    val scope = rememberCoroutineScope()
    return enabled to { value -> scope.launch { store.setEnabled(value) }; Unit }
}

@Composable
internal fun ReaderStatusSetting(enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().testTag("reader-status-setting")
            .toggleable(enabled, role = Role.Switch, onValueChange = onChange).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("全屏显示电量和时间", Modifier.weight(1f))
        Switch(enabled, onCheckedChange = null)
    }
}

internal fun readerClockText(hour: Int, minute: Int): String =
    hour.toString().padStart(2, '0') + ":" + minute.toString().padStart(2, '0')

/** 独立于阅读控制栏, 占用底部空间以避免遮住正文或漫画. */
@Composable
internal fun ReaderStatusFooter(background: Color, foreground: Color) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val now by produceState(Clock.System.now(), lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                value = Clock.System.now()
                delay(60_000L - value.toEpochMilliseconds().mod(60_000L))
            }
        }
    }
    val local = now.toLocalDateTime(TimeZone.currentSystemDefault())
    ReaderStatusContent(readerClockText(local.hour, local.minute), rememberReaderBatteryLevel(), background, foreground)
}

@Composable
internal fun ReaderStatusContent(time: String, battery: Int?, background: Color, foreground: Color) {
    Row(
        Modifier.fillMaxWidth().background(background).testTag("reader-status-footer")
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = foreground.copy(alpha = 0.7f)
        Canvas(Modifier.size(22.dp, 12.dp).testTag("reader-status-battery").semantics {
            contentDescription = battery?.let { "电量 $it%" } ?: "电量未知"
        }) {
            val stroke = 1.dp.toPx()
            val terminal = 2.dp.toPx()
            val bodyWidth = size.width - terminal - stroke
            drawRoundRect(tint, Offset(stroke / 2, stroke / 2), Size(bodyWidth, size.height - stroke),
                CornerRadius(2.dp.toPx()), style = Stroke(stroke))
            drawRect(tint, Offset(size.width - terminal, size.height * 0.3f), Size(terminal, size.height * 0.4f))
            if (battery != null) {
                drawRect(tint, Offset(stroke * 2, stroke * 2),
                    Size((bodyWidth - stroke * 3) * battery.coerceIn(0, 100) / 100f, size.height - stroke * 4))
            } else {
                drawLine(tint, Offset(bodyWidth * 0.35f, size.height / 2),
                    Offset(bodyWidth * 0.65f, size.height / 2), stroke)
            }
        }
        Text(time, color = tint, fontSize = 11.sp, style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.testTag("reader-status-time"))
    }
}

@Composable
internal expect fun rememberReaderBatteryLevel(): Int?
