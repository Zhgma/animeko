// SPDX-License-Identifier: AGPL-3.0-only
package me.him188.ani.app.videoplayer.ui.progress
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.him188.ani.app.videoplayer.ui.keepLayoutWhenHidden

/**
 * The controller bar of a video player. Usually at the bottom of the screen (the video player).
 *
 * See [PlayerControllerDefaults] for components.
 *
 * @param startActions [PlayerControllerDefaults.PlaybackIcon], [PlayerControllerDefaults.DanmakuIcon]
 * @param progressIndicator [MediaProgressIndicatorText]
 * @param progressSlider [MediaProgressSlider]
 * @param danmakuEditor [PlayerControllerDefaults.DanmakuTextField]
 * @param endActions [PlayerControllerDefaults.FullscreenIcon]
 * @param expanded Whether the controller bar is expanded.
 * If `true`, the [progressIndicator] and [progressSlider] will be shown on a separate row above. The bottom row will contain a [danmakuEditor].
 * If `false`, the entire bar will be only one row. [danmakuEditor] will be ignored.
 * @param topActions Actions shown at the top end of the [PlayerControllerBarLayout.VerticalSplit] layout.
 * @param layout 控制器内容在可用空间内的排列方式.
 * @param sliderOnly Whether to keep only [progressSlider] visible without replacing its composition.
 */
@Composable
fun PlayerControllerBar(
    startActions: @Composable RowScope.() -> Unit,
    progressIndicator: @Composable RowScope.() -> Unit,
    progressSlider: @Composable RowScope.() -> Unit,
    danmakuEditor: @Composable RowScope.() -> Unit,
    endActions: @Composable RowScope.() -> Unit,
    expanded: Boolean,
    topActions: @Composable RowScope.() -> Unit = {},
    layout: PlayerControllerBarLayout = PlayerControllerBarLayout.Standard,
    sliderOnly: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (layout == PlayerControllerBarLayout.VerticalSplit) {
        VerticalSplitPlayerControllerBar(
            startActions = startActions,
            progressIndicator = progressIndicator,
            progressSlider = progressSlider,
            danmakuEditor = danmakuEditor,
            endActions = endActions,
            topActions = topActions,
            sliderOnly = sliderOnly,
            modifier = modifier,
        )
        return
    }

    Column(
        modifier
            .clickable(remember { MutableInteractionSource() }, null, onClick = {}) // Consume touch event
            .padding(
                horizontal = if (expanded) 8.dp else 4.dp,
                vertical = if (expanded) 4.dp else 2.dp,
            ),
    ) {
        Column {
            ProvideTextStyle(MaterialTheme.typography.labelMedium) {
                Row(
                    Modifier
                        .keepLayoutWhenHidden(sliderOnly)
                        .padding(start = if (expanded) 8.dp else 4.dp)
                        .padding(vertical = if (expanded) 4.dp else 2.dp),
                ) {
                    progressIndicator()
                }
                if (expanded) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        progressSlider()
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (expanded) 8.dp else 4.dp),
        ) {
            // 播放 / 暂停按钮
            Row(
                Modifier.keepLayoutWhenHidden(sliderOnly),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                startActions()
            }

            Row(
                Modifier.weight(1f).keepLayoutWhenHidden(sliderOnly && expanded),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (expanded) {
                    ProvideTextStyle(MaterialTheme.typography.labelSmall) {
                        danmakuEditor()
                    }
                } else {
                    progressSlider()
                }
            }

            Row(
                Modifier.keepLayoutWhenHidden(sliderOnly),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                endActions()
            }
        }
    }
}

enum class PlayerControllerBarLayout {
    Standard,
    VerticalSplit,
}

@Composable
private fun VerticalSplitPlayerControllerBar(
    startActions: @Composable RowScope.() -> Unit,
    progressIndicator: @Composable RowScope.() -> Unit,
    progressSlider: @Composable RowScope.() -> Unit,
    danmakuEditor: @Composable RowScope.() -> Unit,
    endActions: @Composable RowScope.() -> Unit,
    topActions: @Composable RowScope.() -> Unit,
    sliderOnly: Boolean,
    modifier: Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().keepLayoutWhenHidden(sliderOnly),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            topActions()
        }
        Row(
            Modifier.fillMaxWidth().weight(1f).keepLayoutWhenHidden(sliderOnly),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            startActions()
        }
        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp).keepLayoutWhenHidden(sliderOnly),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProvideTextStyle(MaterialTheme.typography.labelSmall) {
                danmakuEditor()
            }
        }
        ProvideTextStyle(MaterialTheme.typography.labelMedium) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                progressSlider()
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                progressIndicator()
                Spacer(Modifier.weight(1f))
                Row(Modifier.keepLayoutWhenHidden(sliderOnly)) {
                    endActions()
                }
            }
        }
    }
}
