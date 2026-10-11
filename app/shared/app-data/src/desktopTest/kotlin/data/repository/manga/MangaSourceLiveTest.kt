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

/** Opt-in end-to-end source diagnostic with real image decoding. */
class MangaSourceLiveTest {
    @Test fun komiic() = verify(MangaSource.KOMIIC)
    @Test fun manhuagui() = verify(MangaSource.MANHUAGUI)
    @Test fun goda() = verify(MangaSource.GODA)
    @Test fun baozi() = verify(MangaSource.BAOZI)
    @Test fun manhuaren() = verify(MangaSource.MANHUAREN)
    @Test fun ykmh() = verify(MangaSource.YKMH)

    private fun verify(source: MangaSource) {
        if (System.getenv("ANIMEKO_MANGA_LIVE") != "1") return
        val client = createDefaultHttpClient(bare = true) {
            proxy(System.getenv("ANIMEKO_MANGA_PROXY")?.let(::ClientProxyConfig))
        }
        val failures = mutableListOf<String>()
        try {
            runBlocking {
                val repo = MangaSourceRepository(client.asScopedHttpClient())
                for (query in if (source == MangaSource.MANHUAREN) listOf("碧蓝幻想", "薰香花朵") else listOf("碧蓝之海", "薰香花朵")) {
                    var stage = "search"
                    try {
                        withTimeout(65_000) {
                            val results = repo.search(source, query).toMutableList()
                            if (query == "薰香花朵") {
                                for (alias in listOf("花薰凛然", "熏香花朵")) results += repo.search(source, alias)
                            }
                            val names = if (query == "碧蓝之海") listOf("碧蓝之海", "碧藍之海") else if (query == "碧蓝幻想") listOf("碧蓝幻想") else listOf("薰香花朵", "熏香花朵", "花薰凛然", "花熏凛然", "花薰凜然")
                            val book = results.firstOrNull { book -> names.any { it in book.title } }
                                ?: error("No matching title (${results.size} results)")
                            stage = "chapters"
                            val chapters = repo.chapters(book)
                            val chapter = if (source == MangaSource.MANHUAREN) chapters.filter { it.number != null && it.group == "章节" && Regex("第[0-9]+(?:\\.[0-9]+)?话").matches(it.title) }.maxBy { it.number!! } else chapters.firstOrNull { it.group == "章节" } ?: chapters.first()
                            stage = "pages"
                            val pages = repo.pages(book, chapter)
                            stage = "image"
                            val image = repo.image(pages.first())
                            assertTrue(image.size > 1_000)
                            val decoded = Image.makeFromEncoded(image)
                            assertTrue(decoded.width > 100 && decoded.height > 100)
                            println("SOURCE_PASS ${source.name} query=$query book=${book.id} chapters=${chapters.size} chapter=${chapter.id} pages=${pages.size} imageBytes=${image.size} size=${decoded.width}x${decoded.height}")
                            decoded.close()
                        }
                    } catch (e: Exception) {
                        val failure = "${source.name} query=$query stage=$stage ${e::class.simpleName}: ${e.message}"
                        println("SOURCE_FAIL $failure")
                        failures += failure
                    }
                }
            }
        } finally { client.close() }
        assertTrue(failures.isEmpty(), failures.joinToString("; "))
    }
}
