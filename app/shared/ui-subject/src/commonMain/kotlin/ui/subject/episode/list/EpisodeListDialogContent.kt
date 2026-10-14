// SPDX-License-Identifier: AGPL-3.0-only
package me.him188.ani.app.ui.subject.episode.list
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import me.him188.ani.app.ui.lang.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun EpisodeListDialogContent(subjectTitle: String, onDismissRequest: () -> Unit, onCacheClick: () -> Unit,
    episodes: @Composable () -> Unit, onSubjectDetailsClick: (() -> Unit)? = null,
    properties: DialogProperties = DialogProperties(), showMarkWatchedHint: Boolean = false,
    showScrollbar: Boolean = false, scrollbar: @Composable BoxScope.(ScrollState) -> Unit = {}) {
    val scrollState = rememberScrollState()
    val scrollbarEndPadding = if (showScrollbar && scrollState.maxValue > 0) 16.dp else 0.dp
    Dialog(onDismissRequest, properties) {
        Card {
            Box {
                Column(Modifier.padding(16.dp)) {
                    Row {
                        Text(
                            stringResource(Lang.subject_episode_select_play),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Spacer(Modifier.weight(1f))
                    }

                    Row(Modifier.padding(top = 8.dp)) {
                        ProvideTextStyle(MaterialTheme.typography.bodyLarge) {
                            Text(subjectTitle)
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Box(
                        Modifier.weight(1f, fill = false)
                            .heightIn(max = 360.dp), // 特别长需要限制高度并且滚动, #182
                    ) {
                        Column(
                            Modifier.verticalScroll(scrollState).padding(end = scrollbarEndPadding),
                        ) {
                            episodes()

                            Spacer(Modifier.height(16.dp))
                        }

                        Box(Modifier.matchParentSize()) {
                            scrollbar(scrollState)
                        }
                    }

                    HorizontalDivider()
                    Spacer(Modifier.height(16.dp))

                    if (showMarkWatchedHint) Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Lightbulb, null)

                        Text(
                            stringResource(Lang.subject_episode_long_press_mark_watched),
                            Modifier.padding(start = 4.dp),
                        )
                    }

                    Row(Modifier.padding(top = 16.dp).align(Alignment.End)) {
                        onSubjectDetailsClick?.let {
                            TextButton(
                                {
                                    onDismissRequest()
                                    it()
                                },
                            ) {
                                Text(stringResource(Lang.subject_episode_details))
                            }
                        }

                        TextButton(onDismissRequest, Modifier.padding(start = 8.dp)) {
                            Text(stringResource(Lang.subject_episode_close))
                        }
                    }
                }

                IconButton(onCacheClick, Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                    Icon(Icons.Rounded.Download, stringResource(Lang.subject_episode_cache))
                }
            }
        }
    }
}