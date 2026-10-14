// SPDX-License-Identifier: AGPL-3.0-only
package me.him188.ani.app.data.repository.manga

import kotlinx.serialization.Serializable

const val KOMIIC_API_BASE_URL = "https://43.131.255.74:8443/api/komiic"

enum class MangaSource(val title: String, val subscriptionUrl: String, val home: String) {
    KOMIIC("Komiic", "https://cdn.jsdelivr.net/gh/venera-app/venera-configs@main/komiic.js", "https://komiic.com"),
    MANHUAGUI("漫画柜", "https://cdn.jsdelivr.net/gh/venera-app/venera-configs@main/manhuagui.js", "https://www.manhuagui.com"),
    BAOZI("包子漫画", "https://cdn.jsdelivr.net/gh/venera-app/venera-configs@main/baozi.js", "https://cn.bzmgcn.com"),
    GODA("G站漫画", "https://cdn.jsdelivr.net/gh/venera-app/venera-configs@main/goda.js", "https://godamh.com"),
    MANHUAREN("漫画人", "https://cdn.jsdelivr.net/gh/venera-app/venera-configs@main/manhuaren.js", "https://www.manhuaren.com"),
    YKMH("优酷漫画", "https://cdn.jsdelivr.net/gh/venera-app/venera-configs@main/ykmh.js", "https://www.ykmh.net"),
    CCC("CCC追漫台", "https://cdn.jsdelivr.net/gh/venera-app/venera-configs@main/ccc.js", "https://www.creative-comic.tw"),
    COMICK("Comick", "https://cdn.jsdelivr.net/gh/venera-app/venera-configs@main/comick.js", "https://comick.art"),
    // Kept for decoding saved offline records. No network adapter is registered.
    MANGADEX("MangaDex", "https://cdn.jsdelivr.net/gh/venera-app/venera-configs@main/manga_dex.js", "https://mangadex.org");

    val iconUrl: String get() = if (this == KOMIIC) "$KOMIIC_API_BASE_URL/icon" else "$home/favicon.ico"

    companion object {
        val available = entries.filter { it != MANGADEX }
        val maintained = available.filter { it != BAOZI }
    }
}
@Serializable
data class MangaSourceBook(val source: MangaSource, val id: String, val title: String, val cover: String = "")
@Serializable
data class MangaChapter(val id: String, val title: String, val number: Double?, val group: String, val pageCount: Int = 0)
data class MangaPage(val url: String, val referer: String, val width: Int = 0, val height: Int = 0, val cccImageId: Int? = null)

