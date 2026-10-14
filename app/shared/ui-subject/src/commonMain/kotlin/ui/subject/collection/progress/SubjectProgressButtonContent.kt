// SPDX-License-Identifier: AGPL-3.0-only
package me.him188.ani.app.ui.subject.collection.progress
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun SubjectProgressButtonContent(text: String, isPrimary: Boolean, onPlay: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true) {
    val requiredWidth = Modifier.requiredWidth(IntrinsicSize.Max)
    Crossfade(isPrimary) { isPrimary ->
        if (isPrimary) {
            Button(onClick = onPlay, modifier, enabled = enabled) {
                Text(text, requiredWidth, softWrap = false)
            }
        } else {
            FilledTonalButton(onClick = onPlay, modifier, enabled = enabled) {
                Text(text, requiredWidth, softWrap = false)
            }
        }
    }
}
