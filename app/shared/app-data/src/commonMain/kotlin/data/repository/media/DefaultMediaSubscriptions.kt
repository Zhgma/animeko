/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.media

/** Default catalogs shared by native and browser clients. */
object DefaultMediaSubscriptions {
    const val BT = "https://sub.creamycake.org/v1/bt1.json"
    const val WEB = "https://sub.creamycake.org/v1/css1.json"
    const val MANGA = "https://raw.githubusercontent.com/Zhgma/animeko/main/subs/manga.json"
    val anime = listOf(BT, WEB)
}
