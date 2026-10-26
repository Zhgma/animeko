/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.player

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.him188.ani.app.data.repository.manga.MangaReadingMemory
import me.him188.ani.app.data.repository.novel.NovelMemory

@Serializable
enum class ReadingHistoryKind { MANGA, NOVEL }

/** One resumable position per source/book. Source identifiers are never sent to the anime history API. */
@Serializable
data class ReadingHistoryEntry(
    val subjectId: Int,
    val title: String,
    val coverUrl: String = "",
    val chapterTitle: String,
    val updatedAtMillis: Long,
    val totalPages: Int = 0,
    val manga: MangaReadingMemory? = null,
    val novel: NovelMemory? = null,
) {
    init { require((manga != null) != (novel != null)) }
    val kind get() = if (manga != null) ReadingHistoryKind.MANGA else ReadingHistoryKind.NOVEL
    val chapterId get() = manga?.chapterId ?: novel?.chapterId
    val position get() = manga?.page ?: novel!!.paragraph
    val sourceTitle get() = manga?.book?.source?.title ?: novel!!.book.source.title
    val key get() = Json.encodeToString(listOf(kind.name, subjectId.toString(),
        manga?.book?.source?.name ?: novel!!.book.source.name, manga?.book?.id ?: novel!!.book.id))
}

class ReadingHistoryStore(private val store: DataStore<Preferences>) {
    private val json = Json { ignoreUnknownKeys = true }
    private val entriesKey = stringPreferencesKey("reading.history.entries")
    private val migratedKey = booleanPreferencesKey("reading.history.migrated")
    private val syncKey = booleanPreferencesKey("playback.history.cloudSyncEnabled")

    // An absent preference, including on upgrades, means local-only playback history.
    val cloudSyncEnabled = store.data.map { it[syncKey] ?: false }.distinctUntilChanged()
    suspend fun setCloudSyncEnabled(enabled: Boolean) { store.edit { it[syncKey] = enabled } }

    val entries = flow {
        migrate()
        emitAll(store.data.map(::decodeEntries).distinctUntilChanged())
    }

    suspend fun record(entry: ReadingHistoryEntry) {
        if (entry.chapterId == null) return
        migrate()
        store.edit { prefs ->
            val previous = decodeEntries(prefs)
            if (previous.any { it.key == entry.key && it.updatedAtMillis > entry.updatedAtMillis }) return@edit
            prefs[entriesKey] = json.encodeToString((previous.filterNot { it.key == entry.key } + entry)
                .sortedByDescending { it.updatedAtMillis }.take(200))
        }
    }

    suspend fun remove(key: String) {
        migrate()
        store.edit { it[entriesKey] = json.encodeToString(decodeEntries(it).filterNot { entry -> entry.key == key }) }
    }

    /** Restore the original source and position before the reader's view model is created. */
    suspend fun prepareResume(entry: ReadingHistoryEntry) {
        store.edit { prefs ->
            entry.manga?.let { memory ->
                val key = stringPreferencesKey("manga.reader.${entry.subjectId}")
                val layout = prefs[key]?.let { runCatching { json.decodeFromString<MangaReadingMemory>(it) }.getOrNull() }
                prefs[key] = json.encodeToString(memory.copy(
                    rightToLeft = layout?.rightToLeft ?: memory.rightToLeft,
                    firstPageSingle = layout?.firstPageSingle ?: memory.firstPageSingle,
                    sourcePinned = true,
                ))
            }
            entry.novel?.copy(sourcePinned = true)?.let { memory ->
                val raw = json.encodeToString(memory)
                prefs[stringPreferencesKey("novel.reader.${entry.subjectId}")] = raw
                prefs[stringPreferencesKey("novel.position.${entry.subjectId}.${memory.book.source}.${memory.book.id}")] = raw
            }
        }
    }

    private fun decodeEntries(prefs: Preferences): List<ReadingHistoryEntry> =
        prefs[entriesKey]?.let { runCatching { json.decodeFromString<List<ReadingHistoryEntry>>(it) }.getOrNull() }.orEmpty()

    /** Legacy positions have no timestamp. Keep that unknown instead of inventing a reading time. */
    private suspend fun migrate() {
        if (store.data.first()[migratedKey] == true) return
        store.edit { prefs ->
            if (prefs[migratedKey] == true) return@edit
            val entries = prefs.asMap().entries.sortedBy { it.key.name }.mapNotNull { (key, value) ->
                val raw = value as? String ?: return@mapNotNull null
                runCatching {
                    when {
                        key.name.startsWith("manga.reader.") -> {
                            val id = key.name.removePrefix("manga.reader.").toIntOrNull() ?: return@runCatching null
                            val memory = json.decodeFromString<MangaReadingMemory>(raw)
                            if (memory.chapterId == null) null else ReadingHistoryEntry(id, memory.book.title,
                                memory.book.cover, "上次阅读章节", 0, manga = memory)
                        }
                        key.name.startsWith("novel.position.") || key.name.startsWith("novel.reader.") -> {
                            val id = key.name.split('.').getOrNull(2)?.toIntOrNull() ?: return@runCatching null
                            val memory = json.decodeFromString<NovelMemory>(raw)
                            if (memory.chapterId == null) null else ReadingHistoryEntry(id, memory.book.title,
                                chapterTitle = memory.chapters.firstOrNull { it.id == memory.chapterId }?.title ?: "上次阅读章节",
                                updatedAtMillis = 0, novel = memory)
                        }
                        else -> null
                    }
                }.getOrNull()
            }
            prefs[entriesKey] = json.encodeToString((decodeEntries(prefs) + entries).distinctBy { it.key }.take(200))
            prefs[migratedKey] = true
        }
    }
}
