/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.novel

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.repository.novel.*
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.mediaselect.manual.ManualBrowsePageTestTags
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.test.*

@OptIn(TestOnly::class)
class NovelSourcePanelTest {
    private val book = NovelBook(NovelSource.QUANBEN, "santi", "三体")
    private val settings = NovelSourceSettings(subscriptions = listOf(NovelSubscription.defaults().single().copy(sources = DefaultNovelSources.all.take(2))))

    @Test fun automaticUsesSharedRowsAndSelectsAnExplicitBook() = runAniComposeUiTest {
        var picked: NovelBook? = null
        var automatic = 0
        setContent { ProvideCompositionLocalsForPreview {
            NovelSourcePanel(NovelState(sourceSettings = settings, sourceSearches = mapOf(book.source to NovelSearchState("三体", listOf(book)))),
                "三体", {}, { _, _ -> }, { picked = it }, { automatic++ }, false, {}, Modifier.requiredSize(500.dp, 700.dp))
        } }
        onNodeWithTag("novel-auto-source").performClick()
        onNodeWithText("三体", substring = false).performClick()
        runOnIdle { assertEquals(1, automatic); assertEquals(book, picked) }
    }

    @Test fun manualUsesCachedSourceResultsAndOnlySearchesAnUncachedSource() = runAniComposeUiTest {
        var current by mutableStateOf(NovelState(sourceSettings = settings,
            sourceSearches = mapOf(book.source to NovelSearchState("三体", listOf(book)))))
        var picked: NovelBook? = null
        val requested = mutableListOf<NovelSource>()
        setContent { ProvideCompositionLocalsForPreview {
            NovelSourcePanel(current, "三体", {}, { source, query ->
                requested += source
                current = current.copy(sourceSearches = current.sourceSearches + (source to NovelSearchState(query, emptyList())))
            }, { picked = it }, {}, true, {}, Modifier.requiredSize(500.dp, 700.dp))
        } }
        runOnIdle { assertTrue(requested.isEmpty()) }
        onNodeWithTag(ManualBrowsePageTestTags.result(0)).performClick()
        runOnIdle { assertEquals(book, picked) }
        onNodeWithTag(ManualBrowsePageTestTags.SOURCE_SELECTOR).performClick()
        onNodeWithTag(ManualBrowsePageTestTags.sourceItem("HUANMENG")).performClick()
        runOnIdle { assertEquals(listOf(NovelSource.HUANMENG), requested) }
        onNodeWithTag(ManualBrowsePageTestTags.SOURCE_SELECTOR).performClick()
        onNodeWithTag(ManualBrowsePageTestTags.sourceItem("QUANBEN")).performClick()
        runOnIdle { assertEquals(1, requested.size) }
        onNodeWithTag(ManualBrowsePageTestTags.result(0)).assertExists()
    }
}
