/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import me.him188.ani.utils.platform.annotations.TestOnly

@OptIn(TestOnly::class)
class UpdateDownloadNotificationTest {
    @Test
    fun progressAndTerminalStatesAreDistinct() {
        val downloading = TestAppUpdatePresentations.Downloading.copy(fileDownloaderStats = TestFileDownloaderStats.Downloading)
        assertEquals(UpdateDownloadNotice("1.0.0", UpdateDownloadPhase.DOWNLOADING, 50), downloading.downloadNotice())
        // 100% transferred still means verification, not ready to install.
        assertEquals(UpdateDownloadPhase.DOWNLOADING,
            downloading.copy(fileDownloaderStats = TestFileDownloaderStats.Downloading.copy(progress = 1f)).downloadNotice()?.phase)
        assertEquals(UpdateDownloadPhase.DOWNLOADED, TestAppUpdatePresentations.Succeed.downloadNotice()?.phase)
        assertEquals(UpdateDownloadPhase.FAILED, TestAppUpdatePresentations.Failed.downloadNotice()?.phase)
        assertNull(TestAppUpdatePresentations.HasUpdate.downloadNotice())
        assertNull(AppUpdatePresentation.Placeholder.downloadNotice())
    }

    @Test
    fun everyNotificationTapCanReopenTheWindow() {
        val before = UpdateDownloadUiRequests.foregroundRequests.value
        repeat(3) { UpdateDownloadUiRequests.showDownload() }
        assertEquals(before + 3, UpdateDownloadUiRequests.foregroundRequests.value)
    }
}
