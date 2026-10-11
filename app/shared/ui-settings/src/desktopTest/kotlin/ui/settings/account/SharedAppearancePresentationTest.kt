/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.settings.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import me.him188.ani.app.data.models.preference.UISettings
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.settings.SettingsTab
import me.him188.ani.app.ui.settings.tabs.app.AppearanceGroupContent
import me.him188.ani.app.ui.lang.*
import org.jetbrains.compose.resources.stringResource
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import me.him188.ani.app.navigation.MainScreenPage
import me.him188.ani.app.navigation.getText

class SharedAppearancePresentationTest {
    @Test fun dropdownUpdatesItsSelectedLabelAfterChangingTheInitialPage() = runAniComposeUiTest {
        var value = UISettings()
        var initialLabel = ""
        var downloadsLabel = ""
        setContent { ProvideCompositionLocalsForPreview {
            var settings by remember { mutableStateOf(value) }
            initialLabel = stringResource(Lang.settings_app_initial_page)
            downloadsLabel = MainScreenPage.CacheManagement.getText()
            SettingsTab(Modifier.verticalScroll(rememberScrollState())) {
                AppearanceGroupContent(settings, { value = it; settings = it })
            }
        } }
        onNodeWithText(initialLabel).performClick()
        onNodeWithText(downloadsLabel).performClick()
        runOnIdle { assertEquals(MainScreenPage.CacheManagement, value.mainSceneInitialPage) }
        onNodeWithText(downloadsLabel).assertIsDisplayed()
    }

    @Test fun originalTitleUpdatesWhileUnsupportedEpisodeControlsStayDisabled() = runAniComposeUiTest {
        var value = UISettings()
        var originalLabel = ""
        var episodeLabel = ""
        setContent { ProvideCompositionLocalsForPreview {
            var settings by remember { mutableStateOf(value) }
            originalLabel = stringResource(Lang.settings_app_use_original_title)
            episodeLabel = stringResource(Lang.settings_app_light_up_mode)
            SettingsTab(Modifier.verticalScroll(rememberScrollState())) {
                AppearanceGroupContent(settings, { value = it; settings = it }, supportsEpisodeAppearance = false)
            }
        } }
        onNodeWithText(originalLabel).performScrollTo().performClick()
        runOnIdle { assertTrue(value.subjectAppearance.useOriginalTitle) }
        onNodeWithText(episodeLabel).performScrollTo().performClick()
        runOnIdle { assertTrue(value.episodeProgress == UISettings().episodeProgress) }
    }
}
