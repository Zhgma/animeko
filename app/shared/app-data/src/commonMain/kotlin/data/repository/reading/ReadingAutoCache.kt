/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.reading

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.him188.ani.utils.io.*
import kotlin.time.Clock

/** Per-work rolling cache. Only entry into the work renews the 72-hour lease. */
class ReadingAutoCache(
    val directory: SystemPath,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    @Serializable
    private data class Entry(val body: String, val assets: Set<String> = emptySet())
    @Serializable
    private data class Index(
        val expiresAt: Long = 0,
        val catalog: String? = null,
        val previous: String? = null,
        val current: String? = null,
        val next: String? = null,
        val entries: Map<String, Entry> = emptyMap(),
        val assets: Map<String, String> = emptyMap(),
    ) {
        val window get() = setOfNotNull(previous, current, next)
    }
    val images = directory.resolve("images")
    private val indexFile = directory.resolve("index.json")

    suspend fun enter() = mutex.withLock {
        val index = load()
        save(index.copy(expiresAt = now() + TTL_MILLIS))
    }

    /** Reopening current does not shift history; a jump retains the actually opened chapter. */
    suspend fun opened(current: String, next: String?) = mutex.withLock {
        val old = load()
        val updated = old.copy(
            expiresAt = old.expiresAt.takeIf { it > now() } ?: (now() + TTL_MILLIS),
            previous = if (old.current == current) old.previous else old.current,
            current = current, next = next,
        )
        saveAndPrune(updated.copy(entries = updated.entries.filterKeys { it in updated.window }))
    }

    suspend fun catalog(): String? = mutex.withLock { load().catalog }

    suspend fun catalog(value: String) = mutex.withLock {
        val index = load()
        if (index.expiresAt > now()) save(index.copy(catalog = value))
    }

    suspend fun read(key: String): String? = mutex.withLock {
        val index = load()
        val entry = index.entries[key] ?: return@withLock null
        entry.body
    }

    suspend fun put(key: String, body: String, assets: Set<String> = emptySet()): Boolean = mutex.withLock {
        val index = load()
        if (key !in index.window) return@withLock false
        saveAndPrune(index.copy(entries = index.entries + (key to Entry(body, assets))))
        true
    }

    suspend fun asset(key: String): SystemPath? = mutex.withLock {
        load().assets[key]?.let { name -> owned(name).takeIf { it.exists() } }
    }

    /** The caller owns uncommitted files and must delete them if this returns false. */
    suspend fun putAsset(key: String, file: SystemPath): Boolean = mutex.withLock {
        require(file.absolutePath == images.resolve(file.name).absolutePath)
        val index = load()
        if (index.entries.values.none { key in it.assets }) return@withLock false
        saveAndPrune(index.copy(assets = index.assets + (key to file.name)))
        true
    }

    suspend fun invalidate(keys: Set<String>) = mutex.withLock {
        val index = load()
        // Keep existing image references until refreshed catalogs replace them.
        save(index.copy(entries = index.entries.mapValues { (key, entry) ->
            if (key in keys) entry.copy(body = "") else entry
        }))
    }

    suspend fun sweep() = mutex.withLock { load(); Unit }

    private fun owned(name: String): SystemPath {
        require(name.isNotEmpty() && name != "." && name != ".." && '/' !in name && '\\' !in name)
        return images.resolve(name)
    }

    private fun load(): Index {
        val index = if (indexFile.exists()) {
            try { json.decodeFromString<Index>(indexFile.readText()) }
            catch (_: Exception) { Index() }
        } else Index()
        if (index.expiresAt <= now()) {
            // Only the isolated automatic-cache directory is removed, never offline/imported books.
            if (directory.exists()) directory.deleteRecursively()
            return Index()
        }
        return index
    }

    private fun save(index: Index) {
        directory.createDirectories()
        val temp = directory.resolve("index.tmp")
        temp.writeText(json.encodeToString(index))
        temp.moveTo(indexFile)
    }

    private fun saveAndPrune(index: Index) {
        val used = index.entries.values.flatMap { it.assets }.toSet()
        val retained = index.assets.filterKeys { it in used }
        val old = if (indexFile.exists()) json.decodeFromString<Index>(indexFile.readText()) else Index()
        save(index.copy(assets = retained))
        (old.assets.values.toSet() - retained.values.toSet()).forEach { owned(it).delete() }
    }

    companion object {
        const val TTL_MILLIS = 3L * 24 * 60 * 60 * 1000
        // Serializes manifest publication and expiry across multiple views of the same work.
        private val mutex = Mutex()
        private val json = Json { ignoreUnknownKeys = true }
        suspend fun sweepAll(root: SystemPath) {
            if (!root.exists()) return
            root.list().forEach { path ->
                if (path.inSystem.isDirectory()) ReadingAutoCache(path.inSystem).sweep()
            }
        }
    }
}
