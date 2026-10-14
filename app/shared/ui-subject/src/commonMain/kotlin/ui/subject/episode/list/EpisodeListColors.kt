// SPDX-License-Identifier: AGPL-3.0-only
package me.him188.ani.app.ui.subject.episode.list
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import me.him188.ani.app.data.models.preference.EpisodeListProgressTheme
import me.him188.ani.app.ui.foundation.theme.stronglyWeaken
import me.him188.ani.app.ui.foundation.theme.weaken

@Immutable
class EpisodeListColors(
    /**
     * 看过或抛弃的颜色
     */
    val doneOrDroppedColor: Color,
    /**
     * 可以看但还没看
     */
    val canWatchColor: Color,
    /**
     * 未开播颜色
     */
    val notPublishedColor: Color,
) {
    fun containerColor(isDoneOrDropped: Boolean, isBroadcast: Boolean): Color = when {
        isDoneOrDropped -> doneOrDroppedColor
        !isBroadcast -> notPublishedColor
        else -> canWatchColor
    }
}

object EpisodeListDefaults {
    @Composable
    fun colors(
        theme: EpisodeListProgressTheme = EpisodeListProgressTheme.Default,
        action: Color = MaterialTheme.colorScheme.primary,
        disabled: Color = MaterialTheme.colorScheme.onSurface.stronglyWeaken(),
    ): EpisodeListColors {
        val dark = action.weaken()
        return when (theme) {
            EpisodeListProgressTheme.ACTION -> EpisodeListColors(
                doneOrDroppedColor = dark,
                canWatchColor = action,
                notPublishedColor = disabled,
            )

            EpisodeListProgressTheme.LIGHT_UP -> EpisodeListColors(
                doneOrDroppedColor = action,
                canWatchColor = dark,
                notPublishedColor = disabled,
            )
        }
    }
}

