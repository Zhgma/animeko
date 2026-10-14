/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.subject.details.sections
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.lang.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun SubjectCommentsHeader(onWriteReview: () -> Unit) {
Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(Lang.subject_details_tab_comments),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                )
                TextButton(onWriteReview) {
                    Icon(Icons.Rounded.AddComment, contentDescription = null, Modifier.size(18.dp))
                    Text(
                        stringResource(Lang.subject_details_write_review),
                        Modifier.padding(start = 8.dp),
                    )
                }
            }
}
