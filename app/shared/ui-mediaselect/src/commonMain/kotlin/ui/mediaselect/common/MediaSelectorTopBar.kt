// SPDX-License-Identifier: AGPL-3.0-only
package me.him188.ani.app.ui.mediaselect.common
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import me.him188.ani.app.ui.foundation.layout.Zero
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.subject_episode_close_selector
import me.him188.ani.app.ui.mediaselect.MediaSelectorMode
import org.jetbrains.compose.resources.stringResource

object MediaSelectorDialogTestTags {
    const val SURFACE = "media_selector_dialog_surface"
    const val CLOSE = "media_selector_dialog_close"
}

/** Shared selector title, mode menu and close action for media and manga hosts. */
@Composable
fun MediaSelectorTopBar(
    title: String, mode: MediaSelectorMode, onModeChange: (MediaSelectorMode) -> Unit, onClose: () -> Unit,
    showBt: Boolean = true, extraActions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title) },
        actions = {
            extraActions()
            MediaSelectorModeChip(mode, onModeChange, showBt = showBt)
            IconButton(onClose, Modifier.testTag(MediaSelectorDialogTestTags.CLOSE)) {
                Icon(Icons.Rounded.Close, stringResource(Lang.subject_episode_close_selector))
            }
        },
        windowInsets = WindowInsets.Zero,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = BottomSheetDefaults.ContainerColor),
    )
}
