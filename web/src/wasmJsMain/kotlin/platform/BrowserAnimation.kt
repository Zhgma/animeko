package me.him188.ani.app.ui.foundation.animation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.runtime.Composable

/** Browser transitions use Compose defaults without native window motion settings. */
@Composable
fun AniAnimatedVisibility(visible: Boolean, content: @Composable AnimatedVisibilityScope.() -> Unit) {
    AnimatedVisibility(visible, content = content)
}
