/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.settings.account

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.settings.*
import me.him188.ani.app.navigation.SettingsTab as Category
import kotlin.test.Test
import kotlin.test.assertEquals

class SharedSettingsPresentationTest {
    @Test
    fun categoryMenuDispatchesOriginalTabAndHidesDebugByDefault() = runAniComposeUiTest {
        var selected: Category? = null
        setContent {
            ProvideCompositionLocalsForPreview {
                Column {
                    SettingsCategoryItems { tab ->
                        SettingsNavigationItem(settingsTabName(tab), settingsTabIcon(tab), false, { selected = tab },
                            Modifier.testTag("category-${tab.name}"))
                    }
                }
            }
        }
        onNodeWithTag("category-THEME").performClick()
        runOnIdle { assertEquals(Category.THEME, selected) }
        onNodeWithTag("category-DEBUG").assertDoesNotExist()
    }
}
