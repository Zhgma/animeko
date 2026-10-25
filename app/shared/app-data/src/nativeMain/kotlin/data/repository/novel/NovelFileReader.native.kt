/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.novel

actual fun readNovelFile(name: String, bytes: ByteArray, saveAsset: (ByteArray) -> String): NovelDocument {
    require(name.substringAfterLast('.').lowercase() == "txt") { "此平台目前支持 UTF-8 TXT；EPUB、MOBI、AZW3 请使用 Android 或桌面版" }
    require(bytes.size <= 64 * 1024 * 1024) { "文件过大" }
    return textNovel(name.substringBeforeLast('.'), bytes.decodeToString(throwOnInvalidSequence = true))
}
