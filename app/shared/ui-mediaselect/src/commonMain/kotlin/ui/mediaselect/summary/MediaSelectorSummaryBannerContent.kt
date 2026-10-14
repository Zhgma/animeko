// SPDX-License-Identifier: AGPL-3.0-only
package me.him188.ani.app.ui.mediaselect.summary
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MediaSelectorSummaryBannerContent(onClickSwitchSource: () -> Unit, modifier: Modifier = Modifier,
    content: @Composable () -> Unit) {
    Card(onClick=onClickSwitchSource, modifier=modifier.fillMaxWidth(),
        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow),
        shape=MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().heightIn(min=40.dp).padding(horizontal=16.dp,vertical=8.dp),
            verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { content() }
            Icon(Icons.Rounded.SyncAlt, "切换数据源", Modifier.size(20.dp))
        }
    }
}
