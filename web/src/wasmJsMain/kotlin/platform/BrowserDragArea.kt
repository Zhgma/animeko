package me.him188.ani.app.ui.foundation.interaction
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Web content cannot drag the browser window. */
@Composable
fun WindowDragArea(modifier: Modifier = Modifier, content: @Composable () -> Unit) = Box(modifier) { content() }
