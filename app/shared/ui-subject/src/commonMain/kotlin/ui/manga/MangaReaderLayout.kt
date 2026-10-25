// SPDX-License-Identifier: AGPL-3.0-only
package me.him188.ani.app.ui.manga
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.subject.episode.EpisodeScreenLayout
import me.him188.ani.app.ui.subject.episode.EpisodeScreenLayoutMode
@Composable
internal fun MangaReaderLayout(fullscreen: Boolean, modifier: Modifier = Modifier,
    reader: @Composable (landscape: Boolean) -> Unit,
    details: @Composable (scrollModifier: Modifier, pullToRefreshEnabled: Boolean) -> Unit,
) {
    var readerHeightFraction by rememberSaveable { mutableFloatStateOf(1f) }
    BoxWithConstraints(modifier.fillMaxSize().testTag("manga-reader")) {
        val landscape = maxWidth > maxHeight
        val expanded = maxWidth >= 840.dp || (maxWidth >= 600.dp && maxHeight < 480.dp)
        val mode = when {
            fullscreen -> EpisodeScreenLayoutMode.VIDEO_ONLY
            expanded -> EpisodeScreenLayoutMode.WIDE
            else -> EpisodeScreenLayoutMode.COMPACT
        }
        val collapsible = mode == EpisodeScreenLayoutMode.COMPACT && !landscape
        val expandedReaderHeight = maxHeight * 0.7f
        val expandedReaderHeightPx = with(LocalDensity.current) { expandedReaderHeight.toPx() }
        // Only the details list dispatches to this connection. Reading gestures stay independent.
        val detailsScroll = remember(collapsible, expandedReaderHeightPx) {
            object : NestedScrollConnection {
                fun resize(delta: Float): Offset {
                    if (!collapsible || expandedReaderHeightPx <= 0f) return Offset.Zero
                    val previous = readerHeightFraction * expandedReaderHeightPx
                    val next = (previous + delta).coerceIn(0f, expandedReaderHeightPx)
                    readerHeightFraction = next / expandedReaderHeightPx
                    return Offset(0f, next - previous)
                }

                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                    if (available.y < 0f) resize(available.y) else Offset.Zero

                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                    if (available.y > 0f) resize(available.y) else Offset.Zero
            }
        }
        EpisodeScreenLayout(mode, video = {
            Box(Modifier.then(when {
                collapsible -> Modifier.fillMaxWidth().height(expandedReaderHeight * readerHeightFraction)
                mode == EpisodeScreenLayoutMode.COMPACT -> Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                else -> Modifier.fillMaxSize()
            }).clipToBounds().background(Color(0xff101014)).testTag("manga-reading-pane")) { reader(landscape) }
        }, secondary = { details(Modifier.nestedScroll(detailsScroll), !collapsible || readerHeightFraction >= 1f)
        }, modifier = Modifier.fillMaxSize().testTag("manga-player-layout-$mode"))
    }
}
