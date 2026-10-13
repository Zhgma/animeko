/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.manga

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class MangaReadingMemory(
    val book: MangaSourceBook, val chapterId: String? = null, val page: Int = 0,
    val rightToLeft: Boolean = true, val firstPageSingle: Boolean = false,
    val sourcePinned: Boolean = true,
)

/** Namespaced local source bindings and page positions; never modifies Bangumi reading progress. */
class MangaReadingStore(private val store: DataStore<Preferences>) {
    private val json = Json { ignoreUnknownKeys = true }
    suspend fun read(subjectId: Int): MangaReadingMemory? {
        val raw = store.data.first()[key(subjectId)] ?: return null
        return runCatching { json.decodeFromString<MangaReadingMemory>(raw) }.getOrNull()?.takeIf { it.book.source in MangaSource.available }
            ?.let { it.copy(book = it.book.copy(cover = migrateMangaApiImageUrl(it.book.cover))) }
    }
    suspend fun save(subjectId: Int, memory: MangaReadingMemory) {
        store.edit { it[key(subjectId)] = json.encodeToString(memory) }
    }
    private fun key(id: Int) = stringPreferencesKey("manga.reader.$id")
}
