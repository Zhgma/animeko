/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.manga

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.him188.ani.utils.ktor.ClientProxyConfig
import me.him188.ani.utils.ktor.asScopedHttpClient
import me.him188.ani.utils.ktor.createDefaultHttpClient
import me.him188.ani.utils.ktor.proxy
import org.jetbrains.skia.Image
import kotlin.test.Test
import kotlin.test.assertTrue

class VerifiedMangaSourcesLiveTest {
    @Test fun ccc() = verify(MangaSource.CCC, "少年", "276")
    @Test fun comick() = verify(MangaSource.COMICK, "Look Back", "look-back")
    private fun verify(source: MangaSource, keyword: String, id: String) {
        if (System.getenv("ANIMEKO_MANGA_LIVE") != "1") return
        val client = createDefaultHttpClient(bare=true) {
            proxy(System.getenv("ANIMEKO_MANGA_PROXY")?.let(::ClientProxyConfig))
        }
        try { runBlocking { withTimeout(120_000) {
            val repo = MangaSourceRepository(client.asScopedHttpClient())
            val books = repo.search(source, keyword)
            val book = books.first { it.id == id }
            val chapters = repo.chapters(book)
            val chapter = if (source == MangaSource.CCC) chapters.first { it.id == "2674" }
                else chapters.first { it.id == "WE5ZE|chapter|1|en" }
            val pages = repo.pages(book, chapter)
            val bytes = repo.image(pages.first())
            Image.makeFromEncoded(bytes).use { image ->
                assertTrue(image.width > 100 && image.height > 100)
                println("SOURCE_PASS ${source.name} search=${books.size} chapters=${chapters.size} pages=${pages.size} imageBytes=${bytes.size} image=${image.width}x${image.height}")
            }
        } } } finally { client.close() }
    }
}
