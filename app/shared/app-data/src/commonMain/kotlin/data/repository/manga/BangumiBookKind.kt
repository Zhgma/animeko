/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.manga

/** Classification uses Bangumi metadata, never a title or a source's category. */
enum class BangumiBookKind(val platform: String) {
    MANGA("漫画"), NOVEL("小说");
    fun accepts(type: Int, platform: String): Boolean = type == 1 && platform.trim() == this.platform
}
