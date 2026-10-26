/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.novel

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import me.him188.ani.utils.io.SystemPaths
import me.him188.ani.utils.io.createTempDirectory
import me.him188.ani.utils.ktor.asScopedHttpClient
import kotlin.test.*
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZlibraryBookCacheTest {
    @Test fun downloadedBookRemainsReadableAfterRestartWithoutLoginOrNetwork() = runTest {
        val prefs = object : DataStore<Preferences> {
            override val data = MutableStateFlow(emptyPreferences())
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = transform(data.value).also { data.value = it }
        }
        val sessions = ZlibrarySessionStore(prefs)
        sessions.save(ZlibrarySession("https://zlib.test", "123", "test_key"))
        var offline = false
        var requests = 0
        val client = HttpClient(MockEngine { req ->
            assertFalse(offline, "Cached book must not contact the site")
            requests++
            if (req.url.host == "zlib.test") respond("""{"success":1,"file":{"extension":"txt","downloadLink":"https://files.test/book"}}""")
            else respond("第一章 开始\n测试正文\n第二章 结尾\n最后一页")
        })
        try {
            val root = SystemPaths.createTempDirectory("zlibrary-book-test")
            val api = ZlibraryApi(client.asScopedHttpClient(), sessions)
            val book = NovelBook(NovelSource.ZLIBRARY, "12/abcdef/txt", "Fixture")
            val first = ZlibraryBookCache(api, root) { "https://zlib.test" }.document(book)
            assertEquals(2, first.chapters.size)
            assertEquals(2, requests)
            offline = true; sessions.clear()
            val restored = ZlibraryBookCache(api, root) { "https://zlib.test" }.document(book)
            assertEquals(first, restored)
            assertTrue(restored.contents[0].any { it.text == "测试正文" })
            assertEquals(2, requests)
        } finally { client.close() }
    }
    @Test fun epubIllustrationsAreCachedAndRemainReadableAfterRestart() = runTest {
        val prefs = object : DataStore<Preferences> {
            override val data = MutableStateFlow(emptyPreferences())
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = transform(data.value).also { data.value = it }
        }
        val bytes = ByteArrayOutputStream().apply {
            ZipOutputStream(this).use { zip ->
                mapOf(
                    "META-INF/container.xml" to "<container><rootfiles><rootfile full-path='book.opf'/></rootfiles></container>",
                    "book.opf" to "<package><manifest><item id='one' href='one.xhtml'/></manifest><spine><itemref idref='one'/></spine></package>",
                    "one.xhtml" to "<body><p>插图之前</p><img src='plate.jpg'/><p>插图之后</p></body>",
                    "plate.jpg" to "image-fixture"
                ).forEach { (path, content) -> zip.putNextEntry(ZipEntry(path)); zip.write(content.encodeToByteArray()); zip.closeEntry() }
            }
        }.toByteArray()
        val sessions = ZlibrarySessionStore(prefs); sessions.save(ZlibrarySession("https://zlib.test", "123", "test_key"))
        var offline = false
        val client = HttpClient(MockEngine { req ->
            check(!offline)
            if (req.url.host == "zlib.test") respond("""{"success":1,"file":{"extension":"epub","downloadLink":"https://files.test/book"}}""")
            else respond(bytes)
        })
        try {
            val api = ZlibraryApi(client.asScopedHttpClient(), sessions)
            val root = SystemPaths.createTempDirectory("zlibrary-epub-test")
            val book = NovelBook(NovelSource.ZLIBRARY, "12/abcdef/epub", "Fixture")
            val cache = ZlibraryBookCache(api, root) { "https://zlib.test" }
            val document = cache.document(book)
            val image = document.contents.flatten().single { it.image != null }.image!!
            assertEquals("image-fixture", cache.image(book, image).decodeToString())
            offline = true
            val restarted = ZlibraryBookCache(api, root) { "https://zlib.test" }
            assertEquals(document, restarted.document(book))
            assertEquals("image-fixture", restarted.image(book, image).decodeToString())
        } finally { client.close() }
    }

}
