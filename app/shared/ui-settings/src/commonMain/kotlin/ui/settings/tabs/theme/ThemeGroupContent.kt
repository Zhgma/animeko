/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.settings.tabs.theme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import me.him188.ani.app.data.models.preference.ThemeSettings
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_theme_always_dark_episode
import me.him188.ani.app.ui.lang.settings_theme_always_dark_episode_description
import me.him188.ani.app.ui.lang.settings_theme_animated_gradient_subject
import me.him188.ani.app.ui.lang.settings_theme_animated_gradient_subject_description
import me.him188.ani.app.ui.lang.settings_theme_dynamic_colors
import me.him188.ani.app.ui.lang.settings_theme_dynamic_colors_description
import me.him188.ani.app.ui.lang.settings_theme_dynamic_subject
import me.him188.ani.app.ui.lang.settings_theme_dynamic_subject_description
import me.him188.ani.app.ui.lang.settings_theme_frosted_glass
import me.him188.ani.app.ui.lang.settings_theme_frosted_glass_description
import me.him188.ani.app.ui.lang.settings_theme_high_contrast
import me.him188.ani.app.ui.lang.settings_theme_high_contrast_description
import me.him188.ani.app.ui.lang.settings_theme_palette
import me.him188.ani.app.ui.lang.settings_theme_title
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.app.ui.settings.framework.components.SwitchItem
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingsScope.ThemeGroupContent(
    themeSettings: ThemeSettings,
    onChange: (ThemeSettings) -> Unit,
    supportsDynamicColors: Boolean,
    showFrostedGlass: Boolean,
    supportsPageEffects: Boolean = true,
) {
    Group(
        title = { Text(stringResource(Lang.settings_theme_title)) },
    ) {
        DarkModeSelectPanel(
            currentMode = themeSettings.darkMode,
            onModeSelected = { onChange(themeSettings.copy(darkMode = it)) },
            modifier = Modifier.padding(vertical = SettingsScope.itemVerticalSpacing),
        )

        if (supportsDynamicColors) {
            SwitchItem(
                checked = themeSettings.useDynamicTheme,
                onCheckedChange = { checked ->
                    onChange(themeSettings.copy(useDynamicTheme = checked))
                },
                title = { Text(stringResource(Lang.settings_theme_dynamic_colors)) },
                description = { Text(stringResource(Lang.settings_theme_dynamic_colors_description)) },
            )
        }

        SwitchItem(
            checked = themeSettings.useBlackBackground,
            onCheckedChange = { checked ->
                onChange(themeSettings.copy(useBlackBackground = checked))
            },
            title = { Text(stringResource(Lang.settings_theme_high_contrast)) },
            description = { Text(stringResource(Lang.settings_theme_high_contrast_description)) },
        )

        SwitchItem(
            checked = themeSettings.alwaysDarkInEpisodePage,
            enabled = supportsPageEffects,
            onCheckedChange = { checked ->
                onChange(themeSettings.copy(alwaysDarkInEpisodePage = checked))
            },
            title = { Text(stringResource(Lang.settings_theme_always_dark_episode)) },
            description = { Text(stringResource(Lang.settings_theme_always_dark_episode_description)) },
        )

        SwitchItem(
            checked = themeSettings.useDynamicSubjectPageTheme,
            enabled = supportsPageEffects,
            onCheckedChange = { checked ->
                onChange(themeSettings.copy(useDynamicSubjectPageTheme = checked))
            },
            title = { Text(stringResource(Lang.settings_theme_dynamic_subject)) },
            description = { Text(stringResource(Lang.settings_theme_dynamic_subject_description)) },
        )

        SwitchItem(
            checked = themeSettings.enableAnimatedGradientSubjectPage,
            enabled = supportsPageEffects,
            onCheckedChange = { checked ->
                onChange(themeSettings.copy(enableAnimatedGradientSubjectPage = checked))
            },
            title = { Text(stringResource(Lang.settings_theme_animated_gradient_subject)) },
            description = { Text(stringResource(Lang.settings_theme_animated_gradient_subject_description)) },
        )

        if (showFrostedGlass) {
            SwitchItem(
                checked = themeSettings.enableFrostedGlassEffect,
            enabled = supportsPageEffects,
                onCheckedChange = { checked ->
                    onChange(themeSettings.copy(enableFrostedGlassEffect = checked))
                },
                title = { Text(stringResource(Lang.settings_theme_frosted_glass)) },
                description = { Text(stringResource(Lang.settings_theme_frosted_glass_description)) },
            )
        }
    }

    Box(
        modifier = Modifier.alpha(if (themeSettings.useDynamicTheme) 0.5f else 1f),
    ) {
        Group(title = { Text(stringResource(Lang.settings_theme_palette)) }) {
            ThemePalette(
                selectedColor = themeSettings.seedColor.takeUnless { themeSettings.useDynamicTheme },
                onSelect = { color ->
                    onChange(themeSettings.copy(seedColorValue = color.value, useDynamicTheme = false))
                },
            )
        }
    }
}
