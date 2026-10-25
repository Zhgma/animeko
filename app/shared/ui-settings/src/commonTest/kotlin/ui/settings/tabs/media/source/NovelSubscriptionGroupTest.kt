/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.settings.tabs.media.source

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.repository.novel.DefaultNovelSources
import me.him188.ani.app.data.repository.novel.NovelSourceSettings
import me.him188.ani.app.data.repository.novel.NovelSubscription
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.settings.SettingsTab
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NovelSubscriptionGroupTest {
    @Test
    fun createsAndEditsLinksAndKeepsFailedEditorOpen() = runAniComposeUiTest {
        var settings by mutableStateOf(NovelSourceSettings(subscriptions = emptyList()))
        var fail = false
        setContent {
            ProvideCompositionLocalsForPreview {
                SettingsTab(Modifier.requiredSize(600.dp, 800.dp).verticalScroll(rememberScrollState())) {
                    NovelSubscriptionGroupContent(settings, { id, name, url ->
                        if (fail) error("测试网络失败")
                        settings = settings.copy(subscriptions = listOf(NovelSubscription(id ?: "one", name, url, sources = DefaultNovelSources.all.take(1))))
                    }, {}, { _, _ -> }, {})
                }
            }
        }
        onNodeWithTag("novel-subscription-add").performClick()
        onNodeWithTag("novel-subscription-save").assertIsNotEnabled()
        onNodeWithTag("novel-subscription-name").performTextInput("Test")
        onNodeWithTag("novel-subscription-url").performTextInput("https://a.test/source.js")
        onNodeWithTag("novel-subscription-save").performClick()
        onNodeWithTag("novel-subscription-url").assertDoesNotExist()
        runOnIdle { assertEquals("https://a.test/source.js", settings.subscriptions.single().url); fail = true }
        onNodeWithTag("novel-subscription-menu-one").performScrollTo().performClick()
        onNodeWithTag("novel-subscription-edit").performClick()
        onNodeWithTag("novel-subscription-url").assertTextContains("https://a.test/source.js")
        onNodeWithTag("novel-subscription-url").performTextReplacement("https://b.test/index.json")
        onNodeWithTag("novel-subscription-save").performClick()
        onNodeWithTag("novel-subscription-error").assertTextEquals("测试网络失败")
        onNodeWithTag("novel-subscription-url").assertTextContains("https://b.test/index.json")
        runOnIdle { assertEquals("https://a.test/source.js", settings.subscriptions.single().url); fail = false }
        onNodeWithTag("novel-subscription-save").performClick()
        runOnIdle { assertEquals("one", settings.subscriptions.single().id); assertEquals("https://b.test/index.json", settings.subscriptions.single().url) }
    }

    @Test
    fun disableRefreshAndDeleteOperateOnTheChosenSubscription() = runAniComposeUiTest {
        val a = NovelSubscription("one", "One", "https://a.test", sources = DefaultNovelSources.all.take(1))
        val b = a.copy(id = "two", name = "Two", url = "https://b.test")
        var settings by mutableStateOf(NovelSourceSettings(subscriptions = listOf(a, b)))
        val refreshed = mutableListOf<String>()
        setContent {
            ProvideCompositionLocalsForPreview {
                SettingsTab(Modifier.requiredSize(600.dp, 800.dp).verticalScroll(rememberScrollState())) {
                    NovelSubscriptionGroupContent(settings, { _, _, _ -> }, { refreshed += it.id }, { id, enabled ->
                        settings = settings.copy(subscriptions = settings.subscriptions.map { if (it.id == id) it.copy(enabled = enabled) else it })
                    }, { id -> settings = settings.copy(subscriptions = settings.subscriptions.filterNot { it.id == id }) })
                }
            }
        }
        onNodeWithTag("novel-subscription-enabled-one").performScrollTo().performClick()
        runOnIdle { assertFalse(settings.subscriptions.first().enabled); assertTrue(settings.subscriptions.last().enabled) }
        onNodeWithTag("novel-subscription-refresh-all").performScrollTo().performClick()
        runOnIdle { assertEquals(listOf("two"), refreshed) }
        onNodeWithTag("novel-subscription-menu-one").performScrollTo().performClick()
        onNodeWithTag("novel-subscription-delete").performClick()
        runOnIdle { assertEquals(2, settings.subscriptions.size) }
        onNodeWithTag("novel-subscription-confirm-delete").performClick()
        runOnIdle { assertEquals(listOf(b), settings.subscriptions) }
    }
}
