/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.subject.details.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.layout.paddingIfNotEmpty

@Composable
fun SubjectDetailsHeaderCompactContent(
    title: @Composable () -> Unit,
    subtitle: @Composable () -> Unit,
    seasonTags: @Composable () -> Unit,
    collectionData: @Composable () -> Unit,
    collectionAction: @Composable () -> Unit,
    selectEpisodeButton: @Composable BoxScope.() -> Unit,
    rating: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    cover: @Composable () -> Unit,
) {
    Column(modifier) {
        Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.Top) {
            cover()

            Column(
                Modifier.weight(1f, fill = true)
                    .padding(horizontal = 12.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth(), // required by Rating
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    var showSubtitle by remember { mutableStateOf(false) }
                    Box(Modifier.clickable { showSubtitle = !showSubtitle }) {
                        ProvideTextStyle(MaterialTheme.typography.titleLarge) {
                            if (showSubtitle) {
                                subtitle()
                            } else {
                                title()
                            }
                        }
                    }

                    ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                        seasonTags()
                    }

                    Spacer(Modifier.weight(1f))

                    Row(
                        Modifier.requiredHeight(IntrinsicSize.Max).align(Alignment.End),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        rating()
                    }
                }
            }
        }

        Row(
            Modifier.padding(top = 16.dp).align(Alignment.Start),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f),
                verticalAlignment = Alignment.Bottom,
            ) {
                collectionData()
            }
            collectionAction()
        }

        Box(Modifier.paddingIfNotEmpty(top = 8.dp), contentAlignment = Alignment.CenterEnd) {
            selectEpisodeButton()
        }
    }
}
