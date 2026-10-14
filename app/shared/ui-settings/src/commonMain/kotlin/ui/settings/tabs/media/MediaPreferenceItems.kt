/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.settings.tabs.media
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.util.fastAll
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.ui.settings.framework.components.*
import me.him188.ani.app.ui.lang.*
import me.him188.ani.app.ui.media.*
import me.him188.ani.datasources.api.topic.Resolution
import me.him188.ani.datasources.api.topic.SubtitleLanguage
import org.jetbrains.compose.resources.stringResource

private fun List<String>?.choices(all: List<String>): List<SelectableItem<String>> =
    if (this == null) all.map { SelectableItem(it, true) }
    else map { SelectableItem(it, true) } + (all - toSet()).map { SelectableItem(it, false) }

@Composable
fun SettingsScope.MediaPreferenceItems(preference: MediaPreference, onChange: (MediaPreference) -> Unit) {
    val mediaDetailsStrings = rememberMediaDetailsStrings()
    val sortedLanguages = preference.fallbackSubtitleLanguageIds.choices(SubtitleLanguage.matchableEntries.map { it.id })
    val sortedResolutions = preference.fallbackResolutions.choices(Resolution.entries.map { it.id })
        val textAny = stringResource(Lang.settings_media_any)
        val textNone = stringResource(Lang.settings_media_none)

        SorterItem(
            values = { sortedLanguages },
            onSort = { list ->
                onChange(
                    preference.copy(
                        fallbackSubtitleLanguageIds = list.filter { it.selected }
                            .map { it.item },
                    ),
                )
            },
            exposed = { list ->
                Text(
                    remember(list, mediaDetailsStrings) {
                        if (list.fastAll { it.selected }) {
                            textAny
                        } else if (list.fastAll { !it.selected }) {
                            textNone
                        } else
                            list.asSequence().filter { it.selected }
                                .joinToString { renderSubtitleLanguage(it.item, mediaDetailsStrings) }
                    },
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            item = { Text(renderSubtitleLanguage(it, mediaDetailsStrings)) },
            key = { it },
            dialogDescription = {
                Text(
                    stringResource(Lang.settings_media_sort_tip),
                )
            },
            icon = { Icon(Icons.Outlined.Language, null) },
            title = { Text(stringResource(Lang.settings_media_subtitle_language)) },
        )

        HorizontalDividerItem()

        SorterItem(
            values = { sortedResolutions },
            onSort = { list ->
                onChange(
                    preference.copy(
                        fallbackResolutions = list.filter { it.selected }
                            .map { it.item },
                    ),
                )
            },
            exposed = { list ->
                Text(
                    remember(list) {
                        if (list.fastAll { it.selected }) {
                            textAny
                        } else if (list.fastAll { !it.selected }) {
                            textNone
                        } else
                            list.asSequence().filter { it.selected }
                                .joinToString { renderResolution(it.item) }
                    },
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            item = { Text(renderResolution(it)) },
            key = { it },
            dialogDescription = { Text(stringResource(Lang.settings_media_sort_tip)) },
            icon = { Icon(Icons.Outlined.Hd, null) },
            title = { Text(stringResource(Lang.settings_media_resolution)) },
            description = { Text(stringResource(Lang.settings_media_resolution_description)) },
        )

        HorizontalDividerItem()

        val allianceRegexes by remember(preference) {
            derivedStateOf { preference.alliancePatterns?.joinToString() ?: "" }
        }
        TextFieldItem(
            value = allianceRegexes,
            title = { Text(stringResource(Lang.settings_media_alliance)) },
            description = {
                Text(stringResource(Lang.settings_media_alliance_description))
            },
            icon = { Icon(Icons.Outlined.Subtitles, null) },
            placeholder = { Text(textAny) },
            onValueChangeCompleted = { new ->
                onChange(
                    preference.copy(
                        alliancePatterns = new.split(",", "，").map { it.trim() },
                    ),
                )
            },
            sanitizeValue = { it.replace("，", ",") },
        )


}

@Composable
fun SettingsScope.BtPreferenceItems(preference: MediaPreference, mediaSelectorSettings: MediaSelectorSettings,
    onPreference: (MediaPreference) -> Unit, onSelector: (MediaSelectorSettings) -> Unit,
    supportsDisabledSources: Boolean = true, supportsCompletedSubjects: Boolean = true, supportsRememberedSource: Boolean = true) {
            SwitchItem(
                checked = mediaSelectorSettings.showDisabled,
                enabled = supportsDisabledSources,
                onCheckedChange = {
                    onSelector(
                        mediaSelectorSettings.copy(showDisabled = it),
                    )
                },
                title = { Text(stringResource(Lang.settings_media_show_disabled)) },
                description = { Text(stringResource(Lang.settings_media_show_disabled_description)) },
            )

            HorizontalDividerItem()

            SwitchItem(
                checked = !preference.showWithoutSubtitle,
                onCheckedChange = {
                    onPreference(
                        preference.copy(showWithoutSubtitle = !it),
                    )
                },
                title = { Text(stringResource(Lang.settings_media_hide_no_subtitle)) },
                description = { Text(stringResource(Lang.settings_media_hide_no_subtitle_description)) },
            )

            HorizontalDividerItem()

            SwitchItem(
                checked = mediaSelectorSettings.hideSingleEpisodeForCompleted,
                enabled = supportsCompletedSubjects,
                onCheckedChange = {
                    onSelector(
                        mediaSelectorSettings.copy(hideSingleEpisodeForCompleted = it),
                    )
                },
                title = { Text(stringResource(Lang.settings_media_hide_single_episode)) },
                description = { Text(stringResource(Lang.settings_media_hide_single_episode_description)) },
            )

            HorizontalDividerItem()

            SwitchItem(
                checked = mediaSelectorSettings.preferSeasons,
                onCheckedChange = {
                    onSelector(
                        mediaSelectorSettings.copy(preferSeasons = it),
                    )
                },
                title = { Text(stringResource(Lang.settings_media_prefer_seasons)) },
                description = { Text(stringResource(Lang.settings_media_prefer_seasons_description)) },
            )

            HorizontalDividerItem()

            SwitchItem(
                checked = mediaSelectorSettings.autoEnableLastSelected,
                enabled = supportsRememberedSource,
                onCheckedChange = {
                    onSelector(
                        mediaSelectorSettings.copy(autoEnableLastSelected = it),
                    )
                },
                title = { Text(stringResource(Lang.settings_media_auto_enable_last)) },
                description = { Text(stringResource(Lang.settings_media_auto_enable_last_description)) },
            )
}
