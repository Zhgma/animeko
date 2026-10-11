// SPDX-License-Identifier: AGPL-3.0-only
package me.him188.ani.app.ui.settings.tabs.app

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import me.him188.ani.app.data.models.preference.*
import me.him188.ani.app.navigation.MainScreenPage
import me.him188.ani.app.navigation.getIcon
import me.him188.ani.app.navigation.getText
import me.him188.ani.app.ui.settings.framework.components.*
import me.him188.ani.app.ui.lang.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingsScope.AppearanceGroupContent(
    uiSettings: UISettings,
    onChange: (UISettings) -> Unit,
    showDesktopCloseBehavior: Boolean = false,
    supportsCollectionAnimation: Boolean = true,
    supportsEpisodeAppearance: Boolean = true,
    supportsCollectionSearchFilter: Boolean = true,
    languageSettings: @Composable () -> Unit = {},
) {
    languageSettings()

    DropdownItem(
        selected = { uiSettings.mainSceneInitialPage },
        values = { MainScreenPage.visibleEntries },
        itemText = { Text(it.getText()) },
        onSelect = {
            onChange(uiSettings.copy(mainSceneInitialPage = it))
        },
        itemIcon = { Icon(it.getIcon(), null) },
        title = { Text(stringResource(Lang.settings_app_initial_page)) },
        description = { Text(stringResource(Lang.settings_app_initial_page_description)) },
    )
    if (showDesktopCloseBehavior) {
        DropdownItem(
            selected = { uiSettings.desktopCloseBehavior },
            values = { listOf(DesktopCloseBehavior.EXIT, DesktopCloseBehavior.MINIMIZE) },
            itemText = {
                Text(it.renderText())
            },
            exposedItemText = {
                Text(it.renderText())
            },
            onSelect = {
                onChange(uiSettings.copy(desktopCloseBehavior = it))
            },
            title = { Text(stringResource(Lang.settings_app_close_behavior)) },
        )
    }

    Group(title = { Text(stringResource(Lang.settings_app_search)) }, useThinHeader = true) {
        SwitchItem(
            checked = uiSettings.searchSettings.ignoreDoneAndDroppedSubjects,
            enabled = supportsCollectionSearchFilter,
            onCheckedChange = {
                onChange(
                    uiSettings.copy(
                        searchSettings = uiSettings.searchSettings.copy(
                            ignoreDoneAndDroppedSubjects = !uiSettings.searchSettings.ignoreDoneAndDroppedSubjects,
                        ),
                    ),
                )
            },
            title = { Text(stringResource(Lang.settings_app_not_show_done_and_dropped_subjects)) },
        )
        DropdownItem(
            selected = { uiSettings.searchSettings.nsfwMode },
            values = { NsfwMode.entries },
            itemText = {
                when (it) {
                    NsfwMode.HIDE -> Text(stringResource(Lang.settings_app_nsfw_hide))
                    NsfwMode.BLUR -> Text(stringResource(Lang.settings_app_nsfw_blur))
                    NsfwMode.DISPLAY -> Text(stringResource(Lang.settings_app_nsfw_display))
                }
            },
            onSelect = {
                onChange(
                    uiSettings.copy(
                        searchSettings = uiSettings.searchSettings.copy(nsfwMode = it),
                    ),
                )
            },
            title = { Text(stringResource(Lang.settings_app_nsfw_content)) },
        )
    }

    Group(title = { Text(stringResource(Lang.settings_app_my_collections)) }, useThinHeader = true) {
        SwitchItem(
            checked = uiSettings.myCollections.enableListAnimation1,
            enabled = supportsCollectionAnimation,
            onCheckedChange = {
                onChange(
                    uiSettings.copy(
                        myCollections = uiSettings.myCollections.copy(
                            enableListAnimation1 = !uiSettings.myCollections.enableListAnimation1,
                        ),
                    ),
                )
            },
            title = { Text(stringResource(Lang.settings_app_list_animation)) },
            description = { Text(stringResource(Lang.settings_app_list_animation_description)) },
        )
    }

    Group(title = { Text(stringResource(Lang.settings_app_episode_playback)) }, useThinHeader = true) {
        val episode = uiSettings.episodeProgress
        SwitchItem(
            checked = episode.theme == EpisodeListProgressTheme.LIGHT_UP,
            enabled = supportsEpisodeAppearance,
            onCheckedChange = {
                onChange(
                    uiSettings.copy(
                        episodeProgress = episode.copy(
                            theme = if (it) EpisodeListProgressTheme.LIGHT_UP else EpisodeListProgressTheme.ACTION,
                        ),
                    ),
                )
            },
            title = { Text(stringResource(Lang.settings_app_light_up_mode)) },
            description = { Text(stringResource(Lang.settings_app_light_up_mode_description)) },
        )
        SwitchItem(
            checked = episode.showEpisodeImages,
            enabled = supportsEpisodeAppearance,
            onCheckedChange = {
                onChange(uiSettings.copy(episodeProgress = episode.copy(showEpisodeImages = it)))
            },
            title = { Text(stringResource(Lang.settings_app_episode_images)) },
            description = { Text(stringResource(Lang.settings_app_episode_images_description)) },
        )
    }

    Group(title = { Text(stringResource(Lang.settings_app_subject_title)) }, useThinHeader = true) {
        SwitchItem(
            checked = uiSettings.subjectAppearance.useOriginalTitle,
            onCheckedChange = {
                onChange(
                    uiSettings.copy(
                        subjectAppearance = uiSettings.subjectAppearance.copy(useOriginalTitle = it),
                    ),
                )
            },
            title = { Text(stringResource(Lang.settings_app_use_original_title)) },
            description = { Text(stringResource(Lang.settings_app_use_original_title_description)) },
        )
    }
}

@Composable
private fun DesktopCloseBehavior.renderText(): String {
    return when (this) {
        DesktopCloseBehavior.EXIT -> stringResource(Lang.settings_app_close_behavior_exit)
        DesktopCloseBehavior.MINIMIZE -> stringResource(Lang.settings_app_close_behavior_minimize_to_tray)
    }
}
