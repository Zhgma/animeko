// SPDX-License-Identifier: AGPL-3.0-only
package me.him188.ani.app.videoplayer.ui
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics

internal fun Modifier.keepLayoutWhenHidden(hidden: Boolean): Modifier {
    if (!hidden) return this
    return alpha(0f)
        .clearAndSetSemantics { }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            }
        }
}