// SPDX-License-Identifier: AGPL-3.0-only
package me.him188.ani.app.navigation
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import kotlinx.serialization.Serializable
import me.him188.ani.app.ui.lang.*
import org.jetbrains.compose.resources.stringResource

@Serializable
enum class MainScreenPage {
    Exploration,
    Collection,
    CacheManagement,
    ;

    companion object {
        @Stable
        val visibleEntries get() = entries
    }
}


@Stable
fun MainScreenPage.getIcon() = when (this) {
    MainScreenPage.Exploration -> Icons.Rounded.TravelExplore
    MainScreenPage.Collection -> Icons.Rounded.Star
    MainScreenPage.CacheManagement -> Icons.Rounded.DownloadDone
}

@Stable
@Composable
fun MainScreenPage.getText(): String = when (this) {
    MainScreenPage.Exploration -> stringResource(Lang.main_screen_page_exploration)
    MainScreenPage.Collection -> stringResource(Lang.main_screen_page_collection)
    MainScreenPage.CacheManagement -> stringResource(Lang.main_screen_page_cache_management)
}
