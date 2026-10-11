/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.settings.account
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.window.core.layout.WindowSizeClass
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.isWidthAtLeastMedium

@Composable
fun ProfilePopupContainer(onDismissRequest: () -> Unit, windowSizeClass: WindowSizeClass = currentWindowAdaptiveInfo1().windowSizeClass, content: @Composable () -> Unit) {
    if (windowSizeClass.isWidthAtLeastMedium) {
        val density = LocalDensity.current
        Popup(
            alignment = Alignment.TopEnd,
            offset = with(density) {
                IntOffset(0, 32.dp.roundToPx())
            },
            properties = PopupProperties(),
            onDismissRequest = onDismissRequest,
        ) {
            // 模拟点击外面关闭 popup, 否则事件会被广播到下层
            Box(
                Modifier.fillMaxSize()
                    .clickable(interactionSource = null, indication = null, onClick = onDismissRequest)
                    .background(Color.Black.copy(alpha = 0.32f)),
                contentAlignment = Alignment.TopEnd,
            ) {

                // 实际内容
                Surface(
                    modifier = Modifier
                        .windowInsetsPadding(AniWindowInsets.safeDrawing)
                        .padding(horizontal = 24.dp)
                        .widthIn(max = 360.dp)
                        .clickable(interactionSource = null, indication = null, onClick = {}), // 避免触发 onDismissRequest
                    shape = MaterialTheme.shapes.extraLarge,
                    color = BottomSheetDefaults.ContainerColor,
                    contentColor = contentColorFor(BottomSheetDefaults.ContainerColor),
                    tonalElevation = 0.dp,
                ) {
                    content()
                }
            }
        }

    } else {
        BasicAlertDialog(onDismissRequest) {
            Surface(
                modifier = Modifier,
                shape = MaterialTheme.shapes.extraLarge,
                color = BottomSheetDefaults.ContainerColor,
                contentColor = contentColorFor(BottomSheetDefaults.ContainerColor),
                tonalElevation = 0.dp,
            ) {
                content()
            }
        }
    }

}
