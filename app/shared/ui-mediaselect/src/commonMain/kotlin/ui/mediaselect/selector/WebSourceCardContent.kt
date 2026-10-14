// SPDX-License-Identifier: AGPL-3.0-only
package me.him188.ani.app.ui.mediaselect.selector
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.lang.*
import org.jetbrains.compose.resources.stringResource

/** Source order and the manual-search rescue placement are shared by native and Web hosts. */
@Composable
fun <T> WebSourcesColumnContent(sources: List<T>, failed: (T) -> Boolean,
    onManual: (() -> Unit)?, modifier: Modifier = Modifier, card: @Composable (T) -> Unit) {
    val (errors, others) = sources.partition(failed)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        others.forEach { card(it) }
        if (onManual != null) Box(Modifier.fillMaxWidth().padding(vertical=4.dp), contentAlignment=Alignment.Center) {
            TextButton(onManual,Modifier.testTag("auto_match_rescue_button")) { Text(stringResource(Lang.media_selector_auto_rescue)) }
        }
        errors.forEach { card(it) }
    }
}

@Composable
fun WebSourceChannelsContent(channels: List<String>, selectedIndex: Int?, loading: Boolean,
    error: Boolean, onSelect: (Int) -> Unit, enabled: Boolean = true) {
    channels.forEachIndexed { index, name ->
        InputChip(selected=index==selectedIndex,onClick={onSelect(index)},enabled=enabled,label={Text(name)})
    }
    if (loading) Box(Modifier.minimumInteractiveComponentSize(),contentAlignment=Alignment.Center) {
        CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp)
    }
    if (error) Box(Modifier.minimumInteractiveComponentSize(),contentAlignment=Alignment.Center) {
        Icon(Icons.Rounded.Close,null,tint=MaterialTheme.colorScheme.error)
    }
}

@Composable
fun WebSourceRetryButton(onRefresh: () -> Unit) {
    IconButton(onRefresh) { Icon(Icons.Rounded.Refresh,stringResource(Lang.settings_mediasource_refresh)) }
}

@Composable
fun WebSourceCardContent(name: String, icon: @Composable () -> Unit,
    channels: @Composable FlowRowScope.() -> Unit, trailing: @Composable () -> Unit = {}, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically) {
            icon()
            Box(Modifier.padding(start=8.dp)) {
                Text(name,Modifier.alpha(0f).width(IntrinsicSize.Max),softWrap=true,maxLines=2)
                Text(name,Modifier.matchParentSize(),softWrap=true,maxLines=2,overflow=TextOverflow.Clip)
            }
        }
        FlowRow(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy((-8).dp),content=channels)
        trailing()
    }
}
