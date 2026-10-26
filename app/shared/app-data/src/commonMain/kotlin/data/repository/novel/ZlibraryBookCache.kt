/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.novel

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.Json
import me.him188.ani.utils.io.*
import kotlin.random.Random

/** Complete parsed documents and illustrations survive process restart; partial downloads are never published. */
class ZlibraryBookCache(
    private val api: ZlibraryApi,
    private val directory: SystemPath,
    private val home: suspend () -> String,
) {
    private companion object { val mutex = Mutex() }
    private val json = Json { ignoreUnknownKeys = true }
    private suspend fun bookDirectory(book: NovelBook): SystemPath {
        val key = ByteString((ZlibraryApi.origin(home()) + "/" + book.id).encodeToByteArray()).digest(DigestAlgorithm.SHA256).toHexString()
        return directory.resolve(key)
    }
    suspend fun document(book: NovelBook): NovelDocument = mutex.withLock {
        val folder = bookDirectory(book)
        val index = folder.resolve("document.json")
        if (index.exists()) {
            val cached = runCatching { json.decodeFromString<NovelDocument>(index.readText()) }.getOrNull()
            if (cached != null && cached.contents.flatten().all { block ->
                    block.image == null || assetPath(folder, block.image).exists()
                }) return@withLock cached
        }
        val (name, bytes) = api.download(home(), book)
        val created = mutableListOf<SystemPath>()
        folder.createDirectories()
        try {
            val parsed = readNovelFile(name, bytes) { image ->
                val asset = "${Random.nextLong().toULong()}.image"
                val path = folder.resolve(asset); created += path; path.writeBytes(image)
                "zlibrary-image:$asset"
            }.copy(title = book.title)
            val temporary = folder.resolve("document.tmp")
            temporary.writeText(json.encodeToString(parsed)); temporary.moveTo(index)
            parsed
        } catch (e: Exception) { created.forEach { it.delete() }; throw e }
    }
    suspend fun image(book: NovelBook, uri: String): ByteArray = assetPath(bookDirectory(book), uri).readBytes()
    private fun assetPath(folder: SystemPath, uri: String): SystemPath {
        require(uri.matches(Regex("zlibrary-image:[0-9]+\\.image"))) { "无效的电子书插图" }
        return folder.resolve(uri.removePrefix("zlibrary-image:"))
    }
}
