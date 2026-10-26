package me.him188.ani.app.ui.playback

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import me.him188.ani.app.data.repository.manga.*
import me.him188.ani.app.data.repository.novel.*
import me.him188.ani.app.data.repository.player.ReadingHistoryEntry
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import kotlin.test.*

class ReadingHistoryContentTest {
    @Test fun controlsDefaultOffAndTabsAreSelectable() = runAniComposeUiTest {
        var tab by mutableStateOf(0)
        var sync by mutableStateOf(false)
        setContent { ProvideCompositionLocalsForPreview { HistoryPageControls(tab, { tab = it }, sync, { sync = it }) } }
        onNodeWithTag("historyCloudSync").assertIsOff().performClick().assertIsOn()
        onNodeWithText("漫画").performClick().assertIsSelected()
        assertEquals(1, tab)
        onNodeWithText("小说").performClick().assertIsSelected()
        assertEquals(2, tab)
    }

    @Test fun readingItemsResumeAndRequireConfirmationToDelete() = runAniComposeUiTest {
        val manga = ReadingHistoryEntry(10, "花薰凛然", chapterTitle = "205话", updatedAtMillis = 0, totalPages = 21,
            manga = MangaReadingMemory(MangaSourceBook(MangaSource.KOMIIC, "book", "花薰凛然"), "205", 8))
        val novel = ReadingHistoryEntry(0, "本地小说", chapterTitle = "第三章", updatedAtMillis = 1,
            novel = NovelMemory(NovelBook(NovelSource.LOCAL, "book", "本地小说"), emptyList(), "3", 12, 45))
        var opened: ReadingHistoryEntry? = null
        var deleted: ReadingHistoryEntry? = null
        setContent { ProvideCompositionLocalsForPreview {
            ReadingHistoryList(listOf(manga, novel), { opened = it }, { deleted = it })
        } }
        onNodeWithText("第 9 页 / 21 页").assertExists()
        onNodeWithText("第 13 段").assertExists()
        onNodeWithText("花薰凛然").performClick()
        assertEquals(manga, opened)
        onAllNodesWithContentDescription("删除阅读记录")[0].performClick()
        assertNull(deleted)
        onNodeWithText("取消").performClick()
        assertNull(deleted)
        onAllNodesWithContentDescription("删除阅读记录")[1].performClick()
        onNodeWithText("删除", useUnmergedTree = true).performClick()
        assertEquals(novel, deleted)
    }
}
