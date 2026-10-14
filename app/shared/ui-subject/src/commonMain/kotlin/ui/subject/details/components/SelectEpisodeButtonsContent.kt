// SPDX-License-Identifier: AGPL-3.0-only
package me.him188.ani.app.ui.subject.details.components
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dataset
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SelectEpisodeButtonsContent(onShowEpisodeList: () -> Unit, modifier: Modifier = Modifier,
    listContentDescription: String? = null, progressButton: @Composable () -> Unit) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(onShowEpisodeList) {
            Icon(Icons.Outlined.Dataset, listContentDescription)
        }

        Box(Modifier.weight(1f)) {
            progressButton()
        }
    }
}
