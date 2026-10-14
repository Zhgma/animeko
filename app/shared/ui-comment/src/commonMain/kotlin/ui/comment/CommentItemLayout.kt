/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.comment

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object CommentItemTestTags {
    const val LikeButton = "CommentItem:like"
    const val DislikeButton = "CommentItem:dislike"
    const val EmojiButton = "CommentItem:emoji"
    const val MoreButton = "CommentItem:more"
    const val Actions = "CommentItem:actions"
    const val RepliesBlock = "CommentItem:replies"
}

@Composable
fun CommentItemLayout(
    avatar: @Composable () -> Unit,
    title: @Composable RowScope.() -> Unit,
    content: @Composable () -> Unit,
    timestamp: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    titleTrailing: (@Composable RowScope.() -> Unit)? = null,
    reactions: (@Composable () -> Unit)? = null,
    replies: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.clip(CircleShape)) {
            avatar()
        }
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(Modifier.weight(1f, fill = false)) {
                    title()
                }
                titleTrailing?.invoke(this)
            }

            Box(Modifier.padding(vertical = 3.dp)) {
                content()
            }

            reactions?.invoke()

            replies?.invoke()

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(Modifier.weight(1f, fill = false)) {
                    timestamp()
                }
                if (actions != null) {
                    CompositionLocalProvider(
                        LocalMinimumInteractiveComponentSize provides Dp.Unspecified,
                    ) {
                        Row(
                            Modifier.testTag(CommentItemTestTags.Actions),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            actions()
                        }
                    }
                }
            }
        }
    }
}

