// SPDX-License-Identifier: AGPL-3.0-only
package me.him188.ani.app.ui.subject.collection.progress
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

val LocalSubjectProgressButtonPadding = staticCompositionLocalOf<PaddingValues?> { null }

@Composable
fun SubjectProgressButtonContent(text: String, isPrimary: Boolean, onPlay: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true) {
    val contentPadding = LocalSubjectProgressButtonPadding.current ?: ButtonDefaults.ContentPadding
    val requiredWidth = Modifier.requiredWidth(IntrinsicSize.Max)
    Crossfade(isPrimary) { isPrimary ->
        if (isPrimary) {
            Button(onClick = onPlay, modifier, enabled = enabled, contentPadding = contentPadding) {
                Text(text, requiredWidth, softWrap = false)
            }
        } else {
            FilledTonalButton(onClick = onPlay, modifier, enabled = enabled, contentPadding = contentPadding) {
                Text(text, requiredWidth, softWrap = false)
            }
        }
    }
}
