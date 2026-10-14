/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.manga

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.repository.manga.MangaSource
import me.him188.ani.app.data.repository.manga.MangaSourceBook
import me.him188.ani.app.data.repository.manga.MangaSourceSettings
import me.him188.ani.app.data.repository.manga.MangaSubscription
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import kotlin.test.Test

class MangaSubscriptionSelectionTest {
    @Test fun removingLastSubscriptionDisablesAutomaticSelection() = checkRemovedSubscription(false)
    @Test fun removingLastSubscriptionDisablesManualSearch() = checkRemovedSubscription(true)

    private fun checkRemovedSubscription(manual: Boolean) = runAniComposeUiTest {
        val actionTag = if (manual) "manga-source-query" else "manga-auto-source"
        val source = MangaSource.GODA
        val subscription = MangaSubscription("one", "Source", "https://source.test/a", sources = listOf(source))
        var settings by mutableStateOf(MangaSourceSettings(subscriptions = listOf(subscription)))
        val book = MangaSourceBook(source, "book", "Saved manga")
        setContent {
            ProvideCompositionLocalsForPreview {
                Box(Modifier.requiredSize(600.dp, 800.dp)) {
                    MangaSourcePanel(MangaDetailsUiState(sourceSettings = settings, source = source,
                        sourceSearches = mapOf(source to MangaSourceSearchState("Query", listOf(book)))),
                        "Query", {}, { _, _ -> }, {}, {}, manual = manual)
                }
            }
        }
        onNodeWithText("Saved manga").assertExists()
        onNodeWithTag(actionTag).assertIsEnabled()
        runOnIdle { settings = settings.copy(subscriptions = emptyList()) }
        onNodeWithText("Saved manga").assertDoesNotExist()
        onNodeWithTag(actionTag).assertIsNotEnabled()
        runOnIdle { settings = settings.copy(subscriptions = listOf(subscription)) }
        onNodeWithTag(actionTag).assertIsEnabled()
        onNodeWithText("Saved manga").assertExists()
    }
}
