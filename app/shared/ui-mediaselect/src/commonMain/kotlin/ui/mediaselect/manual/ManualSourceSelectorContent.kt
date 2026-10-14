// SPDX-License-Identifier: AGPL-3.0-only
package me.him188.ani.app.ui.mediaselect.manual

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.lang.*
import org.jetbrains.compose.resources.stringResource

/** Source selection presentation, independent of the native source manager. */
@Composable
fun <T> ManualSourceSelectorContent(sources: List<T>, selectedId: String?, onSelect: (String) -> Unit,
    id: (T) -> String, name: (T) -> String, icon: @Composable (T) -> Unit,
    tier: @Composable (T, Boolean) -> Unit, modifier: Modifier = Modifier, isPlaceholder: Boolean = false) {
    if(sources.isEmpty()&&!isPlaceholder){
        Text(stringResource(Lang.media_selector_manual_no_sources),modifier,style=MaterialTheme.typography.bodyMedium,
            color=MaterialTheme.colorScheme.onSurfaceVariant);return
    }
    var expanded by remember { mutableStateOf(false) }
    val selected=sources.firstOrNull { id(it)==selectedId }
    ExposedDropdownMenuBox(expanded,{expanded=it&&sources.isNotEmpty()},modifier) {
        OutlinedTextField(selected?.let(name).orEmpty(),{},
            Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth().testTag(ManualBrowsePageTestTags.SOURCE_SELECTOR),
            readOnly=true,singleLine=true,label={Text(stringResource(Lang.media_selector_sources))},
            leadingIcon=selected?.let { { icon(it) } },
            trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(expanded)},
            colors=ExposedDropdownMenuDefaults.outlinedTextFieldColors(),shape=MaterialTheme.shapes.medium)
        ExposedDropdownMenu(expanded,{expanded=false}) {
            sources.forEach { source ->
                val checked=id(source)==selectedId
                DropdownMenuItem(text={Text(name(source),maxLines=1,overflow=TextOverflow.Ellipsis)},
                    onClick={expanded=false;onSelect(id(source))},leadingIcon={icon(source)},
                    modifier=Modifier.then(if(checked) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier)
                        .semantics { this.selected=checked }.testTag(ManualBrowsePageTestTags.sourceItem(id(source))),
                    trailingIcon={Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){
                        tier(source,checked)
                        Box(Modifier.size(24.dp),contentAlignment=Alignment.Center){if(checked)Icon(Icons.Rounded.Check,null)}
                    }},
                    colors=if(checked) MenuDefaults.itemColors(textColor=MaterialTheme.colorScheme.onSecondaryContainer,
                        trailingIconColor=MaterialTheme.colorScheme.onSecondaryContainer) else MenuDefaults.itemColors(),
                    contentPadding=ExposedDropdownMenuDefaults.ItemContentPadding)
            }
        }
    }
}
