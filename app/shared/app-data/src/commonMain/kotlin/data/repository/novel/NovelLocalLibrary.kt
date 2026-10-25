/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.novel

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

fun novelLocalFilesKey(subjectId: Int) = stringPreferencesKey("novel.files.$subjectId")

/** Imported files available through the standalone local reading entry. */
class NovelLocalLibrary(store: DataStore<Preferences>) {
    private val json = Json { ignoreUnknownKeys = true }
    val books = store.data.map { preferences ->
        preferences[novelLocalFilesKey(0)]?.let {
            runCatching { json.decodeFromString<List<NovelBook>>(it) }.getOrDefault(emptyList())
        }.orEmpty()
    }.distinctUntilChanged()
}
