/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.foundation.theme
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

fun modifyColorSchemeForBlackBackground(
    colorScheme: ColorScheme,
    isDark: Boolean,
    useBlackBackground: Boolean,
): ColorScheme {
    return if (isDark && useBlackBackground) {
        colorScheme.copy(
            background = Color.Black,
            onBackground = Color.White,

            surface = Color.Black,
            onSurface = Color.White,
            surfaceContainerLowest = Color.Black,

            surfaceVariant = Color.Black,
            onSurfaceVariant = Color.White,
        )
    } else colorScheme
}

