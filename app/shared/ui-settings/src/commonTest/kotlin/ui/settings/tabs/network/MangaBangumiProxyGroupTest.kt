/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.settings.tabs.network

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.test.performScrollTo
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import me.him188.ani.app.data.repository.manga.MangaBangumiNetwork
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.settings.SettingsTab
import kotlin.test.Test
import kotlin.test.assertEquals

class MangaBangumiProxyGroupTest {
    @Test fun modesPersistWithoutChangingGlobalSettings() = runAniComposeUiTest {
        val data = object : DataStore<Preferences> {
            override val data = MutableStateFlow(emptyPreferences())
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                transform(data.value).also { data.value = it }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val network = MangaBangumiNetwork(data, scope)
        try {
            setContent {
                ProvideCompositionLocalsForPreview {
                    SettingsTab(Modifier.requiredSize(800.dp, 700.dp).verticalScroll(rememberScrollState())) { MangaBangumiProxyGroup(network) }
                }
            }
            onNodeWithText("自定义代理").performScrollTo().performClick()
            waitUntil(timeoutMillis = 5_000) { data.data.value.toString().contains("CUSTOM") }
            onNodeWithText("直连", substring = false).performScrollTo().performClick()
            waitUntil(timeoutMillis = 5_000) { data.data.value.toString().contains("DISABLED") }
            runOnIdle { assertEquals(1, data.data.value.asMap().size) }
        } finally { scope.cancel() }
    }
}
