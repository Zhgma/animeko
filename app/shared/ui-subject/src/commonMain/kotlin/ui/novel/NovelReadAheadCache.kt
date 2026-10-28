/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.novel

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import me.him188.ani.app.data.repository.novel.NovelBlock
import me.him188.ani.app.data.repository.novel.NovelBook
import me.him188.ani.app.data.repository.novel.NovelChapter
import me.him188.ani.app.data.repository.reading.ReadingAutoCache
import me.him188.ani.app.ui.manga.mangaImageFileUri
import me.him188.ani.utils.io.*

internal class NovelReadAheadCache(
    private val store: ReadingAutoCache,
    private val fetch: suspend (NovelBook, NovelChapter, SystemPath, MutableList<SystemPath>) -> List<NovelBlock>,
) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private fun key(book: NovelBook, chapter: NovelChapter) = json.encodeToString(listOf(book.source.name, book.id, chapter.id))

    suspend fun opened(book: NovelBook, chapter: NovelChapter, next: NovelChapter?) =
        store.opened(key(book, chapter), next?.let { key(book, it) })

    suspend fun content(book: NovelBook, chapter: NovelChapter): List<NovelBlock> = mutex.withLock {
        val key = key(book, chapter)
        store.read(key)?.let { saved ->
            val blocks = json.decodeFromString<List<NovelBlock>>(saved)
            val restored = blocks.map { block ->
                val image = block.image
                if (image?.startsWith("auto:") == true) {
                    val file = store.asset(image.removePrefix("auto:")) ?: return@let
                    block.copy(image = mangaImageFileUri(file.absolutePath))
                } else block
            }
            return@withLock restored
        }
        val files = mutableListOf<SystemPath>()
        var committed = false
        try {
            val blocks = fetch(book, chapter, store.images, files)
            currentCoroutineContext().ensureActive()
            val names = files.associateBy { mangaImageFileUri(it.absolutePath) }
            val saved = blocks.map { block ->
                names[block.image]?.let { block.copy(image = "auto:${it.name}") } ?: block
            }
            withContext(NonCancellable) {
                if (!store.put(key, json.encodeToString(saved), files.map { it.name }.toSet()))
                    throw CancellationException("Chapter left the automatic cache window")
                for (file in files) {
                    if (!store.putAsset(file.name, file)) throw CancellationException("Automatic cache expired")
                }
                committed = true
            }
            blocks
        } finally {
            if (!committed) files.forEach { it.delete() }
        }
    }
}
