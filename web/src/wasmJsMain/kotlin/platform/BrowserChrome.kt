package me.him188.ani.app.ui.foundation.theme

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ColorScheme
import com.materialkolor.dynamicColorScheme
import com.materialkolor.PaletteStyle
import me.him188.ani.app.data.models.preference.ThemeSettings

val LocalBrowserThemeSettings = staticCompositionLocalOf { ThemeSettings() }
@Composable
fun appColorScheme(isDark: Boolean): ColorScheme {
    val settings = LocalBrowserThemeSettings.current
    return dynamicColorScheme(
    primary = settings.seedColor,
    isDark = isDark, isAmoled = settings.useBlackBackground, style = PaletteStyle.TonalSpot,
    modifyColorScheme = { modifyColorSchemeForBlackBackground(it, isDark, settings.useBlackBackground) },
)
}

/** Browser chrome uses the App's default opaque surface roles. */
object AniThemeDefaults {
    val navigationContainerColor @Composable get() = MaterialTheme.colorScheme.surfaceContainer
    val pageContentBackgroundColor @Composable get() = MaterialTheme.colorScheme.surfaceContainerLowest
    @Composable fun topAppBarColors(containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest) =
        TopAppBarDefaults.topAppBarColors(containerColor = containerColor, scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer)
}
val LocalAppChromeOverlayInsets = staticCompositionLocalOf { WindowInsets(0) }
fun isAppChromeFrostedGlassActive() = false
fun Modifier.appChromeFrostedGlass(enabled: Boolean, containerColor: Color): Modifier = this
fun Modifier.appChromeHazeSource(backgroundColor: Color): Modifier = this

val LocalDarkOnSurface = staticCompositionLocalOf { Color.Unspecified }
