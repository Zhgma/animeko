/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.update

import me.him188.ani.app.platform.ContextMP

internal actual fun createUpdateDownloadNotifier(context: ContextMP, onTimeout: () -> Unit): UpdateDownloadNotifier =
    object : UpdateDownloadNotifier {
        override fun update(notice: UpdateDownloadNotice?) {}
        override fun close() {}
    }

internal actual fun canShowUpdateDownloadNotification(context: ContextMP): Boolean = true
