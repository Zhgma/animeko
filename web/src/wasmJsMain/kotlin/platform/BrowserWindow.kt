@file:OptIn(ExperimentalWasmJsInterop::class)
package me.him188.ani.app.ui.foundation.layout

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.browser.document
import kotlin.js.ExperimentalWasmJsInterop

@Composable
actual fun currentWindowAdaptiveInfo1(): WindowAdaptiveInfo = currentWindowAdaptiveInfo()

/** Browser chrome and safe areas are owned by the hosting document. */
object AniWindowInsets {
    val safeDrawing @Composable get() = WindowInsets(browserInset("left"), browserInset("top"), browserInset("right"), browserInset("bottom"))
    @Composable fun forPageContent() = safeDrawing
    @Composable fun forTopAppBar() = WindowInsets(browserInset("left"), browserInset("top"), browserInset("right"), 0)
    @Composable fun forTopAppBarWithoutDesktopTitle() = forTopAppBar()
    @Composable fun forNavigationRail() = safeDrawing
    @Composable fun forNavigationBar() = WindowInsets(browserInset("left"), 0, browserInset("right"), browserInset("bottom"))
    @Composable fun forNavigationDrawer() = safeDrawing
}

@JsFun("""side => Math.round(parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--safe-' + side)) || 0)""")
private external fun browserInset(side: String): Int

object BrowserWindow {
    val isExactlyMaximized get() = document.fullscreenElement != null
    fun maximize() { document.documentElement?.requestFullscreen() }
    fun floating() { document.exitFullscreen() }
}
val LocalPlatformWindow = staticCompositionLocalOf { BrowserWindow }
