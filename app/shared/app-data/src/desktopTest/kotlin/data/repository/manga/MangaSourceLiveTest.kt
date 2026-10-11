/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.manga

import kotlinx.coroutines.runBlocking
import me.him188.ani.utils.ktor.ClientProxyConfig
import me.him188.ani.utils.ktor.asScopedHttpClient
import me.him188.ani.utils.ktor.createDefaultHttpClient
import me.him188.ani.utils.ktor.proxy
import kotlin.test.Test
import kotlin.test.assertTrue

/** Opt-in network diagnostic: ANIMEKO_MANGA_LIVE=1, optional ANIMEKO_MANGA_PROXY. */
class MangaSourceLiveTest {
    @Test fun komiic() = verify(MangaSource.KOMIIC)
    @Test fun manhuagui() = verify(MangaSource.MANHUAGUI)
    @Test fun goda() = verify(MangaSource.GODA)

    private fun verify(source: MangaSource) {
        if (System.getenv("ANIMEKO_MANGA_LIVE") != "1") return
        val client = createDefaultHttpClient(bare = true) {
            proxy(System.getenv("ANIMEKO_MANGA_PROXY")?.let(::ClientProxyConfig))
        }
        try {
            runBlocking {
                val repo = MangaSourceRepository(client.asScopedHttpClient())
                val book = repo.search(source, "碧蓝之海").first { it.title.contains("碧蓝之海") || it.title.contains("碧藍之海") }
                val chapters = repo.chapters(book)
                val chapter = chapters.firstOrNull { it.group == "章节" && it.number == 1.0 } ?: chapters.first()
                val pages = repo.pages(book, chapter)
                val image = repo.image(pages.first())
                assertTrue(image.size > 1_000)
                println("${source.name}: book=${book.id}, chapters=${chapters.size}, pages=${pages.size}, firstImageBytes=${image.size}")
            }
        } finally { client.close() }
    }
}
