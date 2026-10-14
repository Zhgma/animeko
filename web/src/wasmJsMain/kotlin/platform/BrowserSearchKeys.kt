package me.him188.ani.app.ui.foundation.interaction
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
inline fun Modifier.onEnterKeyEvent(crossinline action: (KeyEvent) -> Boolean): Modifier = onPreviewKeyEvent {
    if (it.key == Key.Enter && it.type == KeyEventType.KeyUp) action(it) else false
}
